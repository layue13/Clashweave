# 第三人称跟随与构图（第一步）

## 原因与范围

真人认为第一人称可用，第三人称强跟随不如魂类。旧 STRONG 每 tick 最多36°线性追平、近处眼点 pitch 直连、±30°鼠标偏移与快速回正、20Hz更新以及居中背后机位，确会传递目标抖动与遮挡。本次只修第三人称 STRONG，默认第一人称 WEAK、可选第一人称 STRONG 的 LockFollow、鼠标额度与 tick 调用路径均保留；第三人称 WEAK/OFF 也沿用旧路径。没有改动作时序、攻击辅助、历史匹配、防御、延后提交、交锋和预算。

开工 fetch+merge origin/work 返回 Already up to date，work=4b85c25；无 rebase、新分支或新PR。优先级依用户新反馈：模块 presentation.md 泛称弱跟随/约80ms常数的旧候选不约束本次第三人称 STRONG 的0.15s阻尼，模块“原稿细节保留”未删。GOLDEN 是本次指定的暂定配置起点，不是舒适证明；等待用户在三个预设中选择。

## 实现与配置

| 新增配置（camera，除注明外） | 起始 / 边界 |
| --- | --- |
| lockCompositionPreset | GOLDEN，可 THIRDS/CENTER/OFF |
| lockStrongSmoothTime | 0.15s，0.03–0.5s |
| lockStrongThirdPitchInfluence | ±8°，0–30° |
| lockCompositionHorizonTolerance | 0.025H，0–0.1H；默认留出验收0.03H余量 |
| lockTargetFilterTau | 0.1s，0–1s（0为关闭低通） |
| lockStrongThirdMaxOffset | ±8°，0–90° |
| lockStrongThirdOffsetDecay | 240°/s，0–720°/s |
| lockFlickWindowMillis | 150ms，20–500ms |
| lockFlickThresholdDegrees | 25°，5–90° |
| lockFlickCooldownMillis（lock，客户端与服务端分别加载） | 400ms，100–2000ms；服务端独立强制 |

旧 lockStrongMaxDegreesPerSecond=720 上限仍适用；旧 lockStrongMaxOffset=30、lockStrongOffsetDecay=120 **不删除、不改第一人称的默认与语义**，因此第三人称新起始值采用独立 Third 配置。将旧字段默认直接改8/240会改到可选第一人称 STRONG，违背用户“第一人称行为不改”，这是显式取舍。lockFollowThirdPerson 可选 WEAK/OFF 回旧路径，lockAutoThirdPerson/lockRestoreView 与手动 F5 规则保留。

S1：纯逻辑 ThirdPersonCamera.Spring，对分段常值目标使用临界阻尼解析步进，omega=2/smoothTime，error=x-goal，temp=(velocity+omega×error)×dt，decay=exp(-omega×dt)，next=goal+(error+temp)×decay，velocity=(velocity-omega×temp)×decay；过冲夹到goal，再限制本帧变化≤speed×dt。真实dt上限50ms，暂停不能攒镜头瞬转额度，yaw采用±180最短差展开。

S2：GOLDEN h=0.382、THIRDS h=1/3、CENTER h=1/2：

```text
pitchDown = atan((1−2h) × tan(vFOV/2))
yawBias   = atan((1−2h) × aspect × tan(vFOV/2))
shoulderOffset ≈ distance × tan(yawBias)
```

配置70°、16:9时 GOLDEN pitchDown≈9.38°、yawBias≈16.37°、distance4的肩偏移估计≈1.175格。FOV与实际宽高比每帧取新值，纯公式测试含60/70/90/110°与不同aspect；生产第一步只用纵向公式，**没有肩偏移，不宣称目标/玩家已经分居横向黄金线**。OFF保留有界的眼点pitch目标，仍有平滑和速度上限。

目标高度的附加影响先限制±8°；实测发现仅这个限制仍会使地平线越界，故再用上述公式反算 h±0.025 的两个 pitch 边界，将影响限到较小角差内。70°时有效允许高度影响约±2°，而不是默默把配置8改小；0.025只是新增可配的构图保护，JUnit证明实际目标俯仰不越该屏幕包络。仍按旧 pitchMin/Max 限制。FOV动态效果（疾跑/药水/受击）可能使实际投影FOV不等于 fovSetting，本轮只测静态70°；改变设置会重算，不宣称动态FOV场景已通过。

