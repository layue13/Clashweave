> 历史报告：以下是260c878的锁敌实现与当时实测。第三人称STRONG的当前修正见[第三人称跟随与构图](THIRD-PERSON-COMPOSITION.md)，历史数据不覆盖为新结果。

# 锁敌辅助

## 原因、范围与设计取舍

真人反馈“锁敌对瞄准没有任何作用”成立：原实现 lock 仅控制切换、保持、交锋和抗性归属，未接瞄准或镜头。按用户本次要求补齐 L1–L4；开工 fetch 后 merge origin/work 返回 Already up to date，work=4b85c25，包含第 9 节，没有 rebase、新开分支或新开 PR。

input.md 的弱目标辅助与“不保证命中”、presentation.md 的观察/承诺方向分离继续保留。本次用户审核明确第一人称默认 WEAK、第三人称默认 STRONG，取代 presentation.md 当前“建议弱跟随为默认”的泛化提案；建议该段区分两视角，不悄悄放宽转速和手动 F5 规则。模块的“原稿细节保留”未删改。

## L1 标记与所有者同步

StateMessage 增加 lockTarget、lockKeepRange。服务端每 tick 的已有快照只向本人携带有效锁定 ID；观察者副本 lockTarget=-1，JUnit 实际编码/解码验证不泄漏。客户端持刀、目标活着且客户端已知距离≤服务端保持距离才显示头顶黄色 [LOCK] 与距离。

死亡、解锁、客户端已知超距会在下一次渲染取值时立即隐藏；不是保证 100ms 网络能零延迟传播未知的服务端变化。GUI 暂停跟随，标记仍遵守上述生命/装备条件。距离按双方 boundingBox.minY 脚部坐标计算，避免 1.7.10 客户端 yOffset 把 1.9 格误显示为约 2.5 格。标记关闭深度测试以保持文字可辨，因此可显示在遮挡物前；这仅是表现，攻击辅助仍要求服务端 LOS，跟随有遮挡丢失宽限。

## L2 服务端有限承诺辅助

动作开始先取原有 FacingHistory 校验结果（失败仍按原规则回落并记录），再对服务端已锁定、存活、保持范围内且 canEntityBeSeen 的目标计算水平 yaw。目标与校验后按下朝向夹角≤辅助锥才修正；修正为 clamp(目标角差, ±上限)，±180° 正常环绕。重合及非有限目标方向不修正。pitch 保持原校验值，修正量为 0，不能把俯仰强行改向目标。

仅写动作 state.yaw/state.pitch：原命中扫掠、根位移的承诺方向和身体渲染沿用已有实例状态；不写玩家 rotation、不传送、不逐 tick 改承诺朝向。关闭辅助时承诺方向与原历史匹配结果一致。

| 服务端 lock 配置 | 默认 / 范围 |
| --- | --- |
| lockAssistEnabled | true |
| lockAssistCone | 半角 45° / 0–90° |
| lockAssistMaxDegrees | 15° / 0–30° |

获取/保持距离仍为独立 16/20，engageRadius 仍 8，不复用。

## L3 分视角跟随与鼠标

客户端 camera 配置本地生效，不发送定义或目标选择给服务端。视角 0 取第一人称模式，1/2 取第三人称模式。

| 客户端 camera 配置 | 默认 |
| --- | --- |
| lockFollowFirstPerson / lockFollowThirdPerson | WEAK / STRONG（均可 OFF） |
| lockFollowGraceMillis / lockLostGraceMillis | 250ms / 1000ms |
| lockWeakMaxDegreesPerSecond / lockWeakPitchMaxDegreesPerSecond | 180°/s / 180°/s |
| lockFollowDeadZone | 8° |
| lockStrongMaxDegreesPerSecond | yaw、pitch 共用 720°/s |
| lockStrongMaxOffset / lockStrongOffsetDecay | ±30° / 120°/s |
| lockStrongPitchMin / lockStrongPitchMax | -45° / +60° |

