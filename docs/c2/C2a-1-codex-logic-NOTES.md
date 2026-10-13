# C2a-1 纯逻辑姿态层报告

## 范围与结果

完成 `client/pose` 的五个类与五个 JUnit 测试类。完整阅读并核对已有的 `Pose`、`Track`、`PoseBlend`，补齐 `TrackData` 与 `LimbSolver`。只修改指定的姿态层新文件、新测试和本报告；没有运行提交、推送、切换或创建分支操作。

开始时已有 `ContentPack.java`、`ContentRegistry.java` 修改和未跟踪的轨迹资源。保留这些既有工作。Spotless 前后比较姿态包之外所有主代码及测试 Java 文件的 SHA-256，未发生变化。未改 `WeaponRendering`、`PresentationAssets` 或内容 schema。

已阅读 `docs/design/modules/assets.md`、`art/katana/motion2.py` 的生成与输出结构、`motion.py` 的四元数定义、真实轨迹、`JsonCompat` 和既有分配测试。实现只使用 Java 标准库及 Gson；姿态包没有 Minecraft、LWJGL、Forge/FML import。运行时使用预烘焙方向，不做 IK。

## 每个公开方法的契约

公共输出数组由调用方预分配，必须具有方法名称要求的长度。正常热路径不创建对象、迭代器或装箱值。解析、构造和非法输入的异常路径允许分配。

| 方法 | 契约 |
| --- | --- |
| `Pose()` | 创建独立数组；四元数默认 `[0,0,0,1]`，四肢默认身体系向下 `[0,0,-1]`。位置与偏移为零，两个布尔量为 false。 |
| `Pose.copyFrom(source)` | 复制全部数组、角度和布尔量，支持自复制，不共享数组。 |
| `Pose.blend(a,b,w,out)` | 有限权重夹至 `[0,1]`；端点精确复制。位置、骨盆、脚点与 torsoYaw 线性混合；方向线性混合后归一。方向反向相消时选较近端点，正中选 b。四元数最短弧 slerp，接近重合时 nlerp 并归一。布尔量 `w>=0.5` 选 b。out 可与任一输入相同。调用方提供有限数据、单位方向及单位四元数。 |
| `Track.duration()` | 最后一个样本的 tick，float。 |
| `Track.sample(ticks,out)` | 二分定位预装 float 数组；超界夹紧，含正负 Infinity；NaN 抛异常。连续量按 Pose 的规则插值；两个布尔量保持前一个样本，直到时间真正到达下一个样本。样本时刻直接复制连续数组，避免端点再归一误差。 |
| `TrackData.parse(reader)` | 使用 Gson 2.2.4 的 `new JsonParser().parse(reader)`；不关闭调用方 Reader。必须 version 精确为 2、actions 非空、每动作至少两个样本；起始 t 精确为零，装入 float 后仍严格递增。拒绝缺字段、类型/长度错误、非有限数值及 float 溢出；检查未消费的数值字段。四元数范数容差 `1e-3`，方向容差 `1e-2`，加载时归一。mode 仅 grip/free，bladeDrawn 必须 boolean。失败抛带字段路径的 IllegalArgumentException；语法/读取失败使用 `$` 路径。允许附加字段；不执行内容 schema 校验。 |
| `TrackData.track(localActionId)` | 返回已解析的 Track；未知或 null 返回 null，不补全命名空间。调用者在状态到达时查找一次。 |
| `TrackData.has(localActionId)` | 是否存在该本地动作 ID。 |
| `TrackData.actionIds()` | 按输入顺序提供不可修改的动作 ID 集合。 |
| `LimbSolver.bodyToBipedPoint(x,y,z,out3)` | `(-x/S,1.5-z/S,-y/S)`，S=0.9375；输出单位 px/16。 |
| `LimbSolver.bodyToBipedDir(x,y,z,out3)` | `(-x,-z,-y)`，只有旋转、不平移、不缩放；保长度和右手性。 |
| `LimbSolver.solveLimb(dx,dy,dz,outXYZ)` | 有限非零身体系方向；内部归一并换基。从 Biped +Y 求 swing-only 最短旋转，再输出 `Rz·Ry·Rx` 的 X/Y/Z 弧度。静止为零旋转；精确反向因最短轴不唯一，固定选 Biped +X 的 π 旋转。万向锁附近固定 X=0，并解相应 Z。 |
| `LimbSolver.shoulderPoint(right,yaw,pelvis,out3)` | 输出 rotationPoint 的 px。以精确 ±5、2、0 静止肩点作校准，绕身体系上轴扭转后加骨盆偏移。方向已在根身体系，调用方不得再向肢体重复叠加 torsoYaw。 |
| `LimbSolver.hipPoint(right,pelvis,out3)` | 输出 px；右/左腿基准 `(-1.9,12,0)` / `(1.9,12,0)`，仅加骨盆位移，不叠加躯干旋转。 |
| `LimbSolver.torsoAngleY(yaw)` | 返回 `-radians(yaw)`，供 Biped 躯干 Y 旋转。 |
| `LimbSolver.swordMatrix(pose,out16)` | 单位四元数 xyzw；列主序 4×4，把握点为原点的刀局部方块坐标转换为 Biped px/16。线性部分是换基×四元数矩阵÷S，平移是 bodyToBipedPoint(swordP)，最后行为 `[0,0,0,1]`。不再叠加躯干旋转。 |
| `PoseBlend()` | 构造时预分配一份起始姿态，初始 inactive。 |
| `PoseBlend.start(from,ticks)` | 复制起点并将已过时间置零；ticks 非负，零表示立即结束。可重新开始并覆盖旧过渡。 |
| `PoseBlend.update(target,advance,out)` | advance 是本次增量 tick，有限且非负。先累计时间，再以 `u²(3-2u)` 混合保存起点与当前 live target；advance=0 得到起点，达到时长直接精确 target，结束后持续复制 target。target 可与 out 相同。 |
| `PoseBlend.active()` | 已过时间小于时长。 |
| `PoseBlend.reset()` | 清除过渡并回到 inactive；下一次 update 直接复制 target。 |