S3：仅相机使用目标插值位置，再指数低通 alpha=1−exp(-dt/tau)，不改目标实体字段、服务端锁定或辅助。切目标/换世界清空滤波状态，GUI/遮挡超宽限/失效不写旋转。

S4：第三人称 STRONG 的原始水平鼠标量先换为角度，在150ms窗口累积，方向反转/超时重置；超过25°向服务器发新增末尾 Intent.LOCK_SWITCH（旧意图ordinal不变），方向只有−1/+1。服务器需要持刀、有效锁定、输入门通过、冷却到期；在保持范围内只选可见、存活、非本人、非当前、非受保护友好实体。按相对当前目标方向的同侧最小角差选取，距离微量次序与实体ID稳定同分；客户端不发新目标ID、无远处/穿墙特权。冷却用收包单调时刻，伪造客户端冷却不起作用。小鼠标量保留±8°偏移，停鼠240°/s回正；第一人称不产生甩动意图。

S5：仅本地持刀有效锁定的第三人称 STRONG、无动作实例时，RenderPlayer 的身体模型使用相机平滑yaw；头部观察差补偿，实体 renderYawOffset/rotation 等状态不写。动作实例（含恢复）仍用权威承诺yaw，武器挂点沿用同一模型矩阵，不改命中。

S6：第三人称 STRONG 在 RenderTickEvent.START 做相机帧更新；玩家真实 rotationYaw/Pitch照常发C03。tick继续处理生命周期、所有者快照、自动视角与旧第一人称/WEAK；没有引入 EntityRenderer 钩子、传送或 S08，也未改 ViewPreservingCorrections。弱跟随的输入优先和20Hz语义因“第一人称不变”保留，这是有意限定而非遗漏。

## 复现与证据

```powershell
$env:JAVA_HOME='C:\Program Files\Zulu\zulu-25'
$env:VERSION='0.1.0-dev'
.\gradlew.bat spotlessApply spotlessCheck checkstyleMain checkstyleTest build -I tools/p0/validation.gradle p0LaunchFiles --no-configuration-cache
python tools/p0/dual.py --label composition-final-zero --accept-eula --composition
python tools/p0/dual.py --label composition-final100 --accept-eula --composition --rtt 100
python tools/p0/composition_metrics.py --label composition-final-zero
python tools/p0/composition_metrics.py --label composition-final100
python tools/p0/analyze.py --label composition-final-zero
```

场景：实际专服+两个客户端，服务器驱动同一个目标做静止、30°/s绕身、±0.3格5Hz横向抖动、±1格1Hz上下跳动，每段10秒；每段最初2秒是状态切换与收敛，统一只统计随后8秒，不以结果选择时间段。鼠标静止与小幅各20次，之后大甩动与冷却请求。GLU.gluProject 使用真实 OpenGL modelview/projection/viewport 投影远处水平参考点，不用公式冒充测量。运行真实60fps上限，帧率变化只做纯逻辑dt测试，非多种真实刷新率硬件测量。

过程未隐藏：composition-zero 的旧自动攻击夹具未停，数据混杂，不作验收；修正隔离后 composition-zero-isolated 与 composition100 揭示上下跳动地平线越界，保留摘要和关键摘录，属于失败基线。增加屏幕包络后 **最终0/100各一次** composition-final-zero/final100，不挑一组绿覆盖失败；每个jar SHA与命令保留。第一次包含实验源集的构建因ASM星号import的Checkstyle失败，改为显式import后通过，没有关规则。

## 最终实测

Spotless、Checkstyle、build 通过；JUnit **56 项实际运行，失败/错误 0**（原47项加9项）。构建证据见 [composition-build](evidence/composition-build/summary.json)。

| 场景 | yaw峰速 0/100ms (°/s) | yaw峰加速度 0/100ms (°/s²) | pitch峰速 0/100ms | pitch峰加速度 0/100ms |
| --- | --- | --- | --- | --- |
| STILL | 0.0000/0.0000 | 0.0000/0.0000 | 0.0000/0.0000 | 0.0126/0.0000 |
| ORBIT | 31.0615/30.7840 | 47.1670/11.8676 | 0.0110/0.0134 | 0.1948/0.1198 |
| JITTER | 9.3470/8.7656 | 112.4382/402.2084 | 0.0008/0.0009 | 0.0369/0.0374 |
| JUMP | 0.0006/0.0000 | 0.4584/0.0000 | 16.7088/17.1304 | 287.4349/299.0863 |
| MOUSE | 134.9823/151.6591 | 3322.7632/3551.2487 | 0.0032/0.0032 | 0.0719/0.0744 |

