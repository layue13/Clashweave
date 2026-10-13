# C2b-1 纯逻辑：两段 IK 与铰链欧拉分解

## 状态

**未通过验收。** 最终指定命令输出 `BUILD FAILED in 8s`，没有 `BUILD SUCCESSFUL`。JUnit XML 实际执行 226 项，基线 214，新增 12；225 通过、1 失败，errors=0、skipped=0。失败是新增的相邻样本欧拉角 ≤0.5 rad 断言，未放宽或跳过。既有 214 项全部通过。

新增 LimbAngles、TwoBoneIk、三个测试类；只给 LimbSolver 添加方法/常量，给 PoseAllocationTest 追加测试。开始时 git status 为空，最终变更仅限任务指定的八个文件（含本报告）。没有提交、推送、创建/切换分支，没有改轨迹、Pose、Track、TrackData、PoseBlend 或渲染源码。读取 C2a 报告和逻辑契约及现有 pose 代码/测试，保留既有坐标映射与方法行为。新增代码没有 Minecraft/LWJGL/FML import。三个主代码类 classfile major 均为 52（Java 8）。

工作清单：背景核对、实现、测试、报告已执行；连续性未解决；未做渲染接线。

## 新公开 API 契约

数组至少三个元素；正常输入有限，长度正且有限。单位 Biped px/16，角度弧度。热路径无分配。不同 LimbAngles 实例独立，同一实例不得并发使用。

| API | 契约 |
| --- | --- |
| LimbAngles() | 独立 upper[3] 和包内 scratch；构造允许分配。upper 是上段 X/Y/Z，childAngleX 是下段有符号 X，flex 非负，reachable 表示无需距离或解剖夹紧；shortfall 是最终 FK 与原目标的欧氏距离，可达时精确为 0。 |
| TwoBoneIk.solve | pole 是方向而非位置；输出独立数组中的上下段单位方向。半径夹到 [abs(l1-l2),l1+l2]；太远伸直，太近折叠到最小半径。目标重合选择 +Y 射线，等长零半径上段沿正交 pole，下段反向。pole 平行/零时选择与射线最不平行的 Cartesian 轴，平局 X、Y、Z。无状态。非法长度抛异常；不承诺非法数组/点的异常形式。 |
| LimbSolver.solveHinged | 局部 +Y 为归一化 upperDir，+X 为 unit(upperDir×pole)，+Z 为 X×Y。平行/零 pole 使用最不平行 Cartesian 轴替代，平局 X、Y、Z。输出 Rz·Ry·Rx 欧拉角；可超过 π，不保证全局连续。upper 必须非零。轴只依赖 upper/pole，不读下段或历史帧。 |
| LimbSolver.solveArm | 已有肩点 px÷16；手点按现有 bodyToBipedPoint 转换。左右镜像 pole，距离/解剖夹紧后填输出。不改 Pose，不重复加 torsoYaw。 |
| LimbSolver.solveLeg | 已有髋点 px÷16；脚目标为 body (footX,footY,0)，即下端脚底。镜像膝 pole，其余同手臂。 |
| LimbSolver.endPoint | 两段各 .375，按规定 FK 回代；pivot 已是 Biped 单位。输出可与 pivot 共用数组。静态、零分配。 |

包内 scratch 不属于外部契约。有限、确定的退化结果不等于任意奇点穿越时连续，见未通过项。

## 几何与符号推导

沿用 C2a：点映射 (-x/S,1.5-z/S,-y/S)，方向映射 (-x,-z,-y)，S=.9375。肩/髋输出 px，除以16；两段各6px=.375。

令目标射线单位方向 d、正交投影归一化 pole 为 p、夹紧半径 r。肘相对 pivot 为 a*d+b*p，a=(r²+l1²-l2²)/(2r)，b=sqrt(max(0,l1²-a²))。上段除以 l1，下段为 ((r-a)*d-b*p)/l2。等长 r=0 取 a=0，避免除零。使用 double 中间值，平方根微小负值夹零。通用 IK 不做解剖限制。

上段矩阵列 [h,u,h×u]，h=unit(u×pole)，为右手正交框架。一般欧拉分解 y=atan2(-R20,hypot(R00,R10))、x=atan2(R21,R22)、z=atan2(R10,R00)。cosY≤1e-10 时固定 x=0，z=atan2(-R01,R11)，覆盖两种万向锁符号。X/Z 小于 −π/2 时加2π，让膝的 π 静止角在同一表示分支；固定区间仍有切口。

