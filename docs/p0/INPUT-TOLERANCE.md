# 输入时间戳容差

## 原因与修复

ce921b9 的历史匹配回放曾出现合法输入 seq=34、stamp=912、receivedTick=911 被 FUTURE 丢弃（history-final-fast-zero）。客户端以服务端 tick 加本地经过时间估算 stamp，跨 tick 的估计抖动不能等同于伪造。旧失败证据保留；本次每个 RTT 的快速回放只运行一次，不从多轮结果中挑选。

服务端 network.futureTolerance 默认 2 tick，配置范围 0–4。InputGate 在原有 SESSION、REPLAY 检查及序号消耗后允许 stamp≤receivedTick+tolerance；超过仍 FUTURE；STALE、RATE 顺序和额度不变。认证失败不消耗序号，其余已通过序号检查的拒绝仍消耗序号。兼容的五参数调用保持零容差。

接受后 effectiveStamp=min(原始 stamp, receivedTick)。GUARD_PRESS/RELEASE 只使用夹值；动作调度仍以服务端消费 tick 处理请求，不使用客户端未来 tick。命中 tick、冻结墙钟和 3 tick/150ms 双截止不变。日志保留原始 stamp、effectiveStamp、receivedTick 与 consumedTick；独立格挡审计使用日志中的有效 stamp，不事后把非法输入变为合法。

收包观察的消息长度、序号偏移及 FML discriminator 由 InputMessage 提供常量，注册也使用同一 discriminator。当前 body=44 字节，wire=45（含 discriminator），sequence body offset=32、wire offset=33；C17 和 FMLProxyPacket 两条路径均使用它们。JUnit 实际调用 toBytes，断言长度、序号位置以及所有字段 round-trip；布局变化而常量未同步会失败。

## 构建与单元测试

```powershell
$env:JAVA_HOME='C:\Program Files\Zulu\zulu-25'
$env:VERSION='0.1.0-dev'
.\gradlew.bat spotlessApply spotlessCheck checkstyleMain checkstyleTest build -I tools/p0/validation.gradle p0LaunchFiles --no-configuration-cache
```

实测 BUILD SUCCESSFUL in 5s；JUnit XML 共 39 项、失败 0、错误 0，原有 36 项仍通过。新增容差内夹值、容差外拒绝、+1000 伪造、0/4 配置边界、夹值后的精准窗口及双截止、其它拒绝路径与序号消耗，以及真实消息布局测试。构建摘要和实际 XML 见 evidence/tolerance-build。

## 双客户端运行与实测

每个场景均为专用服务器加两个真实开发客户端；100ms 为代理双向各 50ms。快速转向每档 24 次，45°/90°/180° 各 8 次，通过生产 ClientProxy.send；未改客户端校时、未注入未来值让合法场景通过。视角场景各 20 条连续转动视角的连击。

```powershell
$py='C:\Users\layue13\AppData\Local\Programs\Python\Python312\python.exe'
& $py tools/p0/dual.py --label tolerance-fast-zero --accept-eula --fast-turn
& $py tools/p0/dual.py --label tolerance-fast100 --accept-eula --fast-turn --rtt 100
& $py tools/p0/dual.py --label tolerance-zero --accept-eula
& $py tools/p0/dual.py --label tolerance100 --accept-eula --rtt 100
& $py tools/p0/dual.py --label tolerance-view-zero --accept-eula --view-test
& $py tools/p0/dual.py --label tolerance-view100 --accept-eula --view-test --rtt 100
# 各 label 依次执行 analyze.py、facing_view_metrics.py；视角场景另执行 playfix_metrics.py。
```

### 快速转向：每档一次，完整保留

| RTT / label | 45° / 90° / 180° | 采纳 / 回落 | 游戏意图 FUTURE / 全部输入门拒绝 | 数据确认拒绝（kind=-2） |
| --- | --- | --- | --- | --- |
| 0ms / tolerance-fast-zero | 8 / 8 / 8 | 24 / 0（100%） | 0 / 0 | STALE 1 |
| 100ms / tolerance-fast100 | 8 / 8 / 8 | 24 / 0（100%） | 0 / 0 | STALE 22、RATE 1 |