WEAK 立即让出鼠标，宽限后只在空闲或恢复期跟随；超过 120° 的三维视线夹角不追转。STRONG 用目标 yaw 加有界鼠标偏移，停鼠后回正，pitch 目标限制在配置区间，动作中继续观察但实例承诺朝向不变。迟滞、GUI/死亡/解锁/超距和超时遮挡均暂停相应跟随。

每 tick 使用单调时间与速度上限，墙钟额度最多 50ms，卡顿或 F5 模式切换不能攒出大角度跳变。修改真实客户端 yaw/pitch，照常 C03；未改 FacingHistory、原版位移处理或 ViewPreservingCorrections。新增委托 MouseHelper：OFF/WEAK 保留原始鼠标，STRONG 消费原始增量为偏移，避免原版自由转动与跟随重复叠加额度。原 MouseHelper 的抓取/释放仍委托执行。

客户端眼点使用原版 EntityPlayer.getPosition(1)：反编译 EntityPlayer.java:2514–2520 为 posY+(eyeHeight-defaultEyeHeight)，不能再次 posY+getEyeHeight；方向指向目标眼点。客户端 LOS 亦从该眼点做原版 rayTraceBlocks。

STRONG 只影响当前观察及未来按下快照；已经开始的动作命中/身体朝向不变。它不能扩大攻击几何，也不保证命中。

## L4 一次自动切换与手动优先

lockAutoThirdPerson 默认 false，lockRestoreView 默认 true（客户端 camera 配置）。一个有效锁定过程只记录一次原视角并可切到背后第三人称；同一过程不反复切换。解锁/目标失效可恢复，期间手动 F5 改过视角则保留选择。死亡、换世界/维度、断线清理临时记录。LockView 的 JUnit 覆盖进入、重复更新、恢复开/关、手动 F5、默认关闭及清理；真实跨维度/F5 键硬件多模组联动未独立补测。

## 构建与运行证据

```powershell
$env:JAVA_HOME='C:\Program Files\Zulu\zulu-25'
$env:VERSION='0.1.0-dev'
.\gradlew.bat spotlessApply spotlessCheck checkstyleMain checkstyleTest build -I tools/p0/validation.gradle p0LaunchFiles --no-configuration-cache
```

最终构建与场景实测见下方追加摘要；不把骨架构建当验收。实验源集仅用于真实场景驱动，未并入 src/main。

## 过程失败与限制

首轮 lock-support-zero 在实体尚未被客户端追踪时过早截图，标记两项 false，且死亡段仅 20 tick 未拍到；这不是通过证据。调整夹具为初始位置设定后生成实体，等待客户端已有实体/装备且实际 LOCK 被服务端接受再开始，死亡观察改 40 tick。之后发现绕后位置经实体插值仍未达到实际 120° 禁止阈值，补强目标位置；再对照源码修正客户端眼点/脚部坐标。过程结果不替代下列最终矩阵；各运行的实际 jar SHA 保留在 runtime-snapshot.json。完整矩阵之后只补了 F5 同 tick 多次切换的事件记录（纯状态机测试）与鼠标委托夹具，未改变辅助/跟随数值算法。

其它 MouseHelper/相机/Netty 管线模组共存未验证；强跟随下显式行政传送后恢复策略未独立测试。强模式响应鼠标以客户端 tick 为节奏，真人偏移舒适度需试玩。未实现越肩偏移、相对锁敌 WASD、正式标记美术或其它后置系统。完成后停止等待审核和试玩。

## 实测摘要（专用服务器与双客户端）

Spotless、Checkstyle、build 通过；JUnit 实际 **47 项，失败/错误 0**，包含原 39 项。XML 与任务摘录：[lock-build](evidence/lock-build/summary.json)。所有下列场景 COMPLETE、进程正常退出，专服客户端/OpenGL 类加载为 0。服务端与两个客户端是真实 Forge 进程；网络代理单程 50ms 对应标称 RTT100，不把标称值当成逐包延迟精确值。

复现（Python 3.12，先执行上述构建；每个标签必须未存在）：