Rx(c)*(0,l,0)=(0,l*cos(c),l*sin(c))，所以 FK=pivot+R*(0,l+l*cos(c),l*sin(c))。负 child X 转向局部 −Z，亦即 pole 在 upper 正交平面的反方向。肘 pole 向后时下段向前，膝 pole 向前时下段向后。测试用 FK 验证左右镜像的世界 Z 符号，没有照抄文字描述。

**重要限制：** 上段可能越过 pole，此时 u×pole 反转，匹配原 IK 下段所需的 child X 变正。不能一律写 −flex 并声称目标可达。最终实现通过下段在上段局部 Z 的投影检测正向请求，把禁止正角夹到0，保留上段，不反转铰链，reachable=false，shortfall 用最终 FK 重算。30个左臂样本因此不可达，最大误差约 .707。该策略不保证在所有合法上段方向中找到最近解，不能把这些误差当作轨迹本身无解的证明。

145° 对应等长最小半径 2*l*cos(145°/2)，约 .2255。过近先投到此半径再重求 IK；过远指向原目标伸直。重合的 arm/leg 目标选择朝 pole 反方向的射线，使最大屈曲保持合法符号。若再发生禁止方向夹紧，shortfall 仍以最终 FK 欧氏误差为准。最终 childAngleX=−flex，flex∈[0,145°]。

## pole 与既有常量

| 肢体 | 右 pole | 左 pole | 理由 |
| --- | --- | --- | --- |
| 臂 | (-.35,1,.2) | (.35,1,.2) | 采用规格示例：下为主，略向外、后。 |
| 腿 | (-.2,0,-1) | (.2,0,-1) | 前为主，略向外。 |

均为固定 Biped 方向，不再随 torsoYaw 旋转。已有 hipPoint 的 ±1.9px 与生成器 body 髋常量 .117 不完全相同，C2a 已记载；本轮未改。肩点同样沿用精确 ±5/2px，不重新映射烘焙的 .293/1.289。

## 验证与逐字输出

沙箱仅工作树可写，直接使用工作树 GRADLE_USER_HOME；没有尝试修改用户缓存。从 C2a 工作树复制 wrapper/caches/jdks 到 build/c2b-gradle-home。初次复制6个锁文件因其他进程占用失败，缓存主体已复制；首轮构建因 jdks 尚未复制完成而失败，补拷贝后继续。未改 Gradle、依赖或工具链要求。

首轮关键输出：

```text
> Task :decompileSrgJar FAILED
> Failed to query the value of task ':decompileSrgJar' property 'java17Launcher'.
BUILD FAILED in 19s
16 actionable tasks: 14 executed, 2 up-to-date
```

最终命令：

```powershell
$env:JAVA_HOME='C:\Program Files\Zulu\zulu-25'
$env:VERSION='0.1.0-dev'
$env:GRADLE_USER_HOME="$PWD\build\c2b-gradle-home"
.\gradlew.bat spotlessApply cleanTest spotlessCheck checkstyleMain checkstyleTest test build --no-configuration-cache --console=plain
```

build/c2b-validation-final.log 的逐字摘要：

```text
> Task :spotlessApply
> Task :cleanTest
> Task :spotlessCheck UP-TO-DATE
> Task :checkstyleMain UP-TO-DATE
> Task :checkstyleTest
> Task :test
226 tests completed, 1 failed
> Task :test FAILED
BUILD FAILED in 8s
30 actionable tasks: 6 executed, 24 up-to-date
```

cleanTest 后确实重跑测试；JUnit XML 总数226。新增 TwoBoneIkTest 4、HingedLimbTest 5、HingeAxisTest 2、追加分配测试1，共12。主/测试 Checkstyle XML error 数均为0，Spotless通过。test失败导致后续build未完成，不能声称打包通过。开发中另用本机 Zulu8 javac/java、缓存 Gson/JUnit/Hamcrest独立编译诊断；开发运行也曾失败，不替代最终验收。

最终 FK/连续性 system-out：