## 坐标推导、覆盖与偏差

换基矩阵 A 的三行是 `[-1,0,0]`、`[0,0,-1]`、`[0,-1,0]`，det(A)=+1。身体系 +z 被映为 Biped -Y，所以 `A·Rz(θ)·A⁻¹=Ry(-θ)`。测试不是只检查返回值符号：把一个身体系点先旋转 +30° 再映射，与映射后绕 Biped Y 旋转的结果比较。点/方向往返、身高 1.875 的顶点、肩点舍入也由测试覆盖。

精确反向的最短旋转轴本来不唯一；选 +X 是明确的稳定约定。在非反向处使用 rest×direction 的轴。测试用独立的 Rodrigues 轴角矩阵对比完整欧拉回代矩阵，误差阈值 `<1e-4`，同时校验 +Y 回代方向。覆盖 168 个真实样本的 672 个肢体方向，以及六轴、近静止、精确/近反向、两种符号的近万向锁输入。刀矩阵使用独立的 `q*v*q⁻¹` 双叉积手算参考，检查 `(0,0.3,0)`、`(0.1,0,0)`、原点及法线方向上的点。

肩点存在规格内部的舍入差异：直接用 `(0.293,0,1.289)` 换算得到约 `(-5.0005333,2.0010667,0)` px，与要求的精确 `(-5,2,0)` 不完全相同。本实现采用对应精确身体系点 `(0.29296875,0,1.2890625)` 的 Biped 锚点；这使静止值完全符合验收值，扭转半径与烘焙舍入肩点相差 0.00003125 方块。此处属于有意校准，非隐瞒误差。腿锚点按明确要求使用 ModelBiped 的 1.9 px，不重算生成器基于 0.117 方块髋点烘焙的腿方向。

Gson 2.2.4 JsonParser 本身宽松；本任务没有另建严格 JSON 语法解析器或禁止所有未知字段。所有要求的字段、数值、时间、mode、范数均进行语义校验；也拒绝 NaN/Infinity 字面量。没有调用 parseString、JsonObject.keySet/size、deepCopy 或新的 JsonArray 修改 API。

## 验证命令与实际输出

原样执行要求的命令：

```powershell
$env:JAVA_HOME='C:\Program Files\Zulu\zulu-25'; $env:VERSION='0.1.0-dev'
.\gradlew.bat spotlessApply cleanTest spotlessCheck checkstyleMain checkstyleTest test build --no-configuration-cache --console=plain
```

第一次在 Gradle wrapper 启动前失败，逐字关键输出：

```text
Exception in thread "main" java.io.FileNotFoundException: C:\Users\layue13\.gradle\wrapper\dists\gradle-9.7.1-bin\1w1c7tv4s851m17nbqdsro2tv\gradle-9.7.1-bin.zip.lck (Access is denied)
```

沙箱不允许写用户目录，且本会话不允许权限升级。将所需 Gradle 分发及依赖缓存复制到工作树内 `build/c2a-gradle-home`，额外设置以下环境变量，再执行完全相同的任务命令；未修改 gradle 脚本或依赖版本：

