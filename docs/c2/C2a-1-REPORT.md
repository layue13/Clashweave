# C2a-1:烘焙轨迹驱动的第三人称身体姿态(刚性手臂 v1)

状态:**草稿,等待审核**。日期:2026-10-13。基线:`origin/work` `976175a`(PR #19 合并后),分支 `c2a/skeleton-1`。

术语:**通过**＝该项预设判据在记录的运行中成立;**未验证**＝本轮没有运行;**观察**＝只记录,不下判据。

## 1 结论

- 纯逻辑层、内容 schema、渲染接线已完成并通过构建与 JUnit(214 项,新增 25 项,0 失败/错误/跳过;唯一改动的既有断言见 §3)。
- 第三人称接管在真实游戏中生效:交锋态与全部 6 个动作(`iai`、`light_1/2/3`、`heavy`、`sheathe`)在侧 90°、斜后 140° 两个视角都有真实画面(`c2a1-pose-90-7`、`c2a1-pose-140-7`,相机实体已隐身)。躯干扭转、双臂与刀按轨迹摆动,序列合理:`heavy` 举刀过头再下劈,`sheathe` 举刀、甩落、刀尖回鞘口,轻击与 `iai` 的刀指向前方,刀鞘在髋部。
- **视觉评价:肢体僵硬,不满足"柔软"目标。** 手臂/腿是 `ModelBiped` 的单个方块,无肘无膝,侧视时手臂是一根直棍。这是刚性 v1 的已知范围(HANDOFF §3、§4),由 C2b 处理,本 PR 不宣称解决。
- **更正**:本报告早先版本写过"持刀时双臂在胸前交成 X"。那来自拍摄夹具的相机实体当时可见、与被测玩家重叠(第二个未摆姿势的身体),不是被测姿态;夹具已修(`setInvisible`),该描述作废。
- **未完成/未验证项**见 §5。本 PR 不是 C2a 的完成,只是它的第一半。

## 2 实现

| 部分 | 内容 |
|---|---|
| 轨迹数据 | `art/katana/motion2.py` 生成 `assets/clashweave/tracks/katana.tracks.json`(版本 2,6 个动作,每 0.5 tick 一个样本)。放在 `tracks/` 而不是 `content/<ns>/` 下,避免 `ContentLoader` 把它当内容文件解析。**未纳入 Presentation 清单哈希**(哈希只覆盖内容包文件);Presentation 允许差异,后果仅是两端轨迹不同时外观不同。 |
| 内容 schema | `Animation` 增加 `source`(`domain:path`)、`stance`(本地动作 ID,其首帧作交锋态);`preset:"track"` 缺 `source` 在加载时拒绝。katana 武器改用 `katana:track`(`rigid_arc` 保留作回退)。 |
| 纯逻辑 `client/pose/` | Codex 实现,我审查并独立复核:`TrackData` 解析与校验、`Track` 零分配采样、`LimbSolver`(Biped 坐标映射、最小扭转欧拉分解、肩/髋点、刀矩阵)、`Pose`、`PoseBlend`。契约与推导见 [C2a-1-codex-logic-NOTES.md](C2a-1-codex-logic-NOTES.md)。 |
| 渲染 | `CombatBiped`(在原版角度上叠加姿态,3 tick 淡入淡出)、`PoseDriver`(每玩家每帧决定权重与姿态)、`KatanaPlayerRenderer` 换用 `CombatBiped`、`WeaponRendering` 在姿态生效时按轨迹刀姿直接绘制(不再挂在手臂上)。 |
| 兼容 | 检测到 Mo' Bends(`mobends`)时战斗态让位并记日志。**该分支未在真实环境测试**(没有安装该模组)。 |
| 夹具 | `PoseVerifyReplay`(`dual.py --pose-verify <相机偏角>`):纯观察者,按动作 6 个时间点拍摄。 |

## 3 验证

- 构建:`.\gradlew.bat spotlessCheck cleanTest checkstyleMain checkstyleTest test build`,`BUILD SUCCESSFUL`;JUnit XML 统计 214 项,失败 0、错误 0、跳过 0。Codex 在其沙箱内另有一次构建,数字一致;以上数字来自我在本机的独立复跑。
- 既有断言改动 1 处:`CombatWeaponsTest.weaponDefinitionsCarryTheContentIdsOnTheWire` 把武器外观动画从 `katana:rigid_arc` 改为 `katana:track`,因为武器数据有意切换,不是回归。
- 零分配:`PoseAllocationTest` 对 `Track.sample`、`LimbSolver.solveLimb/swordMatrix/shoulderPoint`、`Pose.blend`、`PoseBlend.update` 各 100 万次,测得 0 字节(计数器基线 512 字节/百万次,属测量开销;Codex 报告记录首轮曾测到 392 字节并解释为测量循环首次运行开销,未定位 JVM 内部来源)。渲染路径的 `CombatBiped`/`PoseDriver` **没有**单独的分配测试。
- 真实画面:`c2a1-art-1`(静态)、`c2a1-pose-side-1`、`c2a1-pose-140-3`、`c2a1-pose-90-3`。失败过的运行保留:`c2a1-pose-90-2`、`c2a1-pose-140-2`(拍摄夹具过早抢占相机,0 帧)。

### 3.1 回归与性能批次(Codex 执行,Lead 复核;详见 [C2a-1-VERIFY-NOTES.md](C2a-1-VERIFY-NOTES.md))

基线 `base-work-dev.jar`(`origin/work` 976175a,SHA-256 `79c1b112…`)与候选 `cand-c2a1-dev.jar`(C2a-1,SHA-256 `f173ec2f…ce65…`)交替运行,共 28 个会话,全部 `complete:true`、服务端退出 0、客户端 GL 类加载 0,无启动失败。

| 组 | 内容 | 基线 | 候选 |
|---|---|---|---|
| A | `--souls-view` 0/100 ms 各 3 轮(连击 20/20、S08、包视角变化、位移纠正) | 6 个会话全部 20/20;S08 0;yaw/pitch 0°/0°;纠正 0 | 同左,6 个会话全部通过 |
| B | `--launcher player` 100 ms 3 轮 + 0 ms 2 轮(每轮 20 例) | 5 轮全部 20/20,`CONDITION_CHANGED` 0 | 5 轮全部 20/20,`CONDITION_CHANGED` 0 |
| C | `--souls-view --render-probe` 0 ms 各 3 轮(两客户端合并,按样本数加权的 CPU 提交时间,µs) | `frame/combo` 677.6;`frame/drawn-idle` 590.6;`frame/sheathed-idle` 417.4 | 675.3;571.5;420.2 |

- 渲染对照(观察,不下判据):`frame/combo`、`drawn-idle`、`sheathed-idle`、`specials/*`、`waist` 均无退化;`frame/draw` 候选均值 689.2 对 635.0 µs,样本仅 72 对 72。`frame/no-state`(状态到达前的帧)候选均值 3074 对 1177 µs,样本 72 对 291,p95 31.2 对 13.8 ms,属起动阶段的少量离群帧,**原因未查明**,不据此下结论。
- 说明:Codex 初版报告把候选会话标为"哈希不符、无效",根因是 Lead 在提示词里抄错了一个哈希字符;JAR 本身与 Lead 记录一致,报告顶部已加更正。
- 仍未做:挂点 X 回测、与 `docs/art/motion-v3/` 的逐帧对照、第一人称(C2a-2)、Mo' Bends/盔甲/多人同屏。

## 4 已知问题(观察到的)

1. 肢体僵硬(见 §1),C2b。
1a. 近距离拍摄时 P0B 常挡住画面,`light_2/3`、`iai` 每动作只有 2–6 张清晰帧;两个视角、每动作最多 6 个时间点,不是逐帧覆盖。
2. 刀身在许多帧中近乎侧面朝向镜头而显得很细;可能与轴向有关,未查明。
3. 手与刀柄有残差:轨迹自身已记录 light_2、iai 末段手够刀柄的偏差约 0.29–0.30 m,画面上未逐帧量化。
4. 刀鞘挂点没有随骨盆偏移的 X 分量(只跟 Y、Z)。
5. 盔甲/细臂皮肤/蹲下/骑乘:盔甲层用独立模型实例,不跟随姿态(设计:C2b 复制主模型角度);其余未处理。

## 5 未验证/未完成

- 画面验证的口径:6 个动作各有两个视角的真实帧,但每动作只有数张;**没有**与 `docs/art/motion-v3/` 逐帧对照,也没有量化手到刀柄的残差。拍摄用测试包 `tools/c2a/pose-pack`(katana 动作图去掉 `hit` 要求,仅测试用),因为连段边要求命中目标。早先几轮拍摄(`c2a1-pose-*-2/-3/-4`)因场景选错、夹具抢占相机,只拍到 `iai`/`light_1` 或 0 帧,保留不删。
- 第一人称:本 PR 不改(C2a-2)。
- 挂点 X 回测:**未运行**。(连击回归、浮空回归、渲染 CPU 对比已在 §3.1 补齐。)
- Mo' Bends 让位分支、盔甲、多人同屏:未验证。

## 6 复现

```powershell
$env:JAVA_HOME='C:\Program Files\Zulu\zulu-25'; $env:VERSION='0.1.0-dev'
.\gradlew.bat spotlessApply cleanTest spotlessCheck checkstyleMain checkstyleTest test build --no-configuration-cache --console=plain
.\gradlew.bat p0LaunchFiles p0ValidationJar -I tools/p0/validation.gradle --no-configuration-cache --console=plain
python tools/p0/dual.py --pose-verify 140 --mod-snapshot build/<jar>-dev.jar --snapshot-coremod --label <新标签> --accept-eula
python art/katana/motion2.py src/main/resources/assets/clashweave/combat/katana.json src/main/resources/assets/clashweave/tracks/katana.tracks.json
```
