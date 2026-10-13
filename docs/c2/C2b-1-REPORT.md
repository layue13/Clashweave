# C2b-1:分段肢体(肘/膝弯曲)与盔甲退化规则

状态:**草稿,等待审核**。日期:2026-10-13。基线:`c2a/skeleton-1`(PR #20,含躯干错位修复 `5d93c47`),分支 `c2b/segmented-limbs`。

术语:**通过**＝该项预设判据在记录的运行中成立;**未验证**＝本轮没有运行;**观察**＝只记录,不下判据。

## 1 结论

- 战斗态下玩家的手臂与腿由单个方块改为**上段 + 下段**,肘/膝按闭式两段 IK 弯曲,手落在刀柄上、膝随骨盆下沉而屈。真实画面(侧 90°、斜后 140°)六个动作可见肘膝弯曲,皮肤贴图连续。
- 盔甲:原版两套盔甲模型换成同一个姿态模型,盔甲随战斗姿态摆动;**穿了胸甲的手臂、穿了护腿或靴子的腿退化为整根刚性**,与盔甲一致,避免皮肤弯、盔甲直的穿插。
- 顺带修复:**观察其他玩家时躯干与四肢偶发分离**(C2a-1 就存在,已在 PR #20 修复并移植)。
- 构建 `BUILD SUCCESSFUL`,JUnit 218 项,0 失败/错误/跳过。
- 局限与未验证见 §5、§6。

## 2 设计与实现

| 部分 | 内容 |
|---|---|
| IK(`client/pose/LimbSolver.solveSegmented`) | 闭式两段 IK,零分配、无迭代。铰链轴 = 从 `-singular` 到目标方向 t 的最短旋转所携带的身体侧向轴:它垂直于 t,弯曲平面由 t 与铰链轴张成,关节偏移 `w = h × t`。手臂下垂时肘向后、前臂向前;前平举时肘在手下方;腿的侧向符号取反使膝朝前。屈曲上限 145°。 |
| 奇点放置 | 任何只由方向决定的弯向场在球面上必有奇点,所以把奇点轴放在轨迹从不指向的方向:对 katana 轨迹全部样本搜索(偏离正上方 ≤45°),右臂奇点轴到最近样本 64.8°、左臂 92.5°(取正上方时分别只有 19.8°、50.0°);腿用正上方(最近 135°)。**这是对 katana 轨迹的数据驱动常量**,别的武器若让手臂指向这些方向会出现铰链翻转,届时需把它变成内容参数。 |
| 段长 | `ModelBiped` 手臂方块是肩枢轴下 −2…10 px,所以手臂到手端只有 **10 px**,两段各 5 px;腿 12 px,两段各 6 px。 |
| 模型(`client/CombatBiped`) | 皮肤模型(`expand 0`)把手臂/腿替换为上段 + 子部件(枢轴在肘/膝);64×32 皮肤按行切开:手臂上段 7 行(含肩部上沿)、前臂 5 行,腿各 6 行。盔甲模型(`1.0F`、`0.5F`)保持整块,按刚性方向摆。权重淡入淡出与 C2a 相同。 |
| 盔甲规则(`client/KatanaPlayerRenderer`) | 胸甲槽 → 双臂刚性;护腿或靴子槽 → 双腿刚性。 |
| 躯干错位修复 | 所有玩家共用一个模型实例;原版 `setRotationAngles` 从不重置头/头外层/躯干的枢轴、手臂枢轴 Y、腿枢轴 X 与腿的 Z 角(字节码核实),而战斗姿态恰好改这些。每次计算前先恢复构造默认值。`CombatBipedTest` 不带修复时失败、带修复时通过。 |
| 删除 Mo' Bends 让位 | 按用户决定不做 Mo' Bends 兼容(见 §6 的设计变更)。 |
| 清理 | 删除生产中无调用者的 `TwoBoneIk`、`PoseBlend`、`Pose.blend`、`LimbSolver.solveHinged` 及只测它们的测试;`Track`/`TrackData` 共用命名槽位常量;删去 `TrackData.finiteTree`(所有被读取的数值已由 `number()` 检查;未读取的说明字段不再判定)。 |
| 夹具 | `dual.py --armor leather|iron[-chest|-legs|-boots]`:服务端给 P0A 装备并打印 `C2B_ARMOR`;默认关闭。 |

### 2.1 过程记录(含失败)

- 纯逻辑第一、二轮由 Codex 实现;**两轮均如实报告未通过验收、未放宽测试**。失败根因是 Lead 的设计缺陷:第一轮用固定 pole(手臂下垂时与 pole 平行,`iai` 左臂铰链翻转 2.89 rad,并被误判成 0.707 的"够不到"),第二轮 Lead 给的"双 pole 混合"公式有抵消零点(路径上铰链轴跳变 π)。第三轮由 Lead 改为 §2 的设计。详细推导与数据见 [C2b-1-codex-logic-NOTES.md](C2b-1-codex-logic-NOTES.md)。
- 连续性判据最初比较欧拉三元组(在 2π 切口与近万向锁处会大幅变化而旋转不动),是 Lead 的提示词缺陷;已改为比较旋转矩阵夹角。

## 3 验证

### 3.1 单元测试
- 全部 168 个轨迹样本 × 4 肢体回代:可达时末端误差最大 2.99e-7 Biped 单位。
- 够不到(手臂伸直、腿伸直):右臂 25/168(最大差 0.058 Biped 单位,约 5 cm)、左臂 3/168(0.054)、右腿 38/168(0.015)、左腿 36/168(0.099)。手臂这部分源于轨迹按更长手臂设计握点距离(见 §5)。
- 旋转连续性:相邻 0.5 tick 的上段旋转步长 右臂 0.81、左臂 1.09、右腿 0.19、左腿 0.58 rad;规则是"除非目标本身跳变超过 0.25 方块,否则 < 0.9 rad",**唯一豁免**是 `iai` t=11.5→12.0 的左臂(左手目标从 `free` 切到 `grip` 时跳了 0.32 Biped 单位,是轨迹数据的不连续)。
- 解剖方向:下垂肘向后/前臂向前、前平举肘在手下、腿膝向前;矢状面整圈扫描每 1° 输入的最大旋转步长 0.115 rad(靠近奇点一侧),无翻转。
- 零分配:`Track.sample`、`solveLimb`、`swordMatrix`、`shoulderPoint`、`solveArm`、`solveLeg`、`endPoint` 各 100 万次 0 字节。

### 3.2 真实画面(`--pose-verify`,测试包 `tools/c2a/pose-pack` 去掉连段的命中要求)
- `c2b1-pose-90-1`、`c2b1-pose-140-1`(修复前 JAR)、`c2b2-pose-90-1`、`c2b2-pose-140-1`(最终 JAR):六个动作都可见肘膝弯曲,拳头在刀柄上,皮肤颜色连续。**观察**:肘外侧屈曲时露出一小块深色楔形(两段方块的内侧面),幅度小。
- 盔甲(`c2b1-armor-iron-1`、`-leather-chest-1`、`-iron-legs-1`,服务端日志均有 `C2B_ARMOR`):盔甲随姿态摆动;胸甲 → 手臂刚性、腿仍弯;护腿 → 腿刚性、手臂仍弯。`c2b2-armor-leather-chest-1` 的帧被贴近镜头的 P0B 挡住,不作证据。
- 观察他人:最终 JAR 的画面中 P0B 躯干与四肢完整。**修复前后的游戏内对比没有专门复现**,修复的证据是单元测试。

### 3.3 回归(部分,按新口径停止)
`--souls-view` 0/100 ms 各 3 轮,基线 = C2a-1 JAR(`f173ec2f…`),候选 = C2b-1 首个构建 JAR(`a146f07d…`,不含躯干错位修复与清理),交替运行,12 个会话全部 `complete:true`、服务端退出 0、客户端 GL 类加载 0:**两者全部连击 20/20、S08 0、包视角变化 0°/0°、位移纠正 0**;`REJECT:STALE` 两者量级相当(0–6)。
浮空回归与渲染探针:**未运行**——批次中途按用户同意的新口径停止(纯客户端渲染改动,性能 A/B 放到里程碑)。

## 4 复现
```powershell
$env:JAVA_HOME='C:\Program Files\Zulu\zulu-25'; $env:VERSION='0.1.0-dev'
.\gradlew.bat spotlessApply cleanTest spotlessCheck checkstyleMain checkstyleTest test build --no-configuration-cache --console=plain
.\gradlew.bat p0LaunchFiles p0ValidationJar -I tools/p0/validation.gradle --no-configuration-cache --console=plain
python tools/p0/dual.py --pose-verify 140 [--armor leather-chest] --content-pack tools/c2a/pose-pack --weapon-items clashweave:katana=posetest:katana --mod-snapshot build/<jar>-dev.jar --snapshot-coremod --label <新标签> --accept-eula
```

## 5 已知问题
1. 肘外侧的小缺口(见 §3.2)。
2. 轨迹的握点距离按 12 px 手臂设计(`motion2.py` 的 `GRIP_DIST` 0.55±0.09 方块,真实臂长约 0.586 方块),少数样本手臂伸直、手差几厘米——**美术待办**:按 10 px 手臂重算握点。
3. `iai` 左手 `free→grip` 交接点没有过渡,目标一步跳 0.32 Biped 单位——**美术待办**:`motion2.py` 加过渡。
4. 刚性模式(穿盔甲)用 `solveLimb` 从"朝下"做最短旋转,手臂接近正上举时方块滚转会突变(右臂最近 19.8°),只影响贴图朝向。
5. 奇点轴常量对 katana 轨迹有效,其他武器未验证(§2)。
6. Forge `getArmorModel` 返回自定义模型类的盔甲不跟随姿态;64×64 皮肤模组的左臂 UV 未支持。
7. 第一人称仍是 C2a 的单臂自绘(C2a-2 未做)。

## 6 设计变更(用户 2026-10-13 决定)
- **不做 Mo' Bends 兼容**,并计划**在不持武器时也由本系统接管玩家全身动画**(取代 Mo' Bends),后续扩展到其他模组与怪物。这推翻了 HANDOFF §3 与 `docs/design/modules/assets.md` 中"只在持武器/战斗态接管""检测到 Mo' Bends 则让位"的条目;设计文档的具体修改将随"全时身体动画"方案一起提交审核,本 PR 不改 `docs/design/`。
- **性能验证口径**:每个 PR 只保留零成本护栏(零分配测试、写法纪律);仅改到战斗/网络/服务端时加 1 轮快速回归;完整实机 A/B 与优化放到里程碑。
