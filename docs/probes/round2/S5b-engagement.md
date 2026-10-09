# S5b 交锋态：通过（所测脚本场景）

## 假设与做法

服务端每 END tick 计算交锋态：8 格内怪物以玩家为目标、刚受击、锁敌或动作中触发，脱离滞后 60 tick；变化时递增修订号并同步。客户端只预测已知本地锁敌/动作触发，怪物目标和受击以权威状态为准；网络线程只入队，在客户端 END 应用，立即刷新包装后的原版移动输入。修订号拒绝旧状态，但本轮没有主动注入乱序修订。

真实 EntityZombie 的 AI 任务清空以稳定脚本，保留真实目标字段。六段脚本每段 90 tick：怪物 4 格指向玩家、目标丢失、9 格外指向玩家、真实 generic 2 点伤害、锁敌、动作中。锁敌/动作是独立谓词夹具，不是 P0 调度器。各段第 75 tick 对照稳定预期；刚受击入态/滞后退态另外由真实伤害返回值与状态变化日志核对。未读取准星决定状态。

客户端实际保持 W+Shift 7 tick；交锋态取消原版 sneak 和 0.3 移速缩放，平时态保留。交锋态发送原生 C08 开箱位置包，服务端交互事件拒绝；平时态调用原版右键开箱。左键取消挖掘，实体右键取消原版交互，无 F 键。持刀识别使用原版 iron_sword 作为夹具，尚无正式重击/攻击流。

## 命令与实测

```powershell
$env:JAVA_HOME='C:\Program Files\Zulu\zulu-25'
$env:VERSION='0.1.0-dev'
.\gradlew.bat -I tools/probes2/movementstate/experiment.gradle movementStateLaunch --no-configuration-cache
python tools/probes2/movementstate/run.py --accept-eula --label new-s5 --rtts 0,100 --state-only
```

最终有效证据：[0ms](evidence/s5-valid-rtt0/summary.json)、[100ms](evidence/s5-valid-rtt100/summary.json)。同目录含客户端/服务端测量与警告。实验 jar SHA256：`2fa6d6d34c409f9a455375e67f6e9a19c0f4b4e18d190c11bbfdfa89e090b4ef`。

| RTT | 六个稳定场景误判 | 状态同步时长 ms | 超过 2 tick（100ms） | 交锋开箱 tick | 交锋原版潜行 tick |
| --- | --- | --- | --- | --- | --- |
| 0ms | 0 | 4.1411 / 13.0506 / 17.7228 / 39.2545 / 32.1983 | 0 | 0 | 0 |
| 100ms | 0 | 79.0388 / 76.5149 / 61.8245 / 85.1275 / 81.8062 | 0 | 0 | 0 |

```text
S5B_HIT accepted=true hpBefore=20.0 hpAfter=18.0 hurtTime=10
S5B_SUMMARY wrong=0 slow=0 openTicks=0 sneakTicks=0
S5B_INPUT case=0 engaged=true sneak=false forward=1.0 opened=false
S5B_INPUT case=1 engaged=false sneak=true forward=0.3 opened=true
```

状态延迟从同机服务端变化 System.nanoTime 到客户端实际应用测量，不包含返回确认包的时间。100ms 代理实际单程最小 50.0967、最大 54.3309、平均 50.5347 ms；0ms 平均转发开销 0.2679 ms。两场 exit=0，服务器客户端/OpenGL 实际类加载=0。

复核曾发现单独运行的场地没有建立地板，玩家已死亡，`accepted=false hpBefore=0`；这场没有作为通过证据。最终夹具显式建立地板并恢复初始生命，且移除手工设置受击触发的做法，要求真实 hurtTime 驱动状态。早期 ms5 位移矩阵附带的交锋记录只作诊断，最终验收以上述有效独立复跑为准。runner 的 `state_cases=4` 是匹配 expected=true 的旧进度计数，不是场景总数；原始日志包含 id=0..5 六次 ORACLE。

## 结论与设计影响

**通过，范围限于上述脚本和两档 RTT**。服务端同步替代客户端 AI 目标判断的方向得到证据支持；应在 input/network 中保留权威修订同步与客户端 END 应用的时序说明。尚未验证低 TPS、真实锁敌合法性、正式动作生命周期、重连、故意乱序、其它 GUI 与模组交互，因此没有声称 P0 或完整输入流通过。

友好实体保护（村民、已驯服宠物、同队玩家）与平时态方块右键放行均有服务端配置，默认开启，仍待用户确认；关闭配置与各实体保护分支未在本轮运行矩阵覆盖。既有键位决策未变，参数可调，没有冻结公开 API。
