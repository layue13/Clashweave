# P1 ground-fix:100 ms 下玩家浮空的偶发 `CONDITION_CHANGED`

状态:**草稿,等待审核;未推送**。日期:2026-10-13。基线:`origin/work` `99f0d46`(PR #18 合并后),分支 `p1/ground-fix`。平台:Windows 11,真实专用服务器与两个 Minecraft 1.7.10 客户端(`tools/p0/dual.py`),Gradle 使用 Zulu 25。

术语:**通过**＝该项预设判据在记录的运行中成立;**失败**＝判据不成立,原运行保留;**未验证**＝本轮没有运行;**观察**＝只记录,不下判据。会话标签对应 `build/p0/runs/<标签>`(忽略目录,不入库)。

## 1 结论

- **根因(有逐包证据)**:`ClientProxy.send()` 在按键时先发一个 `C05PacketPlayerLook`(发布按下时的视角),其 `onGround` 取自 `thePlayer.onGround`。按键处理发生在客户端 tick 开头,而上一 tick 结尾的根位移 `moveEntity(dx,0,dz)` 已把该标志清为 false(原版要求 dy<0 才置 true)。服务端采用包内值,紧随其后到达的 `requires:GROUND` 输入被消费时读到 false,返回 `CONDITION_CHANGED`。
- **修复**:`send()` 的 C05 改用 `physicalGround`——END 阶段在根位移之前采样的、原版为本 tick 算出的地面标志。只改 `ClientProxy.java`(新增 1 字段、1 行赋值、1 处引用)。不放大任何额度,不新增热路径分配。
- **结果**:100 ms 玩家浮空,基线 5 轮 90/100(10 次 `CONDITION_CHANGED`),修复 5 轮 100/100(0 次)。0 ms 修复 40/40。`--souls-view` 回归 12 个有效会话全部连击 20/20、S08=0、包视角变化 0°/0°、位移纠正 0。
- 局限见 §6。

## 2 证据链

1. **服务端窗口**:`gf-diag2-100-r1` 中 6 次 `CONDITION_CHANGED` 的消费 tick(545、649、1275、1400、1527、1901)全部落在服务端攻击者 `onGround=false` 的 1–2 tick 窗口内。
2. **服务端不自改标志**:`NetHandlerPlayServer.processPlayer` 字节码在 `moveEntity` 之后以包内值覆盖 `onGround`;`CombatServer` 无 `MOVE_CORRECT`;src/main 中除 `HitEffects` 对目标赋速度外无写 `onGround` 或 `moveEntity` 的服务端代码。
3. **窗口不在 END 状态**:客户端逐 tick 的 END 状态在这些时刻为 true(`gf-diag3-100-r1`);窗口来源有两类:夹具 `prepare()` 的 `setPlayerLocation` 触发的 S08,原版客户端回 `C06 onGround=false`(`gf-diag4-100-r1`:23 个 S08,38 个服务端窗口中 18 个在 S08 后约 100 ms 内;这类发生在案例开头,与 HEAVY 无关);其余为第 4 条。
4. **逐包确认**:`gf-diag5-100-r1` 在客户端出站管道记录每个 `C03PacketPlayer` 子类的 `onGround`:出现 26 个 `C05PacketPlayerLook onGround=false`,与服务端的案例中段窗口一一对应。该包只由 `ClientProxy.send()` 发出。

## 3 失败史(保留)

修法 v1:根位移时贴地则 `dy=-0.001`(补丁 `Projects/gf-v1-press.patch`)。0 ms 20/20,但 100 ms 三轮仅 5/1/3 被接受(同批基线 20/18/15),**更差,已否决**。机理(观察):压地碰撞使客户端 `motionY` 清零,下一 tick 原版自己的移动只下压 0.0784,未被判为落地,约每隔一个 tick 发一次 false(`gf-diag1-100-r1`:43 个 false/83 个 true)。v1 只处理了"根位移清标志"这个现象,没有触及真正的发送点,v1 的数据不作为修复证据。

## 4 玩家浮空 A/B(`--launcher player`,每会话 20 例)

“接受”＝该案例至少一次 `CW_EFFECT LAUNCH … accepted=true`。基线与修复交替运行,标签 `gf2-*`、`gf3-*`。

| 轮次 | 100 ms 基线 接受/`CC` | 100 ms 修复 接受/`CC` | 0 ms 基线 | 0 ms 修复 |
|---|---:|---:|---:|---:|
| r1 | 14 / 6 | **20** / 0 | 无效(启动失败,保留) | **20** / 0 |
| r2 | 18 / 2 | **20** / 0 | 20 / 0 | **20** / 0 |
| r3 | 19 / 1 | **20** / 0 | | |
| r4 | 19 / 1 | **20** / 0 | | |
| r5 | 20 / 0 | **20** / 0 | | |

`CC`＝`result=CONDITION_CHANGED` 次数。补跑的有效 0 ms 基线 `gf3-base-0-redo`:20/20。无效会话 `gf2-base-0-r1`:服务端提前退出、0 个结果,基础设施抖动,不计入。

## 5 连击回归(`--souls-view`,0/100 ms 各 3 轮,基线与修复交替)

12 个计划会话中 2 个启动失败(`gf3-sv-base-0-r1`、`gf3-sv-fix2-0-r2`,服务端退出码 1,无 P0B,保留),分别补跑 `gf3-sv-base-0-r1b`、`gf3-sv-fix2-0-r2b`。12 个有效会话**全部**:P0A 连击 20/20,区间 S08 0/0,包引起视角变化 yaw/pitch 0°/0°,位移纠正 0,服务端退出 0,客户端类加载检查 0。输入门拒绝(`REJECT:STALE` 为主,100 ms 基线 r3 含 `REJECT:RATE` 14)在基线与修复中量级相当。

服务端战斗逻辑耗时(观察,不下判据):均值 0.09–0.20 ms;最大值基线 3.3–5.4 ms,修复 4.2–9.3 ms,另有一次 `gf3-sv-fix2-0-r2b` 最大 57.6 ms。本改动不含服务端代码,最大值会话间波动大(交接文档记录基线也出现过 177 ms 离群),不据此作结论。

## 6 限制与未验证

- 每种条件仅 5 轮(100 ms)/2 轮(0 ms)浮空与 3 轮连击;基线本身在不同会话间波动(14–20/20),修复的 100/100 是该样本下的结果,不是概率保证。
- 夹具 `prepare()` 的 S08 窗口(案例开头)仍存在,与 HEAVY 输入不重叠,本修复不处理;真实游戏中的服务端传送(HANDOFF §5 遗留项)后的一 tick false 属同类,**未处理**。
- 仅 100 ms 代理(各半程 50 ms);更高延迟、丢包、低 TPS、整合服务器路径未验证。
- 渲染 CPU 与客户端热路径:本改动在 `send()` 与 END 各增一次布尔赋值,未单独测量渲染时间。
- 诊断构建(`diag0–5`)含临时追踪,不在提交中。

## 7 复现

```powershell
$env:JAVA_HOME='C:\Program Files\Zulu\zulu-25'; $env:VERSION='0.1.0-dev'
.\gradlew.bat spotlessApply cleanTest spotlessCheck checkstyleMain checkstyleTest test build --no-configuration-cache --console=plain
.\gradlew.bat p0LaunchFiles -I tools/p0/validation.gradle --no-configuration-cache --console=plain
python tools/p0/dual.py --launcher player --rtt 100 --mod-snapshot build/<jar>-dev.jar --snapshot-coremod --label <新标签> --accept-eula
python tools/p0/dual.py --souls-view [--rtt 100] --mod-snapshot build/<jar>-dev.jar --snapshot-coremod --label <新标签> --accept-eula
```

`--mod-snapshot` 需传 `clashweave-0.1.0-dev-dev.jar`(非 reobf 版),否则服务端因 SRG 名不符启动失败(`gf-diag-100-1` 即此例)。构建结果:`BUILD SUCCESSFUL`,JUnit 189 项,0 失败、0 错误、0 跳过。

| JAR | SHA-256 |
|---|---|
| 基线 `base-dev.jar`(`743ae6f` 构建) | `6f63e327f25b0a5e18cf89caed3505e9bfdda4f2aec47995312e6695df7f7e1d` |
| 修复 `fix2-dev.jar` | `79c1b112ed1f2dbb4a3841df271e35c3e4ffb66a236f149b2ecd11f1b65b5780` |

注:基线 JAR 为 PR #17 合并提交 `743ae6f`,不含 PR #18;修复 JAR 基于 PR #18 合并后的 `99f0d46`。二者差异包含 WF-1 全部内容,与 WF-1c 报告的基线口径一致。
