# 朝向校验与视角保护修正

8945344经审核后，同一prototype/p0-vertical分支追加，仅修朝向历史匹配与S08保护范围，不改动作、防御、玩家延后提交、交锋同步和累计位移预算。

## 朝向历史匹配

删除30°/tick、相邻包转速与50ms滚动窗口判定；`maxDegreesPerTick`不再读取，旧配置属性在加载时删除。容差angleTolerance默认5°、historyAgeMillis默认500ms、位置漂移上限仍4格。

查找请求实际收到之前、年龄内、位置漂移合格的任意C03朝向样本，yaw使用圆周差，pitch用绝对差。匹配则采纳按下快照；非有限值、pitch越界、无历史、过旧、漂移或没有匹配样本回落服务端已知朝向，分别记录INVALID_ANGLE/NO_HISTORY/OLD_HISTORY/POSITION_HISTORY/HISTORY_MISMATCH。位置包没有新朝向时继承旧朝向的原始时间，不让位置包刷新过期的朝向。

按键时客户端先发原版C05，再发动作意图，两者同连接有序。原版C03允许任意有限转向，这只是保证快照已进入实际发送历史，不是反作弊转速限制。服务端只读观察，不改原版包处理。

请求实际Netty收包时间另行登记（有界512项），不以主线程延后解码时间证明朝向；该时间只用于朝向匹配，原输入/格挡截止时间语义未改。之后收到的C03不能证明更早意图，即使意图到主线程时该样本已在历史中。Forge NetworkDispatcher会把已注册频道的C17转换为FMLProxyPacket，因此观察兼容这两种包装，读ByteBuf不改变readerIndex。内部输入判别符与固定长度来自当前InputMessage协议，不承诺公开API。

历史匹配证明客户端两类包一致，不能阻止恶意客户端同时伪造C03与意图；不声称此校验建立独立可信的人类朝向。

## 仅战斗纠正保护旋转

服务端预算超限调用setPlayerLocation之前，先发送CorrectionMessage：递增序号与预期S08的XYZ。动作开始仍用MOVE_READY自定义预算握手，没有重新引入传送。序号在服务器实例内递增，各连接维护自己的最新序号/一次性待处理标记；重连创建新状态。重复/旧序号不能覆盖或重启有效标记；更新标记替换旧待处理项。

客户端在已解码包、packet_handler前只读登记自定义标记；支持S3F及Forge转换后的FMLProxyPacket。标记从Netty收包起250ms（正常20TPS约5tick）有效；下一个S08消费一次，坐标不匹配或超时则作废，不再影响后来包。S08收到时选定标记，实际主线程执行时才读玩家yaw/pitch。标记匹配时保留四个当前/上一帧旋转字段；否则原包原旋转完全交给原版处理。位置、速度清零、地形初始化和C06回执仍属原版。

标记由服务器依据纠正来源发送，不查询客户端是否拿刀；切物品后的战斗纠正仍受保护。普通/tp、管理员及其它未标记的传送恢复原版有意转向。

### 源码与有序性依据

实际Forge1.7.10反编译`NetHandlerPlayServer.java:443`的setPlayerLocation发S08时，包Y是脚部y加1.6200000047683716；标记坐标使用同一换算，不能用脚部y直接匹配包Y。`NetHandlerPlayClient.java:631`处理绝对yaw/pitch，没有相对旋转标志；S08三个double、两个float、一个boolean的既有核对成立。

NetworkManager.java:148–200：同一服务器线程先后调用发包，dispatchPacket在同一连接eventLoop直接writeAndFlush或依序execute任务；TCP字节流/Netty解码顺序保留这两个包的顺序。NetworkDispatcher将自定义包变为FMLProxyPacket后仍fireChannelRead；客户端观察器在packet_handler之前登记/消费，不依赖稍后主线程IMessageHandler的调度。原版queue只延后实际世界处理，不能把标记消费反排。实际运行日志另记录标记的Netty线程与主线程S08结果。

### 首轮失败与修正

首轮在主线程IMessageHandler登记标记、却在Netty收到S08时消费，实测未保护，facing-marker-correction作废。之后对照Forge转换及原版Y偏移，改为同一Netty观察点登记/消费，并用包坐标匹配；重新跑最终独立目录。中间facing-marker-zero漏识别Forge代理包而出现NO_HISTORY，未作最终0ms验收，改为识别代理包后另跑history-final-zero。不把中间构建或慢速观察代替最终快速转向验收。旧facing-marker-fast-zero/100早期运行未包含最终请求收包时基，另以history-final-fast-zero/100复跑。

