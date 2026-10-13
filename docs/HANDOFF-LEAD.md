# Clashweave 主导者(Lead)交接

更新:2026-10-13。接手者是在 Windows 端直接工作的 Claude。你既是**技术负责人/设计审核者/提示词作者**,也可以**直接动手**(构建、跑游戏、改代码),并且 Windows 上装有 Codex,可作为第二执行者。`docs/design/handoff.md` 是设计阶段的旧交接,本文是工程阶段的交接,两者不冲突。

## 0. 先读什么(按顺序)

1. 本文。
2. `docs/design/as-built-p0.md`(P0 已实现的机制与默认值,在 PR #16 的分支 `docs/sync-p0`,尚未进 `work`)。
3. `docs/design/modules/extension.md` §15–17(内容分级与注册表)、`docs/design/modules/assets.md`(骨架、动画、移动层的设计来源)。
4. `docs/wf1/WF-1c-REPORT.md`、`docs/p1/P1-a-REPORT.md`(最近两轮的完整证据与失败史)。
5. `docs/art/katana-a.md`、`docs/art/SHEATH-MOUNT-SWEEP.md`(美术决定与挂点)。

## 1. 项目与协作方式

- **Clashweave**:Minecraft 1.7.10 / Forge 的**战斗引擎**(不是一把刀的 mod)。服务端权威;武器、动作、动作图、动画、特效、资源都是**内容**,以命名空间 ID(`katana:light_1`)组织,Gameplay 清单须与服务端哈希一致,Presentation 清单允许差异。第一套内容是日本刀(居合/纳刀/轻重/切上),之后是别的武器和 SA/爆气。Java 8 目标,GTNH 构建插件,Gradle 用 Zulu 25 运行。
- **用户(layue13)**:做决定的人。偏好:解决真问题;结论要对着代码/文档/实测核实;重要取舍用实验决定;破坏性或对外动作先确认;如实汇报(已完成并验证 / 已完成未验证 / 未完成 / 受阻)。**推送到共享分支要用户明确批准**(用户每次说"推送"/"批准"才推);合并 PR 也要他确认。他说"你看着来"时可自行决定,但仍要说明。
- **你的汇报格式**:先给结论,再给证据(命令与输出),再列限制和下一步;不要编造数字或"完成";失败批次保留不覆盖。
- 用户说话直接,对"乱"很敏感:每次回复开头先说"现在状态/要他做什么",一次只给他**一个**提示词。

## 2. 分支与 PR 地图(截至 2026-10-13)

| 项 | 状态 |
| --- | --- |
| `work` | 主线。已含 P0 与 **P1-a(PR #17,743ae6f)** |
| PR #18 `prototype/wf1-wire` | **WF-1 全部**(内容层、Scheduler 数据驱动、Forge 接线、渲染解耦、握手、第二武器、生命周期修复)+ 合入 PR #15 美术。草稿,**待用户确认合并**。最新 2bcd701(我在 8a51b22 之上加了一个清理提交,未在容器里编译,请你用 Gradle 复核) |
| PR #15 `claude/upbeat-goodall-klai2o` | 美术(模型、贴图、`art/katana/` 生成脚本、`docs/art/*`、v3 动作预览)。内容已随 #18 合入,合并 #18 后关闭 #15 即可,但 `art/katana/` 与 `docs/art/motion-v3/` 要确认是否已进入 `work`(#18 合入了该分支,应该已含) |
| PR #16 `docs/sync-p0` | 文档(`as-built-p0.md` + 本文)。草稿 |
| `prototype/wf1-core` | WF-1a/1b 的容器内工作,已被 #18 包含,不用再动 |
| `p1/ground-fix` | **尚未创建**,提示词已给用户(见 §4-①) |

## 3. 已做的决定(不要再争论,除非用户翻案)

- **美术方向 A**(低多边形 + 手绘色块);概念图 B/C 仅作皮肤参考。刀长默认 **0.786×玩家可见身高 1.875 = 1.47**;黄金比例参数化生成(`art/katana/build_models.py`,`KATANA_REF` 环境变量可换)。
- **挂点**:X=0.58 Y=0.55 Z=0.18 Angle=45(扫描组 C);居合起手/纳刀结束按"**瞬抓**"处理(动作首帧换成空鞘+手中刀)。C2a 做完"交锋态左手扶鞘口"后应回测并把 X 往身体收。
- **reach 与可见刀不强制对齐**,开发者自由设定,只对差距给 warning。
- **玩家骨架由我们自己接管,不依赖 Mo' Bends**:战斗/持武器态才接管渲染;内容是烘焙的骨骼变换曲线,运行时不做高级 IK(原设计后置);先刚性(C2a)再柔软关节(C2b,盔甲穿帮要决策)再移动层(C3)。
- **相机纯 SOULS**(第三人称锁定);参数见 `as-built-p0.md` §1。锁定锥水平 30°/垂直 60° 且高度差 ≤ range/2。
- **握手**:Gameplay 清单不一致=拒绝(踢出),仅 Presentation 不同=允许。**`render.cfg` 的 `[mount]` 已移除**,挂点以内容数据为准。`content.legacyKatanaJson` 回退开关保留一个版本再删。
- **切上入口暂定**:light_2 真实伤害命中后的地面 HEAVY;设计里的"崩势开放挑飞"留到 SA/崩势阶段。控制预算 3 点、抗浮空保护 40 tick、重置要求落地≥20 tick 且距最后控制≥20 tick。
- Level 2 Java API 先内部、不冻结;第二武器只靠数据;新命中形状(直线突刺、地面冲击)、双持、背部挂点放 WF-2。

## 4. 工作队列(按顺序)

**① p1/ground-fix(提示词已给用户,可能已在执行)**:100 ms 玩家浮空偶发 18–19/20,基线同样出现。线索:客户端根位移 `moveEntity(dx,0,dz)` 清空 `onGround`,服务端 `requires:GROUND` 拒绝(`CONDITION_CHANGED`)。要求修复后 100 ms 连续多轮 20/20,不放大任何额度。若用户已把提示词给了别人,先问状态,别重复开工。

**② 合并 PR #18**:用户确认后合并。合并前建议你亲自再跑一遍 `spotlessCheck checkstyleMain checkstyleTest test build`(我的清理提交 2bcd701 只动了 `CombatServer.begin`,删掉多余的 `PlayerLoggedInEvent` 构造),通过再合。

**③ C2a 玩家骨架 v1**(基线=合并后的 `work`,**一次只给用户一个提示词;或你直接做**):
- `track` 动画预设:读烘焙轨迹(`art/katana/motion2.py` 输出:刀姿四元数、`bladeDrawn`、骨骼单位方向 armR/armL/legR/legL、pelvis、torsoYaw、footR/L),按 tick 采样+四元数插值,partial tick 平滑。轨迹是 Presentation 内容,经 Animation 注册表引用。
- 接管 `ModelBiped` 的双臂、双腿、躯干扭转;双手握柄(右手近刀镡、左手近柄头);两视角统一刀姿(顺带修好第一人称刃口反向,去掉第三人称单独的 90° Y roll);交锋态待机构(动作开始/结束不跳回原版走路姿态);拔刀用 `stowed_empty`、收刀用 `stowed`;第一人称自绘可见双手臂。
- 持武器时屏蔽原版摆臂覆盖挥刀手臂;检测到改玩家模型的模组(如 Mo' Bends)时战斗态让位并记日志。
- 验收:构建/JUnit;0/100 ms `--souls-view` 连击 20/20、S08=0、包视角变化=0;六个动作两视角真实截图/录屏对照 `docs/art/motion-v3/`;交锋态下回测挂点 X(0.58 起每 0.04 一档)并报告穿臂与右手可达性;盔甲/细臂皮肤/蹲下/骑乘穿帮列为已知项。
- **性能**:渲染热路径无每帧字符串/分配/资源加载;与基线对照渲染 CPU 提交时间(用 WF-1c 的 `RenderProbe`)。

**④ 动作轨迹继续**(我留下的,纯 Python/Blender):`art/katana/motion2.py` + `motion_preview2.py`。v3 自检全过(`reachViolations` 全 0),但手写关键帧离"手够得到"偏差 0.29–0.30 m(light_2、iai 的有效帧末尾与冲过帧),成品是合法的;可见刀尖距离比服务端 reach 短 0.29–0.56,只作 warning。还没做:launcher(切上)的轨迹;居合起手"瞬抓"在轨迹里的显式标记。Blender 5.2 无头命令见 `art/katana/README.md`;Windows 上用用户自己的 Blender。

**⑤ 文档**:PR #16 补"内容引擎设计页(注册表与 schema,取自 `docs/wf1/WF-1a-NOTES.md`/`WF-1b-NOTES.md`/`WF-1c-REPORT.md`)与内容作者指南"、P1 设计页。不要改 `docs/design/` 的设计规则本身,除非用户要求。

**⑥ 之后**:P1-b 空中追击(攻击者空中动作链、`requires:AIR` 边、落地收束;注意 `ActionCatalog.launchPolicy` 会把所有进入带 LAUNCH 动作的边强制成 `GROUND`,P1-b 做 AIR 入口时要改;小目标/蜘蛛命中几何(20° 仰角、4 层身体高度)也并入这里);C3 移动层;C2b 柔软关节;WF-2(新命中形状、双持、背部挂点、第三方模组适配演练);Mo' Bends 兼容探针(低优先);Codex 出 2D 素材(图标、斩击拖尾、锁定标记)。

## 5. 已知遗留(来自报告,别当成已解决)

- 服务端在动作中传送玩家,会被位移校验当成非法位移拉回并计一次纠正(既有机制);决定:ground-fix 之后做"传送前取消动作并重置预算"的小项。
- 左手握位数据已读入但刚体适配器不消费(C2a 解决);第二武器测试复用 katana 模型;整合服务器路径、更高延迟、低 TPS、长时间稳定性未验证。
- 性能:服务端战斗逻辑均值与基线区间重叠,最大值会话间波动大(基线也有 177 ms 离群);最终冷协商补丁未重跑三次矩阵。
- P1-a:夜间蜘蛛 7/20 是没打中不是效果被拒;末影人传送批次保留;1.2/1.5 倍短时超速未被位移校验检出(既有灵敏度缺口)。
- 现有 `Scheduler` 之外仍有的短 ID/katana 痕迹已在 WF-1c 清理,发现新的就按"目录自带默认命名空间"处理。

## 6. 工具与流程要点(都踩过坑)

- **构建**:Windows 原生 PowerShell,`$env:JAVA_HOME='C:\Program Files\Zulu\zulu-25'; $env:VERSION='0.1.0-dev'`,`.\gradlew.bat spotlessApply cleanTest spotlessCheck checkstyleMain checkstyleTest test build --no-configuration-cache --console=plain`;验证夹具用 `.\gradlew.bat p0LaunchFiles -I tools/p0/validation.gradle ...`(不要和 `build` 合并运行,旧 `p0Diagnostics` 源集会被 Checkstyle 拒绝)。**检查 `BUILD SUCCESSFUL`,不要只看退出码**。
- **真实验证**:`python tools/p0/dual.py --souls-view [--rtt 100] --mod-snapshot <jar> --label <新标签>`(`--launcher player` 测玩家浮空;`--wf1c <mode>` 测内容/生命周期/握手/第二武器;`--render-probe` 测渲染 CPU 提交时间)。标签不能复用,脚本拒绝覆盖。基线 jar 用 `origin/work` 当前提交单独构建,A/B 交替而不是先后跑。
- **验收口径**:连击 20/20、区间 S08=0、包引起视角变化=0、位移纠正不高于基线、JUnit 实际执行数入报告;失败保留、不择优重跑;未验证单列。
- **Codex**:Windows 上直接调用。参考命令 `codex exec --skip-git-repo-check -m gpt-6.1-sol -c model_reasoning_effort=medium -s workspace-write -C <worktree> - < prompt.md`。适合:不依赖游戏的纯逻辑实现、独立代码审查(`-s read-only`)、出 2D 图;不适合:需要真实游戏的验证。它写不了 `.git`(沙箱),所以你自己提交;它有用量上限,中断时会留半成品,要读完再接。**让它审你的代码,再亲自核实它的发现**(它在 P1-a 找到的 MOVE_READY 重放漏洞是真的)。
- **Blender**:`art/katana/` 全部是无头脚本(`build_models.py` 生成模型、`paint_atlas.py` 画图集、`render_preview.py`/`motion_preview2.py` 渲染)。用户的 Blender 在 Windows 上;`.blend` 已设为二进制(`.gitattributes`)。
- **提示词纪律**:给执行端的提示词要写清:基线分支、先读的文档、要做/不要做、验收口径、性能要求、"推送前等用户批准"、输出报告路径、"完成后停下"。用户明确要求**性能要注意**:热路径不得有每 tick/每帧的线性扫描、字符串拼接、不必要分配;给出与基线的对照数字。
- **PR**:每次推送后确认有对应 PR(草稿);回应评论要克制;提交信息末尾带 Co-Authored-By 与会话链接(见各自会话的归属提示)。

## 7. 我犯过的错(别重蹈)

- 没读设计文档就提议与之冲突的方案(例如运行时 IK,而 `assets.md` 已规定烘焙)——先查 `docs/design/`。
- 给用户的提示词里把"前置未完成"当成已完成(把 WF-1 发给执行端时 P1-a 还没合并)——发之前核对 `git ls-remote`。
- 阈值/数字凭感觉写(早期的 3° 停滞阈值)——要有实测依据。
- 后台任务用 `pgrep -f` 自匹配当监控、把后台命令的 `exit 0` 当成功——用"命令本身即监控",并 grep `BUILD`/`GRADLE_EXIT`。
- 报告里把"一次全绿"说成"已修复"——要给多轮、交替基线的数据。