```powershell
python tools/p0/dual.py --label lock-eye-zero --accept-eula --lock-support
python tools/p0/dual.py --label lock-eye100 --accept-eula --lock-support --rtt 100
python tools/p0/dual.py --label lock-eye-view-zero --accept-eula --view-test
python tools/p0/dual.py --label lock-eye-view100 --accept-eula --view-test --rtt 100
python tools/p0/dual.py --label lock-mouse-zero --accept-eula --follow-only
python tools/p0/dual.py --label lock-mouse100 --accept-eula --follow-only --rtt 100
python tools/p0/dual.py --label lock-regression-zero --accept-eula
python tools/p0/dual.py --label lock-regression100 --accept-eula --rtt 100
python tools/p0/lock_support_metrics.py --label lock-eye-zero
python tools/p0/analyze.py --label lock-eye-zero
python tools/p0/playfix_metrics.py --label lock-eye-view-zero
```

其余标签同样运行对应分析器。摘要/逐条结构化观测/关键日志/截图在 [evidence](evidence/) 的同名目录，完整原始进程日志仅留本地 build/p0/runs。没有只挑一次全绿覆盖失败运行。

### L1 逐图判读

两档延迟各五次观测：锁定、解锁、死亡、重新锁定、22格超距，标记状态错误 **0**。截图使用生产渲染回调而非合成叠图：

| 0ms 原始帧 | 判读 |
| --- | --- |
| [锁定](evidence/lock-eye-zero/P0A/screenshots/p0-lock-mark_lock.png) | 怪物头部有黄色 `[LOCK] 1.9 m`，持刀且 owner 状态锁定有效；标记可辨。 |
| [解锁](evidence/lock-eye-zero/P0A/screenshots/p0-lock-mark_unlock.png) | 同一近处怪物仍在，头顶标记消失，HUD 为 UNLOCK。 |
| [死亡](evidence/lock-eye-zero/P0A/screenshots/p0-lock-mark_dead.png) | 目标已死亡移除，标记消失；不留在空位置。 |
| [重新锁定](evidence/lock-eye-zero/P0A/screenshots/p0-lock-mark_relock.png) | 新目标存在，标记重新显示，不继承死亡目标 ID。 |
| [超距](evidence/lock-eye-zero/P0A/screenshots/p0-lock-mark_range.png) | 目标在远处仍可见，22格超过20格保持阈值，标记消失；交锋态不等于仍锁定。 |

100ms 对应五帧和状态观测在 [lock-eye100](evidence/lock-eye100/lock-support-metrics.json)。服务器变化到客户端已知变化仍有网络传播时间；“立即”是每次客户端渲染不继续显示已知失效目标。

### L2 承诺朝向（不是命中率保证）

每档 **关闭20次 + 开启20次**，10°/30°/60°分别7/7/6次，目标距离1.9格，使用真实 light_1。两档结果一致：

| 目标偏角 | 关闭后误差 | 开启后误差 | 辅助修正量 |
| --- | --- | --- | --- |
| 10° | 10° | 0° | 10° |
| 30° | 30° | 15° | 15° |
| 60° | 60° | 60° | 0°（锥外） |

两档各20/20命中，关闭组也是20/20：已有宽扫掠在此近距场景覆盖目标，**不能声称命中率提升或锁定保证命中**。平均方向误差32°→23.25°，最大修正15°，pitch 修正0；遮挡专项出刀目标30°，实际修正0、未命中。两档64/64动作执行、历史匹配回落0、辅助上限违例0。纯函数 JUnit 还覆盖边界、±180°环绕、重合和 NaN。

### L3 跟随、鼠标、F5 与承诺分离

| 完整绕身矩阵 | 0ms | 100ms |
| --- | --- | --- |
| WEAK / STRONG 输出 yaw 峰值 | 180 / 720°/s | 180.000021 / 720.000001°/s |
| 超上限违例（浮点容差0.01°/s） | 0 / 0 | 0 / 0 |
| WEAK 绕后停止样本 | 56 | 57 |
| STRONG 动作中继续跟随样本 | 24 | 26 |
| 同动作实例承诺 yaw 变化 | 0 | 0 |
| 遮挡宽限到期 / GUI 停止样本 | 13 / 40 | 13 / 40 |
| F5 STRONG→WEAK 下一 tick 转角 | 0° | 0° |
| F5 WEAK→STRONG 下一 tick 转角 | 10.683° | 9.924° |