上述是实际玩家 rotationYaw/Pitch 的帧写入变化，不把它冒充新增越肩矩阵后的完整相机轨迹。速度上限违例两档均0；加速度没有预设阈值，完整值保留供比较。抖动衰减比 **0.52974 / 0.49442**，明显小于1；服务器±0.3格5Hz输入经原版实体跟踪量化，客户端实到约±0.3125格，数据不等同理想连续5Hz传递函数。跳动 pitch 峰峰值 **3.23083 / 2.92783°**，小于16°；真实GLU地平线最大误差 **0.024850 / 0.024874H**，±0.03H违例0。只展示公式并不足以保证此结果：初版仅±8°高度限幅仍越界，失败基线已保留，新增 lockStrongHorizonTolerance=.025 的屏幕包络把实际高度影响进一步收窄到约±2°。这是明确的实现取舍，不将8°当实际振幅。

鼠标静止20次、小幅2°每200ms移动20次，两档各0/20误触；并不代表任意连续小幅累积都不会越过25°。大甩动在170、190tick各触发一次；172tick反向甩动被客户端冷却拦截。直接服务端请求在约198/244ms被400ms冷却拒绝，约1s后因该方向无候选拒绝。完整到达时间/目标选择见各档 composition-metrics.json。目标已失效或健康值0时不切换；最后补的“死亡同tick甩动”健康值保护经构建验证，该网络交错场景未单独实测。

## 第一人称与视角回归

用旧47项版本的实际jar作为 baseline（runtime-snapshot保存SHA），同一 FIRST 夹具与0/100ms各一次：35°起始，WEAK最终8°，单tick最大9°；鼠标介入时写入变化0，动作承诺中变化0。旧版 WEAK 帧样本189/188，当前190/187；旧承诺样本7/8，当前7/9，实际网络调度产生采样数差异，不声称逐帧字节一致。鼠标5帧两版均让出。原第一人称算法、参数与tick更新保留；可选第一人称 STRONG 未新增实际运行矩阵，原逻辑测试与新增视角交接测试通过。

最新 composition-release-view-zero / composition-release-view100 各20条完整连击、61次动作开始：合法序列 S08 **0**，动作开始 yaw/pitch变化 **0/0**，位置包造成视角变化违例 **0**；合法61/61执行，格挡不变量/双截止违例0，专服客户端类加载0。序列之外各1次未标记原版旋转包仍按原版处理。先前 composition-view100 登录未完成（server exit1，原因未查明），没有删除；retry与最终release均完成，失败摘要保留，不能只报告绿数据。

证据：[0ms](evidence/composition-final-zero/composition-metrics.json)、[100ms](evidence/composition-final100/composition-metrics.json)、[第一人称旧版0ms](evidence/composition-first-baseline-zero/composition-metrics.json)、[第一人称旧版100ms](evidence/composition-first-baseline100/composition-metrics.json)、[当前0ms](evidence/composition-release-first-zero/composition-metrics.json)、[当前100ms](evidence/composition-release-first100/composition-metrics.json)、[视角0ms](evidence/composition-release-view-zero/view-metrics.json)、[视角100ms](evidence/composition-release-view100/view-metrics.json)、[失败过程摘要](evidence/composition-development/)；完整原始进程日志只保留本地 build/p0/runs。

## 限制与审核

数值矩阵之后仅追加第三→第一视角的内部状态清理和上述健康值0保护；前者又跑第一人称与视角release回归，后者构建通过但特定网络交错未验证。原版动态奔跑/药水FOV、其它相机/Netty模组、不同真实刷新率未验证。非动作期身体镜头朝向只应用本地玩家渲染；观察者仍用原版身体朝向。地平线是远处水平参考方向GLU投影，有限区块边缘/地形线不是几何地平线。鼠标舒适性与魂类体验未取得本次真人评价。

越肩不会随此生产改动启用，独立结论见[越肩可行性探针](CAMERA-SHOULDER-PROBE.md)。本阶段完成后停下审核；由用户对比三个预设决定默认，不把黄金分割当舒适证明。
