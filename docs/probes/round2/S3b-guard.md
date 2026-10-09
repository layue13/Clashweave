# S3b 格挡：延后提交的已测条件成立；闸门仍需补足实际提前量样本

## 假设与做法

服务端在原命中 tick 固定真实玩家对与该时刻的距离观察，延后 2 或 3 tick 提交；提交时只检查此前收到的真实按键意图，不重新选择目标、不回滚世界。未格挡时调用真实 `EntityPlayerMP.attackEntityFrom(causePlayerDamage)`，实测扣血 1；成功时扣血 0。固定 1 格、1000 血量是隔离夹具负载，不是玩法数值。独立 `tools/probes2/s3b` 源集/实验 jar，不进入 `src/main`。

四场新建专用服务器各接入 GuardA、GuardB 两个真实 Forge 客户端，整个连接通过 FIFO TCP 代理。正式矩阵共 400 次：延后 2/3 tick × 配置 RTT 0/50/100/200ms × **计划**提前 0/1/2/3/4 tick × 每格 10 次。另有 20 次 0ms 旧客户端调度公式诊断。客户端在实际发送时用同机墙钟与已知服务端锚点换算 stamp；没有把计划 stamp 冒充真实发送时刻。此校时仅为同机 20TPS 测试夹具，生产安全校时未实现。

`window=5`、`ageCap=8` 通过 JVM/runner 参数配置。首轮 cap=4 不与延后方案直接等价：如果在命中前 4 tick 按下、命中后 3 tick 才到达，年龄可能达 7；截止与精准窗口仍须同时满足。8 是可调测试上限，不是冻结协议或无限回溯。实际年龄范围四场分别 0–1、0–2、1–2、2–3，没有测到 cap=8 边界。按本轮用户的延后提交条件取舍，须在原型说明 1.1/S3 与 network 的历史补偿段明确区分输入年龄上限、窗口与提交截止。

## 命令与证据

```powershell
$env:JAVA_HOME='C:\Program Files\Zulu\zulu-25'
$env:VERSION='0.1.0-dev'
.\gradlew.bat -I tools/probes2/s3b/experiment.gradle s3bLaunchFiles --no-configuration-cache
& 'C:\Users\layue13\AppData\Local\Programs\Python\Python312\python.exe' tools/probes2/s3b/run.py --accept-eula --label s3b-qpc --rtts 0,50,100,200 --window 5 --age-cap 8
& 'C:\Users\layue13\AppData\Local\Programs\Python\Python312\python.exe' tools/probes2/s3b/analyze.py --label s3b-qpc
```

重跑须换 `--label`，脚本拒绝覆盖已有世界/证据；服务器/代理只绑定 localhost:25572/25573。`s3bLaunchFiles` 实测 `BUILD SUCCESSFUL in 1s`，17 tasks。生产构建/实验语法检查及最终 imports 整理见[总报告](REPORT.md)。实测 jar SHA-256 为 `6bdedc558fa62b1d73874499ed14d6a246686c6cd28514850ffb16a502407a41`；每场 summary 保存命令、该次源文件与 jar 哈希。原生日志中的 Forge 开发签名/版本检查和 OpenAL 后台异常保留，没有计成战斗功能通过。

原始记录：[0ms](evidence/s3b-qpc-rtt0/server-measurements.log)、[50ms](evidence/s3b-qpc-rtt50/server-measurements.log)、[100ms](evidence/s3b-qpc-rtt100/server-measurements.log)、[200ms](evidence/s3b-qpc-rtt200/server-measurements.log)；每场另有 defender 的 SEND/WIRE 记录和 `per-message.json` 全部逐条对照。[汇总](evidence/s3b-qpc-metrics.json)、[源码哈希](evidence/s3b-qpc-context-hashes.json)、[时钟能力](evidence/s3b-qpc-clock-info.json)。

开发中发现 Python 3.12 Windows `monotonic=GetTickCount64` 分辨率为 15.625ms，单次 asyncio sleep 的旧代理曾提前放行；旧代理数据不用于验收。本报告只采用 QPC `perf_counter` 截止配合线程内 waitable timer、循环检查到 due 才发送的新矩阵。四场代理全流实测单程最小值分别 0.090/25.087/50.081/100.073ms；均值 0.189/25.533/50.422/100.437ms。50ms 场全流最大 131.864ms，包含非格挡流量，不能隐去；下表另列逐条格挡消息的实际单程测量。

## 0ms 的 age=2 来源

首轮 `ProbeClient.java`：112/138 行在 ClientTick START 消费私有队列，264 行用**本地消费 tick**加上服务端计划 tick 差值调度，170 行却发送原计划 stamp。实际 Minecraft `onPreClientTick` 在客户端 `PlayerControllerMP.updateController/processReceivedPackets` 之前，因此 S2C handler 入队后还要等下一 START。服务端原生 `receivedPacketsQueue` 又等 `NetworkSystem.networkTick`，私有队列在 ServerTick END 消费。没有找到固定 sleep 2 tick；是收到消息的时基被当成发送锚点，加上两端原生队列/帧相位。