```text
C2B_IK samples=168 limbs=672 maxReachableError=3.478711107352004E-7
C2B_IK limb=0 unreachable=0 maxShortfall=0.0
C2B_IK limb=1 unreachable=30 maxShortfall=0.70680594
C2B_IK limb=2 unreachable=38 maxShortfall=0.014982169
C2B_IK limb=3 unreachable=36 maxShortfall=0.09942578
C2B_CONTINUITY light_1 leftLeg t=6.5 deltaX=0.5857208
C2B_CONTINUITY maxDelta=6.1838026 iai limb=0 t=9.0 axis=0
C2B_CONTINUITY limb=0 maxDelta=6.1838026
C2B_CONTINUITY limb=1 maxDelta=4.4082484
C2B_CONTINUITY limb=2 maxDelta=0.19136596
C2B_CONTINUITY limb=3 maxDelta=0.5857208
```

limb依次为右臂、左臂、右腿、左腿。总不可达104/672，最大shortfall=.70680594。可达误差小于 .002；全部输出有限，屈曲在范围内。不可达结果也检查了 distance(FK,原目标)==shortfall，容差2e-5。

新增五条热路径各预热20,000次、实测1,000,000次；沿用既有64KiB bookkeeping容差，不扣计数器开销。最终 system-out（标签沿用既有写法）：

```text
C2A_ALLOC counter baseline bytesPerMillionCalls=856
C2A_ALLOC Track.sample bytesPerMillionCalls=0
C2A_ALLOC LimbSolver.solveLimb bytesPerMillionCalls=0
C2A_ALLOC LimbSolver.swordMatrix bytesPerMillionCalls=0
C2A_ALLOC LimbSolver.shoulderPoint bytesPerMillionCalls=0
C2A_ALLOC Pose.blend bytesPerMillionCalls=0
C2A_ALLOC PoseBlend.update bytesPerMillionCalls=0
C2A_ALLOC counter baseline bytesPerMillionCalls=0
C2A_ALLOC TwoBoneIk.solve bytesPerMillionCalls=0
C2A_ALLOC LimbSolver.solveHinged bytesPerMillionCalls=0
C2A_ALLOC LimbSolver.solveArm bytesPerMillionCalls=0
C2A_ALLOC LimbSolver.solveLeg bytesPerMillionCalls=0
C2A_ALLOC LimbSolver.endPoint bytesPerMillionCalls=0
```

## 未通过项、实验与不确定性

连续性断言仍为 .5rad。右臂6.1838包含表示切口；左臂4.4082存在近万向锁/框架变化，不能简单视为2π差；左腿light_1在6.0→6.5tick的 .5857208 是同一分支上的真实增量，仅展开2π不能消除。

诊断先固定指标“最大相邻欧拉增量”及 .5 判据，再用真实JSON和独立double公式比较固定pole。build/pole_experiment.py测试膝外侧 .05/.1/.2/.3/.35/.5/.7/1，Y=0、前=−1，最大约 .582–.632；均未通过。build/pole_grid.py又比较35组膝pole（外侧 .05/.1/.2/.35/.5，Y=−2/−1/−.5/0/.5/1/2，前=−1）与四种固定切口，最小约 .58165。arm候选也未找到全部通过的固定chart。仅是有限候选实验，不是对所有pole或算法的不可能性证明；最终未采用候选，也没调整阈值。脚本在忽略的build目录，可重跑，不是交付源码。

轴不依赖下段，并不保证任意轨迹无反转：upper平行pole时定向框架没有唯一解，固定fallback保证有限/确定，不能保证两侧极限相同。HingeAxisTest通过膝向前pole、目标射线向下的0→140°扫描（步长.1°）以及矩阵回代、两种万向锁测试；没有证明任意pole奇点穿越的连续性。这是与“退化输入不得铰链翻转”强保证的差距。

连续性仍未解决。可能需要有历史的等价欧拉分支选择，以及评估真实轨迹中超过 .5 的几何变化或pole策略；只展开历史角度不能解决左腿 .5857。未改轨迹、未加时间平滑，未把不精确落点伪装成可达。未运行游戏、ModelRenderer或OpenGL；视觉可用性和大幅左臂夹紧误差未验证。暂不推荐渲染接线。已按任务要求停止。

## 第二轮：pole 场反例与验收阻塞（2026-10-13）

**未修复、未通过验收。** 本轮验证了 Lead 的真实旋转翻转诊断，但指定的新 pole 公式也有零点，且从 P1 到 P2 的要求路径恰好穿过零点。没有把这个候选公式接入生产求解器，没有宣称修复完成。最终指定命令为 `BUILD FAILED in 9s`，JUnit XML 实际 230 项：226 通过、4 失败、errors=0、skipped=0。原连续性失败 1 项，新增候选设计契约测试失败 3 项。没有放宽阈值、删除或跳过断言。