## 测试与实测

构建命令：Zulu25、VERSION=0.1.0-dev，`gradlew.bat spotlessApply spotlessCheck checkstyleMain checkstyleTest build -I tools/p0/validation.gradle p0LaunchFiles --no-configuration-cache`。JUnit实际36项，原32项中的4项朝向测试按已审核新语义修订，不保留错误的快速转向拒绝断言；其余原测试保留。新增实际收包时间、一次性标记/250ms边界/坐标错配/序号/重连/编解码，failures/errors=0。history-marker-build10输出BUILD SUCCESSFUL in 5s。

联网命令统一`python tools/p0/dual.py --label <label> --accept-eula`，100ms加`--rtt 100`。快速转向加`--fast-turn`（24次，45/90/180各8次，一个客户端tick完成转向），传送加`--correction-test`，原连续观察加`--view-test`，真实预算超限加`--movement`。每轮专服+两真实客户端，运行产物快照记录SHA256；摘要及关键日志在evidence同名目录。逐快速转向请求执行`python tools/p0/facing_view_metrics.py --label <label>`，连续观察执行`playfix_metrics.py`，常规回放执行`analyze.py`。

| 场景 | 实测与结论 |
| --- | --- |
| history-final-fast-zero | 24次快速转向（45/90/180各8）；23采纳、朝向回落0；总采纳率23/24=95.8333%。另1次输入门FUTURE拒绝，未进入朝向校验；已评估23/23=100%。 |
| history-final-fast100 | 同24次，24采纳、回落0；总/已评估采纳率均100%。 |
| facing-marker-view-zero/100 | 各20完整连击、61 START，序列内所有原因S08为0，最大yaw/pitch差0；受保护包视角违例0。 |
| history-final-correction-zero/100 | 两档均空手：未标记S08由0/0转为45/20，旋转变化45/20与原包一致；标记S08虽携带-90/-30，变化0/0；超时标记后原版设为120/-10，变化75/-30。未标记原版行为违例0。 |
| facing-marker-budget100 | 20运动场景，仅5格横移/上移2次预算纠正；两条标记在Netty Client IO登记，S08在Client thread执行，虽携带旧角度，变化0/0。 |

0ms快速回放被拒输入的真实记录：seq=34、stamp=912、receivedTick=911、consumedTick=912、gate=FUTURE。它不是未匹配角度导致的回落，仍不从总分母中抹去。按照本任务至少20次与回落0口径，两档已评估23/24次均满足；原有生产校时偶发预测提前1tick仍存在，本轮未擅改校时/格挡语义，供用户审核。没有重跑挑选一次全绿结果来覆盖这条记录。伪造角度、NaN、pitch越界、过期/漂移/无历史和未来样本由JUnit独立覆盖，不把它们混入合法快转的分母。

常规最终0ms history-final-zero：合法61/61、非法3条执行0，格挡及双截止违例0，34对齐无倒退/前跳，硬纠正0。100ms最终构建回放另列下方；此前facing-marker100的61/61保留为中间回归，最终以history-final100为准。

## 设计影响、限制与停止

本节取代PLAYFIX和HOOKS中“转速限制能防伪造”及“所有S08均保护”的当前结论，旧实测保留为历史。遵从本次用户审核优先级：历史匹配不限制快速鼠标；有意的未标记传送允许转向。合法战斗序列仍统计所有原因S08和旋转变化；开场/专门行政传送另列，不将其原版转向误记为战斗保护失败。

与其它修改Netty管线的模组共存未验证；未知模组重排/插包不在本次有序性验证范围。标记超时按客户端单调墙钟，不受服务器低TPS放大；物理鼠标/真人修复后体感待再试玩。原P0预算余量内轻度超速、纳刀连续插入与手指骨骼等缺口未改变。停止等待审核，不开始后续系统。

最终100ms history-final100：合法61/61、非法2条执行0；格挡/双截止违例0/0；33对齐无倒退/前跳，硬纠正0。全部接受运行均COMPLETE、退出0、专服客户端/OpenGL类加载0。

后续校时抖动修复见[输入时间戳容差](INPUT-TOLERANCE.md)。上方 seq=34 的 FUTURE 保留为本次修复前的真实失败，不以新回放覆盖历史记录。