这里“游戏意图”指 kind≥0 的按键意图；握手/数据确认不是玩家点击。**并非所有网络消息零拒绝**：启动确认的超龄/突发拒绝完整保存在 all_input_gate_rejections 与 summary，不排除后重算全部网络通过率。若验收要求包括启动确认，则该扩大口径未满足。两档均提交、输入门接受、服务端采用快照及动作开始一一对应，24/24 实际执行；无预测倒退/前跳和 S08 预算纠正。此次真实回放有效 stamp 夹值事件为 0：±2 边界由 JUnit 验证，不能宣称网络场景已实测命中夹值分支。

### 常规回归及未满足的项

| RTT / label | 服务端合法请求执行 | 非法派生执行 | 格挡 / 双截止违例 | 对齐倒退 / 前跳 | 客户端累计纠偏计数最大值 |
| --- | --- | --- | --- | --- | --- |
| 0ms / tolerance-zero | 61/61 | 0/2 | 0 / 0 | 0 / 0（33 次） | 0 |
| 100ms / tolerance100 | 62/62 | 0/3 | 0 / 0 | 0 / 0（35 次） | 1 |

100ms 另外有玩家真实重击 seq=70、stamp=589、receivedTick=599，被保留的 STALE（年龄 10>8）拒绝；这条不属于上表“服务端合法请求”，**不能用 62/62 证明玩家提交全部成功**。其原始客户端挥刀、输入门及 REJECT:STALE 记录均保存。累计纠偏计数 1 是该拒绝引发的动作预测回退（CW_ALIGN sameAction=false），没有 MOVE_CORRECT/战斗 S08，旋转变化仍为 0。相对 history-final100 的纠偏计数 0，本次该指标退步，故“常规回归指标不得变差”未完全满足。没有通过重复普通回放挑选绿结果；保留原年龄检查，未擅自增加年龄限额或改校时算法。建议后续单独核查启动定义确认积压和客户端锚点滞后；因果关系尚未独立对照验证。

+1000 未来伪造本次 JUnit 实际拒绝；这两档普通回放未触发夹具启动阶段的伪造分支（没有 P0_FORGED），因此联网伪造重测为未验证。不能把历史伪造记录当成本次结果。

### 视角与双截止

| RTT / label | 完整连击 / 动作开始 | 序列内全部 S08 | 最大开始 yaw / pitch 变化 | 格挡 / 双截止违例 |
| --- | --- | --- | --- | --- |
| 0ms / tolerance-view-zero-retry | 20 / 61 | 0 | 0° / 0° | 0 / 0 |
| 100ms / tolerance-view100 | 20 / 61 | 0 | 0° / 0° | 0 / 0 |

两档各有一个准备阶段、序列外的未标记 S08 按原版转向（yaw 180°、pitch 0°），单列 all_position_packet_rotation_changes=1，不冒充战斗视角保护失败，也不隐藏。战斗标记视角违例为 0，所有被接受输入 effectiveStamp≤receivedTick 违例 0。100ms 视角场景启动确认另有 STALE 17/RATE 3，游戏意图门拒绝 0。

首次 tolerance-view-zero 在 60s 首客户端登录等待内未登录，complete=false，尚未开始玩法；失败 summary 和日志片段保留。仅这个未开始的视角场景重新启动为 tolerance-view-zero-retry，不重跑快速转向或普通回放筛选结果。以上六个完成场景均 COMPLETE、server_exit=0、专服客户端/OpenGL 类加载 0。失败启动的 server_exit=1 不计作完成运行。

最终复查命令 spotlessCheck checkstyleMain checkstyleTest build --no-configuration-cache，BUILD SUCCESSFUL in 20s；本轮 JUnit 实际运行证据来自前述首次构建的 39 项 XML。完整原始日志留在 build/p0/runs，不整份入库。入库证据保留命令、运行 jar SHA、逐请求/命中/视角 JSON、summary 与关键日志片段。

## 设计影响与限制

与原型说明 1.2 第 3 条“未来 stamp 均拒绝”存在直接冲突；按本次用户审核优先级，建议改为“超过可配置未来容差的 stamp 拒绝，容差内夹到收包 tick”。本次用户结论优先于旧 FUTURE 零容差口径：有限容差用于吸收校时抖动，不授予未来调度或延后格挡截止。历史匹配、标记式 S08 保护和所有动作/防御/交锋/位移预算保持原行为。未重测无关渲染、生命周期和低 TPS 场景，不将历史结果写成本次实测。其它修改 Netty 管线的模组共存仍未验证；修复后真人体感待再试玩。完成后等待审核，不进入后续系统。