```powershell
$env:GRADLE_USER_HOME="$PWD\build\c2a-gradle-home"
```

最初复制因 Windows 长路径失败，使用 Python 的扩展长度绝对路径重试成功。本地缓存首轮运行又等待 Java 17 工具链下载；停止仅本次构建的进程、复制已有 Java 17 缓存后重跑成功。这些都不是通过代码放宽检查。

首轮完整构建的逐字输出摘要：

```text
> Task :spotlessApply
> Task :spotlessCheck UP-TO-DATE
> Task :checkstyleTest
> Task :checkstyleMain
> Task :test
> Task :build
BUILD SUCCESSFUL in 52s
39 actionable tasks: 28 executed, 11 up-to-date
```

JUnit XML 实际统计：总数 214，其中本任务新增 25，其他既有测试 189；失败 0、错误 0、跳过 0，共 33 个测试类。新增的 TrackDataTest、TrackSamplingTest、LimbSolverTest、PoseBlendTest 各 6 项，PoseAllocationTest 1 项。统计依据是 `build/test-results/test/TEST-*.xml`，不是仅统计源码注解。

另外直接使用已安装的 Zulu Java 8 javac/java、缓存 Gson 2.2.4、JUnit 4.13.2、Hamcrest 1.3 编译姿态包和测试，运行五个测试类，实际输出 `OK (25 tests)`。这直接验证纯逻辑包可脱离 Minecraft 编译运行。构建产物的五个姿态类 classfile major version 均为 52。

首轮分配测量 Track.sample 为 392 字节/百万次，其余五条路径为 0；均在既有 64 KiB bookkeeping 容差内。补充分配计数器预热后，首个测量仍出现 392。进一步增加可观察的无分配控制循环，独立重跑时控制循环测得 512、六个实际热路径均测得 0。该实验支持首轮固定小开销来自测量循环首次运行的推断，但未定位 JVM 内部具体分配来源；没有扣减计数器结果或更改容差。

补充测量控制循环后，再次执行完整命令（仍使用上述工作树内 GRADLE_USER_HOME），逐字关键输出：

```text
> Task :spotlessApply
> Task :cleanTest
> Task :spotlessCheck UP-TO-DATE
> Task :checkstyleMain UP-TO-DATE
> Task :compileTestJava
> Task :checkstyleTest
> Task :test
> Task :build
BUILD SUCCESSFUL in 7s
33 actionable tasks: 6 executed, 27 up-to-date
```

这次 `cleanTest` 后确实重新执行 test，没有将旧测试 XML 当成本次结果。最终 XML 重统计仍是 214 项，新增 25 项，既有 189 项；失败、错误、跳过均为 0。最终主代码与测试 Checkstyle XML 的 error 元素数均为 0。最终再比较包外 Java 文件哈希，变化列表为空。

最终分配测试的 system-out 逐字结果如下；每项 20,000 次热路径预热后测量 1,000,000 次，计数器另外预热，控制循环先运行一次。所有对象、输出数组及 Runnable 都在测量窗口外创建；volatile sink 保留计算结果，避免全部被消除。PoseBlend 在长过渡中持续 active，确实执行混合路径。

```text
C2A_ALLOC counter baseline bytesPerMillionCalls=512
C2A_ALLOC Track.sample bytesPerMillionCalls=0
C2A_ALLOC LimbSolver.solveLimb bytesPerMillionCalls=0
C2A_ALLOC LimbSolver.swordMatrix bytesPerMillionCalls=0
C2A_ALLOC LimbSolver.shoulderPoint bytesPerMillionCalls=0
C2A_ALLOC Pose.blend bytesPerMillionCalls=0
C2A_ALLOC PoseBlend.update bytesPerMillionCalls=0
C2A_SWING samples=168 limbs=672
```

两次成功构建都仍输出既有 Gradle 废弃功能、Eclipse JDT 版本建议等警告；没有更新 Gradle、插件、JDT 或构建脚本。缓存复制、构建输出与独立测试 class 文件都位于忽略的 build 目录，不作为交付源码。

## 未验证的范围

没有运行 Minecraft 客户端、真实 OpenGL glMultMatrix 或实际 ModelBiped 渲染；本任务只交付数学与数据逻辑。符号、单位、旋转顺序通过独立数学测试固化，仍需 Lead 在渲染接线时做游戏内验证。异常/解析/构造路径不承诺零分配；零分配测量限定在当前 Java 8 JVM、正常数据和上述预热条件。最终保留原有分配测试容差，不声称在所有 JVM 上绝对零字节。

完成后停止；渲染接线由 Lead 处理。