工作清单：

- [x] 读第一轮报告和 Lead 更新的矩阵连续性测试，检查当前状态。
- [x] 重跑原基线：226 项、1 失败，复现左臂 iai 翻转。
- [x] 证明并数值复现候选 pole 场的抵消零点及两步法的 FK 精度问题。
- [x] 新增 `PoleFieldTest` 四个可重跑实验并执行完整指定命令。
- [ ] 生产 IK 修复、全局连续非零保证、新 pole 热路径分配覆盖、BUILD SUCCESSFUL：均未完成。
- [x] 追加本节并按失败出口停止。

### 修改范围与原测试

本轮新增 `src/test/java/com/layue13/clashweave/client/pose/PoleFieldTest.java`，追加本报告。三个生产源码、`PoseAllocationTest` 及其他既有测试没有本轮逻辑修改。起始工作树已有第一轮修改/未跟踪文件，全部保留。未执行 Git 提交、推送、创建或切换分支，未改任何其他源码。

`adjacentTrackRotationsAndFlexionStayContinuous` 保持 Lead 版本及 `<1.0` 断言不变。`anatomicalClampKeepsClosestAllowedEndpoint`、`forbiddenFlexIsClampedRatherThanReversingHingeAxis`、`fkConfirmsElbowForwardAndKneeBackwardLowerDirection` 三项均保持原断言并通过；没有因新设计调整它们。特别是 forbiddenFlex 测试仍锁定旧实现 iai t=0 的夹紧行为，本轮没有将其改成修复后的行为测试，因为尚无合格生产实现。

### 指定公式为何仍有奇点

设 A=P1、B=P2，F(u)=(I−uuᵀ)(A+w(u)B)，w(u)=(u·Â)²。P1、P2 不平行并不能保证 F 非零：它们的垂直投影可方向相反并相互抵消。

给出直接证明，无需假设采样网格足够密：令 u(s)=unit(A+sB)，s∈[0,1]，g(s)=(u(s)·Â)²−s。对本轮的非平行 A、B，A+sB 不为零，g 连续，g(0)=1，g(1)<0，因此存在 s*∈(0,1) 使 g(s*)=0。此时 w(u(s*))=s*，A+wB 平行 u，故 F(u)=0。由于 w 是平方，−u 也有相同零点。这不是“u 同时平行两个参考向量”的情况；四个零点均在 P1 和 P2 之间的方向弧内。

采用任务所给的臂备用向量 (outward*.5,0,1)，腿备用向量 (outward*.2,−1,0)。独立 double 计算定位如下，顺序仍为右臂、左臂、右腿、左腿：

| limb | s* | u 的零点方向 |
| --- | --- | --- |
| 0 | .7418399948262953 | (−.4646951438214864, .6445862862577484, .6070971445140986) |
| 1 | .7418399948262953 | (.4646951438214864, .6445862862577484, .6070971445140986) |
| 2 | .6894495949689061 | (−.2680054068951972, −.5468533059039602, −.7931737285720257) |
| 3 | .6894495949689061 | (.2680054068951972, −.5468533059039602, −.7931737285720257) |

`PoleFieldTest` 用 1° 单位球网格、±P1、±P2、±(P1+P2)、±零点方向检查 `norm>1e-3`。测试中 80 步二分只用于定位数学反例，不是生产 IK 迭代。在 P1→P2 的归一化线性混合路径上检查 4000 个相邻步的轴夹角 `<.2`。实际输出：

```text
C2B_POLE limb=0 minNorm=2.220446049250313E-16
C2B_POLE limb=1 minNorm=2.220446049250313E-16
C2B_POLE limb=2 minNorm=0.0
C2B_POLE limb=3 minNorm=0.0
C2B_POLE limb=0 maxAxisStep=3.141592653589793
C2B_POLE limb=1 maxAxisStep=3.141592653589793
C2B_POLE limb=2 maxAxisStep=3.141592653589793
C2B_POLE limb=3 maxAxisStep=3.141592653589793
```

