# WF-1 内容引擎(执行端提示词)

前置:P1-a 已合并到 `prototype/p0-vertical`。在独立 git worktree 工作,分支 `prototype/wf1-content`。先读 `docs/design/modules/extension.md` §15–17、`docs/design/modules/assets.md`、`/root/.claude/plans/deep-growing-firefly.md` 的阶段 B(若无此文件,以本提示词为准)。

## 目标
把"单把刀写死"改成"按内容包装载",katana 变成第一个内容包,行为与视觉与现在一致。核心代码不得出现 katana。

## 要做
1. 内容模型 v1(带 `schemaVersion`):Weapon(物品、风格、倍率、外观 ID)、Style(动作图、移动、防御)、Action(时序、命中形状、效果引用)、Animation、Resource(模型 `held/stowed/stowed_empty`、握点、双手握位、挂点槽位)。ID 命名空间化(`katana:light_1`),旧 ID 做兼容映射。
2. 注册表与加载器:扫描 `assets/<ns>/…` 与配置目录;**加载期校验,错误信息指向文件与字段**;物品按定义在 preinit 注册(1.7.10 要求)。
3. 替换 `StaticKatanaResolver`、`CombatWeapons.DEFAULT`、单一 `config.actions`;动作目录按风格映射并同步客户端。
4. 渲染解耦:`KatanaRendering` → 通用 `WeaponRendering`;握点/挂点/握位读数据;拔刀用 `stowed_empty`,收刀用带柄鞘;动画预设按 ID 分派(`rigid_arc` 保留)。katana 挂点用 X=0.58、Y=0.55、Z=0.18、Angle=45°。
5. katana 迁移为内容包。
6. Gameplay 清单哈希:连接握手比对,不一致拒绝或降级;Presentation 允许差异。
7. 生命周期验收矩阵:死亡、重生、换维度、断线、换刀、扔刀、GUI、骑乘、传送,逐项自动化或截图。

## 验收(不得省)
- `spotlessCheck checkstyleMain checkstyleTest test` 通过,JUnit 实际执行数写进报告。
- 双客户端 `dual.py --souls-view` 0/100 ms 连击 20/20、S08=0、包引起视角变化=0,与改前冻结 jar 对照不劣化。
- **第二把测试武器只加数据与资源、不写 Java**即可装备、出招、同步、渲染。
- 故意写错的数据包:报错含文件与字段。
- 视觉:拔刀/收刀/两视角真实截图。

## 纪律
失败如实保留,不择优重跑;未验证项单列;不改 `docs/design/`;推送前等用户批准;输出 `docs/wf1/REPORT.md`。