本轮复现旧公式与 START/END 消费路径，预告/间隔采用 12/5 tick（首轮 20/10），不冒称完整同 cadence 回放。20 次 age 分布：1 有 1 次，2 有 17 次，3 有 2 次。说明首轮固定 2 是原场次的相位表现，不能冻结为普遍恒定延迟。逐条数据直接分离了网络与排队，下面是原记录 id=3：

```text
server anchorTick=424 anchorWall=1791545043700 stamp=434
client WIRE=1791545043700 handler=1791545043716 START-consume=1791545043765
client dueWall=1791545044200 actual SEND=1791545044266
server WIRE=1791545044266 handler=1791545044299 END-consumeTick=436 age=2
```

S2C/C2S 实际网络各 0ms；客户端原生排队 16ms、额外下一 START 49ms，使真实发送比声明 stamp 晚 66ms；服务端原生排队 33ms，最终 stamp=434 被 tick=436 消费。20 次中额外 START 中位数 50ms，旧发送偏移中位数 86.5ms，服务端原生排队中位数 12.5ms。原型说明 1.1/S3 的“固定 2 来源待查”应改成上述时基/队列结论；不能把所有 0ms 损失归因网络。

## 实测与通过标准的差距

下表是**计划提前量**下成功次数，每格分母均为 10；不是整体成功率。括号中的单程来自真正客户端 SEND 到服务端 Netty WIRE 的中位数与范围。

| 配置 RTT / 实际格挡单程 ms | 延后 tick | 提前 0 | 1 | 2 | 3 | 4 | 本轮全 10 次成功的最小计划提前量 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 0 / 0（0–1） | 2 | 0 | 10 | 10 | 10 | 10 | 1 |
| 0 / 0（0–1） | 3 | 1 | 10 | 10 | 10 | 10 | 1 |
| 50 / 26（25–30） | 2 | 7 | 10 | 10 | 10 | 10 | 1 |
| 50 / 26（25–30） | 3 | 9 | 10 | 10 | 10 | 10 | 1 |
| 100 / 51（50–51） | 2 | 5 | 10 | 10 | 10 | 10 | 1 |
| 100 / 51（50–51） | 3 | 2 | 10 | 10 | 10 | 10 | 1 |
| 200 / 101（100–101） | 2 | 1 | 6 | 10 | 10 | 10 | 2 |
| 200 / 101（100–101） | 3 | 7 | 10 | 10 | 10 | 10 | 1 |

计划提前 0 的失败须拆开：客户端只在 START 发送，真实 stamp 可能已到 hit+1，属于窗口外，不是符合窗口条件的格挡被吞。0/50/100ms 两个延后量合计，真正窗口内且 WIRE 明确早于截止的输入分别 **81/81、96/96、87/87，合计 264/264 成功且扣血 0**。最短截止余量分别 99/60/27ms，没有同毫秒边界项。这满足已测“RTT≤100ms、窗口内、延后量内到达”条件。

200ms 有 84/84 明确先到的窗口内输入成功；另有 id=16 同毫秒边界：`RESULT(commit=741)` 与 `WIRE` 都为 `1791545452758`，日志 RESULT 在前，handler 在下一 tick=742 才消费，实际 `vanillaAccepted=true loss=1.0`。毫秒分辨率不能断言该 WIRE 是否严格在提交之前；保守标为截止边界未确定，不将它塞进已通过分母。200ms 不在 100% 条件阈值范围内，其迟到/边界结果仍完整报告。

但 **计划提前量不等于实际 stamp 的提前量**。按实际 `hit-stamp` 分桶，0ms 延后 2 的实际提前 4 只有 1 次，100ms 延后 3 的实际提前 4 为 0 次；其它桶也有不足 10 次的情况，完整计数见汇总 `actual_lead_by_defer`。因此不能宣称每个精确实际提前量都完成 ≥10 次，更不能把表中的“计划提前 1”冻结成人类操作所需值。

## 结论与设计影响

**完整 S3b 闸门仍需补足实际提前量样本；延后提交的已测条件成立。** 1.2/S3b 的“每个提前量≥10次”按实际输入时刻严格解释时，本轮只满足计划负载数量，尚不满足所有实际桶配额。建议下一轮将校时/帧相位作为显式夹具参数，在真实发送时记录时刻并按实际桶补足，不伪造 stamp 或用计划数字替代实测；同时用单调高精度时刻界定 END 截止前后，消除 200ms 同毫秒项。

这没有推翻延后提交方向，也不降低 100% 条件标准；它修正了原型说明 1.1/S3 的固定延迟解读，并要求 1.2/8.1 的最小提前量报告明确计划与实际口径。四场服务器均正常退出 exit=0、专用服务器 Minecraft 客户端/OpenGL/本实验 Client 类加载 0。跨机生产时间同步、低 TPS、乱序/重放、松开/失效防御、冻结期间死亡/换维度、一般攻击几何与完整动作流均未验证。P0 未开始。