在零点指定一个 Cartesian 兜底值不能改变其两侧相反的铰链轴极限，因而无法补成连续场。也不能证明这些 u 不可达：上段方向无这种解剖限制，任意单位 u 可配任意正交单位 v 和允许屈曲 f，得到上段 l*u、下段 l*(cos(f)*u−sin(f)*v) 的合法两段构型。任务没有限制手目标使这些方向被排除。

测试还从 P1 一侧穿越到另一侧，用实际 `solveHinged` 欧拉输出重建矩阵检查旋转夹角；该局部检查通过：

```text
C2B_POLE limb=0 nearPrimaryRotationStep=1.0415166807691618E-4
C2B_POLE limb=1 nearPrimaryRotationStep=1.0415166807691618E-4
C2B_POLE limb=2 nearPrimaryRotationStep=1.0013580769936222E-4
C2B_POLE limb=3 nearPrimaryRotationStep=1.0013580769936222E-4
```

这说明新公式能移开 P1 处的原奇点，但会在参考向量之间引入另一处真实 π 翻转，不能从 P1 局部通过推出全局通过。新增测试的公式在测试类中实现，明确是候选设计契约实验，不是对已接入生产的新算法的测试。

### 两步求解的自洽与精度

第四个实验直接调用现有 `TwoBoneIk.solve`：pivot=(0,0,0)、target=(0,.4,0)、两段各 .375，使用左臂 A、B。先用 A 解 u0，计算 F(u0)，用 F(u0) 再解 u1；最后把满足任务上层要求的 F(u1) 传给 `solveHinged`，屈曲按精确目标距离计算，再用实际 `endPoint` 回代。目标距离 .4，位于 [.2255,.75] 内，屈曲约 115.5°，应可达。

```text
C2B_POLE twoPassFinalFieldReachableError=0.6532099302363219
```

断言仍为 `<2e-3`，失败。这是一个具体两步方案的反例，不是对所有闭式 IK 方案的不可能性证明。原因是第二次 IK 改变了 u；用于第二次肘位置的 F(u0) 与最终框架要求的 F(u1) 不同。直接复用 F(u0) 到最后框架也不能声称最终 pole 是指定的 F(u1)，且仍需处理禁止屈曲方向。没有通过增加迭代、翻转轴或将该误差标作不可达绕开要求。

### 真实轨迹与最终验证

指定命令沿用第一轮工作树内 Gradle 缓存，未尝试写用户 `~/.gradle`，未更改依赖/工具链：

```powershell
$env:JAVA_HOME='C:\Program Files\Zulu\zulu-25'
$env:VERSION='0.1.0-dev'
$env:GRADLE_USER_HOME="$PWD\build\c2b-gradle-home"
.\gradlew.bat spotlessApply cleanTest spotlessCheck checkstyleMain checkstyleTest test build --no-configuration-cache --console=plain
```

最终日志 `build/c2b-round2-validation.log`，基线日志 `build/c2b-round2-baseline.log`。最终逐字摘要：

```text
> Task :spotlessApply
> Task :cleanTest
> Task :spotlessCheck
230 tests completed, 4 failed
> Task :test FAILED
BUILD FAILED in 9s
30 actionable tasks: 7 executed, 23 up-to-date
```

JUnit XML 汇总 tests=230、failures=4、errors=0、skipped=0；Checkstyle main/test XML error 数均为0，Spotless通过。test 失败使 build 后续打包未完成，未出现 BUILD SUCCESSFUL。

原真实轨迹测试输出（生产未变，所以不是修复后的结果）：

```text
C2B_IK samples=168 limbs=672 maxReachableError=3.478711107352004E-7
C2B_IK limb=0 unreachable=0 maxShortfall=0.0
C2B_IK limb=1 unreachable=30 maxShortfall=0.70680594
C2B_IK limb=2 unreachable=38 maxShortfall=0.014982169
C2B_IK limb=3 unreachable=36 maxShortfall=0.09942578
C2B_CONTINUITY limb=0 maxRotationStep=0.8917120068190479 maxFlexStep=0.5337201356887817
C2B_CONTINUITY limb=1 maxRotationStep=2.8925768165036696 maxFlexStep=1.791512131690979
C2B_CONTINUITY limb=2 maxRotationStep=0.1931886571835067 maxFlexStep=0.4498625695705414
C2B_CONTINUITY limb=3 maxRotationStep=0.5785961170836167 maxFlexStep=0.8190250992774963
C2B_CONTINUITY worst iai limb=1 t=6.5 step=2.8925768165036696
```

