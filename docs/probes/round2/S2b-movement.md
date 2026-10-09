# S2b 位移：失败

## 假设与做法

在服务端 END 读取实际 `player.posX/Z`，与动作包络和 AABB 扫掠比较；超出调用原生 S08 回退。没有修改 C03 包处理器。独立源集 `tools/probes2/movementstate` 使用真实专用服务器、一个真实客户端和双向 FIFO TCP 延迟代理。客户端用原生 moveEntity/C03 移动，步幅 0.4、突进 0.8、持续 5 tick；累计路径限制为 2/4 格，未用探针消息授权客户端坐标。END 的未使用位移额度最多累积 3 tick，全部候选参数可配置。

观测 S08 的只读 Netty 监听器不修改/取消数据包。每次场地传送后等待 12 客户端 tick，再清零计数，准备传送不计入动作纠正。每个 RTT 40 次合法动作，模式依次为踏步、突进、墙前突进、斜向、空中、楼梯、液体，次数分别 6/6/6/6/6/5/5。另发 5 格原生 C04 单包和跨墙位置包各一次。

## 复现与实测

```powershell
$env:JAVA_HOME='C:\Program Files\Zulu\zulu-25'
$env:VERSION='0.1.0-dev'
.\gradlew.bat -I tools/probes2/movementstate/experiment.gradle movementStateLaunch --no-configuration-cache
python tools/probes2/movementstate/run.py --accept-eula --label new-s2 --rtts 0,100
```

使用新 label，脚本拒绝覆盖旧世界；EULA 已获使用者接受。最终有效位移矩阵为 [0ms](evidence/ms5-qpc-rtt0/summary.json)、[100ms](evidence/ms5-qpc-rtt100/summary.json)，原始测量与警告在同目录。被测实验 jar SHA256 为 `8b1aff6bdf76fd55a85a290cdcae14311fb798c3c4121cd75d7f9614f325b093`。随后修改仅涉及交锋态夹具、独立运行开关、友好保护和显式 import；本报告位移结果对应这一被测版本，不冒称最终 jar 的重新全矩阵测试。

| RTT | 合法次数 | 合法 S08（阈值 0） | 5 格包 S08 | 穿墙包 S08 | 首次拉回与收到攻击包通知的 tick 差 |
| --- | --- | --- | --- | --- | --- |
| 0ms | 40 | **1，失败** | 1 | 1 | 两者均 0 |
| 100ms | 40 | 0 | 2 | 1 | 两者均 0 |

```text
S2B_OBSERVED trial=7 tick=462 dx=1.5999999999999996 ... credit=1.2000000000000002 sweptWall=false
S2B_CORRECT trial=7 tick=462 reason=envelope noticeDelta=-1 illegalX=2.0999999999999996 rollbackX=0.5
```

失败发生在 0ms 的合法踏步：4 个客户端 tick 的 1.6 格变化被一次 END 观察到，超过 3 tick 的 1.2 格额度。其它 39 次没有 S08。100ms 下 5 格包的第二次 S08 来自在途位置回报，首次纠正仍在同一服务端 tick。穿墙包首先被原版 processPlayer 碰撞校验纠正，不能将这条证据归功于 END 扫掠。`noticeDelta` 是同连接中先于位置包发送的探针通知在处理器收到的 tick 差；只读 S08 出站日志提供原生纠正时刻。

斜向使用归一化 X/Z；空中保留原生跳跃/重力；楼梯出现 0.5 格阶跃、水块场地 mode6 中直接施加动作位移，到达 x=2.5，S08=0，未单测液体阻力或记录 inWater 标记。这些样本无额外 S08，并非所有方向、地形、WASD 与动作叠加或低 TPS 均已验证。

代理使用 Windows Python 3.12 `perf_counter` 高分辨率时钟、工作线程的 deadline 等待，发送前再次检查时间，避免 asyncio 粗时钟提前唤醒。最终 100ms 单程测得最小 50.0599、最大 53.8433、平均 50.4199 ms；0ms 是无额外睡眠，平均转发开销 0.2018 ms。早期使用 GetTickCount64/asyncio.sleep 的名义 RTT 数据仅作诊断，没有纳入最终验收。两场客户端/服务器 exit 均为 0，专用服务器实际客户端/OpenGL 类加载均为 0。

## 结论与设计影响

**失败**：原型说明 1.2/S2b 要求每个 RTT ≥40 次合法移动且 S08=0，0ms 的 1 次纠正已构成反例。不能继续增大固定额度来宣称通过。

建议修改 collision/network 中候选 END 方案：研究由动作起始服务端时间、累计合法路程和实际到达批次约束共同定义的累计包络，明确普通移动与动作叠加和丢包/低 TPS 策略，再重跑相同矩阵。此结果推翻的是所测固定 3 tick 额度候选，不足以证明所有 END 校验都不可行。仅取 tick 末坐标还无法证明 tick 内穿出再返回、沿途命中与中间位置安全；需要独立验证这些边界。没有采用新的方案，P0 未开始。
