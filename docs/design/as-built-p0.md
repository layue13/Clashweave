# P0 已实现状态(as-built)

> 本文记录**代码里实际生效的**机制与默认值,取自 `CombatConfig.java`(提交 `ce32fce` 之后的 `work`)。它不改变 `docs/design/modules/` 的设计规则;与设计冲突处以设计为准并应回头讨论。数值的验证数据见 `docs/p0/` 的各报告。

## 1. 第三人称锁定相机(SOULS)

- 范围约束(硬):俯角 `cameraPitchMin 8°`–`cameraPitchMax 35°`;小目标(高度 ≤ `smallTargetHeight 0.8`)上限放宽到 `smallTargetPitchMax 45°`,瞄准点取目标高度的 `smallTargetAimFraction 0.85`;大目标阈值 `largeTargetHeight 1.5`。轨道距离 `cameraDistanceMin 3.5`–`cameraDistanceMax 5.0`,默认 `4.3`。
- 构图(软锚点):目标在屏幕纵向 `targetScreenY 0.45`,玩家 `playerScreenY 0.68`;枢轴高于眼 `cameraHeightOffset 0.6`,基线俯角 `15°`。
- 平滑:临界阻尼 `lockStrongSmoothTime 0.15 s`;目标位置滤波 `lockTargetFilterTau 0.1 s`。
- 前馈:玩家移动的前馈速度上限 `180`,位移 > `cameraFeedForwardTeleportDistance 3` 视为传送(跳变判定见 `CameraBearing.lead`:位移 > 3 且 (位移 ≥ 5 或 位移 − |v|·原始秒数 > 3),单侧);停滞 `0.1 s` 后以 `0.3 s` 恢复。
- 碰撞:射线半径 `0.12`、间隙 `0.3`、前瞻 `0.6`、最大回缩速度 `1.2`;仅在碰撞时隐藏玩家模型(距离 `1.0`,迟滞 `0.2`)。
- FOV 从投影矩阵读取,不用配置。
- 目标切换:翻转窗口 `150 ms`、阈值 `25°`、服务端冷却 `400 ms`。

## 2. 锁定选择与辅助

- 获取范围 `16`,保持范围 `20`(强制大于获取范围)。
- **选择锥水平/垂直拆分**:水平半角 `lockConeHalfAngle 30°`,垂直半角 `lockConeVerticalHalfAngle 60°`,另外要求目标与玩家的高度差不超过 `range/2`,避免楼上楼下被误选。
- 弱跟随(未强锁时):宽限 `250 ms`,最大角速度 `180°/s`,死区 `8°`;遮挡丢失宽限 `1000 ms`。
- 强跟随:最大 `720°/s`,水平偏移上限 `30°`、衰减 `120°/s`,俯仰范围 `-45°`~`60°`。
- 提交辅助(LockAssist):锥 `45°`,最大修正 `15°`;只在服务端起手提交时生效。

## 3. 连段窗口延迟补偿

后续连段输入在窗口边缘被 tick 量化时,可向前补偿一定 tick。补偿量 = `min(收包龄 receiptAge, inputEdgeCompensationTicks 4, ceil(ping/100) + inputEdgeCompensationJitterTicks 1)`,**ping 未知时补偿为 0**(`LatencyCompensation.limit`)。ping 来自 `EntityPlayerMP.ping`。窗口仍由服务端按 tick 判定,补偿只改变"输入被视为到达的 tick",不放宽窗口本身。输入戳龄上限 `stampAgeLimit 8`,未来戳容忍 `2`。实测见 `docs/p0/COMBO-*.md`。

## 4. 保持不变的 P0 机制

数据驱动动作表(`katana.json`:iai、light_1..3、heavy、sheathe)、服务端 Scheduler、InputGate、玩家目标延后提交(`confirmationDelay 3`)、持久 `maxHurtResistantTime 8`、FacingHistory(角度容差 `5°`、历史 `500 ms`)、MovementBudget(外部纵向/横向额度,`impulseMultiplier 4`)。

## 5. 美术方向与比例(摘要)

基础美术语言为"低多边形 + 手绘色块"(方向 A);刀的全部尺寸由玩家可见身高 `1.875` 推出,默认刀长 `0.786×身高`(1.47)。细节、图集分区与挂点结论见 `docs/art/katana-a.md`;该文档在 PR #15 中。

## 6. 尚未实现(设计已有)

内容包装载与注册表(`extension.md` §15–17)、玩家骨架接管与移动层(`assets.md`)、切上/浮空/控制预算(P1-a)、空中追击(P1-b)、SA 与爆发。路线见维护者的阶段计划。
