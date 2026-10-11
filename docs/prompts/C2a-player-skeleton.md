# C2a 玩家骨架 v1(执行端提示词)

前置:WF-1 已合并。分支 `prototype/c2a-skeleton`,独立 worktree。读 `docs/design/modules/assets.md`(骨架树、部位遮罩、Blender 烘焙约定)与 `art/katana/motion2.py` 的轨迹格式。

## 目标
战斗/持武器状态下接管玩家 `ModelBiped` 的双臂、双腿、躯干扭转,不引入弯曲关节;非战斗态交还原版。

## 要做
1. `track` 动画预设:读取烘焙轨迹(sword 位姿、`bladeDrawn`、骨骼单位方向 armR/armL/legR/legL、pelvis、torsoYaw、foot),按 tick 采样 + 四元数插值,partial tick 平滑。轨迹作为 Presentation 内容,由 Animation 注册表引用。
2. 双手握柄:右手近刀镡,左手近柄头;两视角统一刀姿(同时修好第一人称刃口反向,去掉第三人称单独的 90° Y roll)。
3. 交锋态待机构(engaged idle):动作开始与结束不跳变回原版走路姿态;左手扶鞘口。
4. 拔刀用 `stowed_empty`,收刀用带柄鞘;居合起手与收刀按"瞬抓"处理:动作第一帧直接换模型。
5. 第一人称自绘可见双手臂(含左手)。
6. 持武器时屏蔽原版摆臂覆盖挥刀手臂。
7. 检测到 Mo' Bends 等改玩家模型的模组时,战斗态让位,仅记录日志。

## 验收
- 构建与 JUnit 口径同 WF-1;双客户端连击 20/20、S08=0。
- 六个动作(iai、light_1/2/3、heavy、sheathe)两视角真实截图/录屏,对照 `docs/art/motion-v3/`。
- 交锋态下重测挂点:左手扶鞘口后,把 sheathX 往身体收(从 0.58 起每 0.04 一档),报告穿臂与右手可达性。
- 盔甲、细臂皮肤、蹲下、骑乘下是否穿帮,列为已知项。

## 纪律
不做运行时 IK;失败如实保留;未验证项单列;推送前等用户批准;输出 `docs/c2a/REPORT.md`。