F5 转角均小于该 tick STRONG 可用约36°，没有瞬间对准；STRONG 绕后连续转动也逐 tick 受上限约束。WEAK 动作承诺期暂停有 JUnit 覆盖，本次完整绕身未单独发起 WEAK 承诺期出刀，不冒充实测。pitch 目标边界与速度限制有纯逻辑测试；真实不同身高/垂直绕身矩阵未补测。

另以生产 `LockCamera.wrap` 委托路径注入 MouseHelper 原始 deltaX=33（输入采集器为脚本替身，其后使用真实生产路径与原版 setAngles）：WEAK 返回 deltaX33，下一 tick reason=MOUSE、跟随转角0；STRONG 返回deltaX0，把4.95°计入偏移，不给原版自由转动额外额度。

| 鼠标补测 | 0ms | 100ms |
| --- | --- | --- |
| WEAK 下一 tick 让出响应 | 50.026ms | 48.611ms |
| STRONG 下一 tick 偏移响应 | 49.589ms，4.95° | 49.995ms，4.95° |
| WEAK / STRONG 峰值 | 180 / 720°/s | 180.000021 / 720.000081°/s |
| 转速 / 承诺改写违例 | 0 / 0 | 0 / 0 |

这表示 tick 节奏的脚本响应；没有测物理鼠标驱动的输入到光子延迟，也不能据此替代真人舒适度。完整矩阵中的直接5°注入响应另保留逐条数据，不与委托路径混计。

第三人称对照各10次：相同30°目标、相同服务端辅助开启，关闭镜头时按下方向平均误差30°、承诺后15°；STRONG 时按下误差 **0.392711° / 0.470683°**，承诺后均浮点误差内0°（0/100ms）。三种组均10/10命中，仍非命中率提升证明。真实追目标的 C03 历史匹配通过，已开始的动作承诺没有改变。

### L4 与视角保护回归

自动第三人称默认关闭；状态机测试覆盖自动一次/解锁恢复/关闭恢复/手动 F5/同 tick 多次 F5 后视角回到原值仍算手动/生命周期 clear。实际 F5 模式切换见上表；跨维度与断线恢复组合未新增真人实测。

连续转动视角的回归两档各 **20条完整连击、61次动作开始**：合法序列内 S08=0，动作开始 yaw/pitch 变化最大均0，位置包导致视角变化违例0。0ms 初始序列外还有1个未标记原版旋转包，保留原版行为，没有计成受保护战斗包；100ms该项0。见同名 view-metrics.json。

补测中的输入门拒绝必须区分：完整矩阵0ms无拒绝，100ms有9条 kind=-2 初始化校时确认 STALE；鼠标补测0ms有7条此类 STALE，100ms有50条此类 STALE和8条 RATE。它们不是动作意图；实际出刀均执行、历史回落0。原有输入门行为未改，未隐藏这些初始化/成批校时消息。

常规战斗回归 `lock-regression-zero` / `lock-regression100`：合法输入61/61、62/62执行（100%），各4条非法派生拒绝、未执行；35次预测对齐均无回退/硬纠正，格挡与逻辑/墙钟双截止违例均0，怪物额外提交tick仍0。不同合法总数来自真实输入及场景分支，未裁成同分母。原39项测试全部保留，新增8项；没有因锁敌改动作时序、格挡、防御、交锋或预算。默认跟随会使未来普通C03与按下角度改变，这正是镜头操作的自然输入，不是跳过历史匹配。

结论：L1–L4 已有实现及以上脚本/JUnit证据；物理鼠标体感、自动视角的真实跨维度/断线组合、其它相机/Netty模组共存仍未验证。等待用户审核与再次试玩，不开始后续系统。