原零分配测试两项通过；TwoBoneIk.solve、solveHinged、solveArm、solveLeg、endPoint 每百万次均为0 bytes，counter baseline 为912/0 bytes。**没有新的生产 pole 计算，也没有新增 pole 的零分配测量，不能声称完成该验收项。**

判断：现有左臂失败是固定 pole 框架翻转及随后的禁止方向夹紧，矩阵测试已排除仅欧拉切口的问题；不能归咎于轨迹不可达。候选方案失败则是 pole 设计本身的抵消奇点，加上具体两步求解与最终框架不自洽。没有合格修复可交付。本轮保留失败测试作为反例，未将候选接入生产。后续必须明确可覆盖的方向域，或允许依赖上一帧轴的延续策略，再设计自洽 IK；这些变更未获本轮新增授权，未执行。已停止。

## Lead 复核与第三轮(Lead 本人完成,2026-10-13)

**结论**:第二轮 Codex 的停止判断是对的:Lead 给出的"主弯向 `P1` + 备用弯向 `P2` 按权重混合"的公式在路径上有抵消零点(铰链轴跳变 π),两步法 FK 有反例(误差 0.65)。根本原因不是公式细节,而是**任何只由方向决定的固定弯向场在球面上必有奇点**(球面上不存在处处非零的连续切向量场)。因此换成了下面的设计,并删除了 Codex 为证明旧公式失败而写的 `PoleFieldTest`(3 项)。

**新设计(`LimbSolver.solveSegmented`)**:铰链轴取"从 `-singular` 到目标方向 t 的最短旋转所携带的身体侧向轴"`h = Swing(-singular → t) · (lateral·X)`,它垂直于 t,所以弯曲平面由 t 和 `h` 张成,关节偏移方向 `w = h × t`,上段 `(r/2·t + height·w)/l`、下段 `(r/2·t − height·w)/l`;闭式、无迭代、零分配。手臂下垂时肘向后、前臂向前;前平举时肘在手下方;腿的 `lateral = −1` 使膝朝前。**奇点只在 t 恰好沿 `singular` 方向**。
- `singular` 取在轨迹从不指向的方向:对 `katana.tracks.json` 全部样本的 `baked` 手臂方向做搜索(在偏离正上方 ≤45° 的范围内取使"到最近样本的夹角"最大的方向):右臂 64.8°、左臂 92.5°(正上方时分别为 19.8°、50.0°)。取值写在 `LimbSolver` 常量的注释里。腿的奇点轴取正上方(最近样本距其 135°)。**这些常量是对 katana 轨迹的数据驱动选择**;别的武器轨迹若让手臂指向这些方向,会在那里出现铰链翻转,届时需把 `singular` 变成内容参数。
- 解剖夹紧只剩屈曲量上限 145°(对应最小半径 0.2255);"禁止屈曲方向"不再会出现,已删除对应夹紧代码。

**测试**(`HingedLimbTest`、`SwingHingeTest`):
- 全部 168 个样本 × 4 肢体回代:可达误差最大 2.99e-7;手臂 0/336 不可达(之前 30 个"不可达"全是铰链翻转的假象,`handAtTheScabbardMouthIsReachableWithABentElbow` 固化);腿 38/36 个样本略微够不到(最大差 0.015、0.099 Biped 单位),腿伸直,属正确行为。
- 旋转连续性改为比较旋转矩阵夹角,并限定"除非**目标本身**跳变,否则步长 < 0.9 rad"。真实数据:右臂 0.81、右腿 0.19、左腿 0.58 rad;**唯一被豁免的是 `iai` t=11.5→12.0 的左臂:旋转 1.02 rad,而左手目标在这一步从 `free` 跳到 `grip` 移动了 0.32 Biped 单位**——轨迹生成器 `motion2.py` 在这个交接点没有平滑,是**轨迹数据的不连续**,不是解算问题。已记为美术轨迹待办(给 `motion2.py` 的 `free→grip` 加过渡)。
- `SwingHingeTest`:下垂/前平举/腿的弯向符合解剖;矢状面一圈扫描每 1° 输入的最大旋转步长 0.115 rad(出现在手臂指向上后方一侧,靠近奇点处放大约 6 倍),无翻转。

**仍未验证**:渲染(`ModelRenderer` 子部件的 FK 约定与 `endPoint` 一致,但没有在游戏里看过)。
