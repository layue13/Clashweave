# S2c 累计墙钟预算：通过（原矩阵与批量包负载）

## 假设与做法

独立 `tools/probes2/s2c` 源集复用 S2b 客户端 moveEntity/C03 和原场地矩阵。服务端以 System.nanoTime 的真实经过时间计量，不是可跳变的日历时钟；END 累计实际 X/Z 路径长度与 `速度 × (经过纳秒 / 50,000,000 + marginTicks)` 比较，默认余量2 tick。另受动作声明的总距离上限（5tick×速度，踏步2/突进4格）约束，动作不会因网络等待获得无限距离。没有自制扫掠，墙交给原生 processPlayer。

先准备场地传送，客户端等待12tick确认稳定并发 ready；服务端启动预算后发开始确认，客户端下一 START 开始动作。计时包含确认传输的真实经过时间。这是动作开始握手夹具，不是客户端任意时间戳授权。动作自身速度、时长与余量可配置；普通 WASD、击退等叠加未实现。

0/100ms各40合法位移：踏步、突进、墙前突进、斜向、空中、楼梯、水块场地，次数6/6/6/6/6/5/5。清零客户端 S08 计数发生在首个动作样本，准备传送不计纠正。非法5格C04、单包跨墙、同一客户端 tick 发送跨墙5.5→6.5→0.5三包；另触发服务端 START sleep 的人为长tick，保留原生每包处理。

Netty 监听器只记录 C03 与 S08，不拦截、取消、改写或接管原版处理。END 校验只有预算分支；没有使用位置消息代替实际 player.posX/Z。

## 命令与实测输出

```powershell
$env:JAVA_HOME='C:\Program Files\Zulu\zulu-25'
$env:VERSION='0.1.0-dev'
.\gradlew.bat -I tools/probes2/s2c/experiment.gradle s2cLaunch --no-configuration-cache
python tools/probes2/s2c/run.py --accept-eula --label new-matrix --rtts 0,100 --stall-ms 200
python tools/probes2/s2c/run.py --accept-eula --label new-extra --rtts 0,100 --extra-only --stall-ms 50
```

新 label，EULA 已接受，回环专用服25575/代理25576。有效证据：原矩阵 [0ms](evidence/s2c-clock1-rtt0/summary.json)、[100ms](evidence/s2c-clock1-rtt100/summary.json)；一 tick 延长补测 [0ms](evidence/s2c-extra50-rtt0/summary.json)、[100ms](evidence/s2c-extra50-rtt100/summary.json)，各目录含原始测量与警告。

| 原矩阵 RTT | 合法次数 | 合法 S08（要求0） | 5格 S08 | 单包跨墙 S08 | 三包跨墙 S08 | 非法首次纠正 tick 差 |
| --- | --- | --- | --- | --- | --- | --- |
| 0ms | 40 | 0 | 1 | 1 | 2 | 三种均0 |
| 100ms | 40 | 0 | 1 | 1 | 3 | 三种均0 |

```text
S2C_STALL tick=1849 elapsedMs=202.3415
S2C_OBSERVED trial=43 tick=1849 dx=1.6 ... travelled=0.3999999999999999 budget=2.0
S2C_CLIENT trial=43 mode=10 S08=0 x=2.5 ...
```

长tick矩阵每档额外1次，200ms 人为暂停后批量位移无误纠正。一 tick 补测设 sleep50ms，实际0/100档停63.0272/62.6993ms，END 观察到0.8格两包批量变化，均S08=0。四场服务器与客户端exit=0，专用服务器客户端/OpenGL类加载0。

5格包造成 END `reason=budget`，通知处理与第一次 S08 同tick。跨墙单包和三包只有原版 S08，没有 END budget 纠正；三包在原生到达日志中有相同服务端tick，原版 processPlayer 的碰撞残差/最终AABB检查（NetHandlerPlayServer 约363–400行）和 hasMoved 传送确认门逐包生效。原版纠正会令后续包等待确认，不能声称每个后续包都独立移动或把END预算当穿墙阻挡。它证明所测单包/出墙再返回批次未穿过墙，不外推任意脚本或其它模组改动。

斜向归一化，空中保留原生 jump/gravity，楼梯dy=0.5；水块场地直接施加动作位移并记录inWater，未单测游泳阻力与动作叠加。最终位置和总路程在每条 END/CLIENT 日志中。

两版 jar 明确区分：完整矩阵 SHA256 `a72c00d4c1922bead8ba151e6773381a4bbdab24270692fe60945b06fc968351`；最终补测 `d8f0f9b60a29c491b7e3eca68363b894f5d6a23bea453999e487b923655ce8c6`。后一版将暂停量参数化、新增extraOnly夹具开关并修正只读原生包日志的z getter；完整矩阵旧原生日志的z标签实际是stance，实际校验一直使用player.posZ/正确dz，不依据该打印字段。

高精度FIFO代理：完整矩阵100ms单程最小50.0548、平均50.4357、最大56.0282ms；0ms转发平均0.1929ms。补测100ms最小50.0656ms。没有采用第二轮的粗时钟数据。

## 结论与设计影响

**通过所测标准**：两档各40合法S08=0；非法首次拉回≤1tick；同tick多包墙由原生处理；一个人为延长tick的批量包无误纠正。支持原型说明1.1/S2和1.2/S2c的累计预算方向，建议明确单调时钟、余量2tick、声明总距离上限与准备传送/预算开始握手。

目标位移的纵向预算、普通移动/击退合并、低TPS持续运行、失效动作、瞬移模组和载具尚未验证；END 只观察最终路径，不保证同tick中间位置的攻击几何。这些边界应在P0/PvP接入再验，不冻结公开API。S4b/S5b代码未改；副本中继承的交锋场景不启用，未冒称重新验收。
