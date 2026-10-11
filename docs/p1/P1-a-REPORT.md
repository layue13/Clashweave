# P1-a：切上、命中效果与抗浮空

## 范围与审核状态

基于 `origin/work` 的 `ce32fce`，独立 worktree，分支 `p1/launcher`。本轮实现切上和目标浮空，不实现 P1-b 空中追击，也不开始 WF-1。不修改 `docs/design/`、相机、锁敌或既有消息的二进制字段布局。这里的数值都是暂定测试值，不冻结 API。

**暂定规则**：没有架势/崩势系统。`light_2` 的真实、未被格挡的伤害命中确认后，窗口内 HEAVY 可进入地面切上；切上本身普通伤害被接受、目标存活且未格挡/豁免时才结算 LAUNCH。`control.requireConfirmedHit` 和 `control.requireGround` 可以关闭这两个入口条件。与 katana §4.2/4.3 尚待确定的“崩势开放挑飞”规则存在阶段性差异，后续审核应决定入口，不能据本轮冻结为最终规则。

## 数据与生产路径

- JSON 新增 `effects`、`lift`、`sweepElevation`；旧 JSON 缺省分别为 `[]`、0、0。未知 effect、非有限/负速度、超过服务端 `effectVelocityLimit` 的速度或 lift、无效 requires、重叠同输入边会在加载时拒绝。
- 边新增 `requires`，只接受 GROUND/AIR。GROUND 在执行前校验真实 `onGround`；AIR 本轮仅可解析，执行明确拒绝 `CONDITION_CHANGED`。新 `damageHit` 区分真实伤害确认与原有的格挡接触确认，P0 边仍使用原有 `hit` 语义。
- `light_2 --HEAVY--> launcher`：`[6,10)`、hit=true、damageHit=true、GROUND。原 LIGHT 边保留，输入不同，不构成歧义。
- launcher：5/2/10 tick、damage 4、reach 2.6、arc 80°、相对仰角 20°、LAUNCH vy=0.60、root distance/speed=0、lift=0。20° 是向上扫掠的经验测试起点，写在 JSON，非设计定稿。
- `lift` 目前是可验证的预算声明；MOVE_READY 创建攻击者预算时加入 `lift × impulseMultiplier`。不自动给攻击者起跳，也不新增空中链。
- 命中仍是一次普通 `attackEntityFrom`，仍用现有动作/目标/段号账本。没有修改 hurtResistantTime、lastDamage 或伤害事件递归语义。S1d 持久 maxHurtResistantTime 归属未改。
- 玩家仍经 tick 和墙钟双截止提交，普通怪物仍按原有确定性排序同 tick 提交；效果在实际伤害提交与 HIT/HURT 语义事件之后，按 effects 声明顺序处理。
- LAUNCH 使用 `motionY=max(motionY,vy)`，不是累加或压低原有较高上升速度。设置 velocityChanged，真实玩家显式发送原版 S12 速度包；没有浮空位置传送。
- LAUNCH 语义事件以目标为 actor、攻击者为 target，经既有 SemanticMessage 通路；追加枚举值 7，原有 0–6 不变。新消息类型：**无**。StateMessage 内原有 definitions JSON 增加上述动作字段与 effectVelocityLimit。客户端占位表现是一枚 crit 粒子，动作动画继续用已有回退链的 rigid_arc。

## 控制预算、生命周期与豁免

目标 UUID 和实体实例对应一份服务端 ControlBudget，所有攻击者共享。默认容量 3、每个 LAUNCH 消耗 1；第 3 次仍生效并进入 40 tick 保护，第 4 次的 LAUNCH 被忽略，普通伤害仍走原路径。保护不回溯或撤销已有上升速度。

重置要求：保护结束、连续落地至少 20 tick、距离最后一次成功控制至少 20 tick，三者同时满足。悬空时间再长也不补充点数。已恢复满预算且 120 tick 无成功控制后可清理记录；未恢复的空中链不会靠超时获得新预算。死亡、换维度、断线/实体卸载、服务器停止结束相应实体生命周期并清理记录。没有 SA 或其它主动补充接口。

| control 配置 | 默认值 | 含义 |
|---|---:|---|
| capacity / launchCost | 3 / 1 | 按目标共享控制点/每个 LAUNCH 花费 |
| protectionTicks | 40 | 耗尽后的抗浮空保护 |
| groundQuietTicks / chainExpiryTicks | 20 / 120 | 落地安静重置/满额旧记录清理 |
| effectVelocityLimit | 1.2 | vy 与 lift 上限，格/tick |
| launchCreditTicks | 60 | 有限浮空移动额度的最长保留期 |
| requireConfirmedHit / requireGround | true / true | 暂定入口条件 |
| excludeSwimming | true | 当前处于水中的目标不浮空 |
| exemptEntities | EnderDragon、WitherBoss、Ghast、Blaze、Bat、Squid | Boss 和原版飞行/水生类型；小鸡可浮空 |

此外按原版 EntityFlying/EntityWaterMob 类型、玩家创造/飞行能力豁免；这些类型规则与列表共同生效，删除列表项不关闭类型规则。理由是避免破坏 Boss、已有飞行或游泳运动。1.7.10 没有原版旁观模式；第三方旁观实现兼容性未验证。原有 protectedTarget 友好保护仍在几何采集前拦截。

移动额度独立于 root motion/WASD：每次服务端 LAUNCH 加入有限 `vy × 原有 impulseMultiplier(4)` 垂直额度；沿实际向上位移消耗，按 60 tick 超时丢弃；不以可能晚到的 C03 落地标志撤销新的速度授权。已有动作预算立即获额度；上升期间新动作只继承尚未消耗的余额，不重复领取原始额度。原有水平、跳跃、余量和全局倍率未放大。

## 构建、夹具与证据口径

命令在 Windows 原生 PowerShell、Zulu 25 构建，真实专服/客户端使用仓库配置的 Java 8：

```powershell
$env:JAVA_HOME='C:\Program Files\Zulu\zulu-25'
$env:VERSION='0.1.0-dev'
.\gradlew.bat spotlessCheck checkstyleMain checkstyleTest test build --no-configuration-cache --console=plain
.\gradlew.bat p0LaunchFiles -I tools/p0/validation.gradle --no-configuration-cache --console=plain
python tools/p0/dual.py --souls-view --snapshot-coremod --mod-snapshot build/p1/baseline-ce32fce.jar --label p1-base-0 --accept-eula
python tools/p0/dual.py --souls-view --snapshot-coremod --rtt 100 --mod-snapshot build/p1/baseline-ce32fce.jar --label p1-base-100 --accept-eula
python tools/p0/dual.py --launcher native --snapshot-coremod --mod-snapshot build/p1/candidate.jar --label p1-native-0 --accept-eula
python tools/p0/dual.py --launcher player --snapshot-coremod --mod-snapshot build/p1/candidate-final.jar --label p1-player-0 --accept-eula
python tools/p1/run_matrix.py
python tools/p1/run_impulse_regression.py
python tools/p0/dual.py --launcher native-final --snapshot-coremod --mod-snapshot build/p1/candidate-impulse-fix.jar --label p1-native-final --accept-eula
```

`run_matrix.py` 每个场景新启动专服和 P0A/P0B；100 ms 使用原 FIFO 代理，各半程 50 ms，不自动重试。所有标签与 jar 哈希见汇总 JSON。该脚本为复现记录，不包含提交的进程日志。

专服 PvE 矩阵用真实实体，生命 200、移动属性 0，真实物理逐 tick 记录最高点。隔离夹具通过反射赋予一个真实 launcher 动作实例，再由生产握手、几何、冻结、提交与效果路径运行；不是每组都从轻击入口发输入。预算压力测试则注入四个不同动作实例的接触，明确绕过几何/可达动作间隔，用真实普通施伤和效果结算验证共享预算。格挡夹具直接登记防御时间线；玩家路线另外使用真实客户端生产意图和生产校时/朝向历史匹配。

玩家路线固定双方水平位置以隔离 knockback/root motion 的距离变化，保留垂直物理和旋转；受击方连续转 yaw。最终版本还在 LAUNCH 后开启受击方新动作，检查剩余预算。这个受控场景不等于自然移动的真人玩法验收。

只提交摘要 JSON 与报告；原始日志、jar、逐 tick 过程留在忽略的 build 目录。

## 结果

### 构建与纯逻辑

上述生产检查和 build 输出 `BUILD SUCCESSFUL`，JUnit **126 项，failure/error/skipped 均为 0**，实际执行而非 NO-SOURCE。原 107 项保留，新增 19 项：schema 与向后兼容、非法 effect/lift、边歧义与 GROUND/AIR、预算耗尽/保护/落地重置/空中过期、多攻击者排列、效果顺序、枚举序号及有限垂直额度。SchedulerTest 的旧断言用迁移前的 JSON 测试快照继续执行，避免把“旧动作表没有 launcher”的断言套到新动作表。

冻结基线 `baseline-ce32fce.jar` SHA256：
`39f1102327e1a0cdd7d68a090d66fdf443bb79286d10e128a2c506f99f47ecc1`。
最终 `candidate-impulse-fix.jar` SHA256：
`ed6eddaea517d8b1838d0448622a47a7fc2f3ea9342ef028574ed28304def2ef`。
阶段 jar 和全部 23 次运行的结构化摘要见 [summary.json](summary.json)。旧失败批次不覆盖；它们的 jar 与最终 jar 不相同。

### 全部会话

“含 LAUNCH”指至少一个效果被接受的案例数，不是自然命中率；预算压力和多目标案例单列在后文。S08/角度只统计有明确连击/浮空测量区间的客户端；`—` 表示该场景未建立此测量区间，不能写成 0。预算纠正为整场专服计数，包含注入非法位移和夹具复位。

| 会话 | 完成或含 LAUNCH 案例数 | 预算纠正 | 区间 S08 A/B | 包引起 yaw/pitch 最大变化 |
|---|---:|---:|---:|---:|
| p1-base-0 | 连击 20/20 | 0 | 0/0 | 0° |
| p1-base-100 | 连击 20/20 | 0 | 0/0 | 0° |
| p1-native-0 | 64/86 | 0 | — | — |
| p1-player-0 | 20/20 | 0 | 0/0 | 0° |
| p1-night-control | 0/20 | 0 | — | — |
| p1-guards-multi | 1/4 | 0 | — | — |
| p1-player-release-0 | 20/20 | 0 | 0/0 | 0° |
| p1-player-release-100 | 20/20 | **14** | 0/14 | 0° |
| p1-regression-0 | 连击 20/20 | 0 | 0/0 | 0° |
| p1-regression-100 | 连击 20/20 | 0 | 0/0 | 0° |
| p1-budget-audit-100 | 20/20 | **10** | 0/10 | 0° |
| p1-enderman-helmet-control | 22/23 | 0 | — | — |
| p1-fixed-player-0 | 20/20 | 0 | 0/0 | 0° |
| p1-fixed-player-100 | 20/20 | 0 | 0/0 | 0° |
| p1-fixed-regression-0 | 连击 20/20 | 0 | 0/0 | 0° |
| p1-fixed-regression-100 | 连击 20/20 | 0 | 0/0 | 0° |
| p1-move-base-0 | 位移矩阵 | 2 | — | — |
| p1-move-fixed-0 | 位移矩阵 | 2 | — | — |
| p1-move-base-100 | 位移矩阵 | 2 | — | — |
| p1-move-fixed-100 | 位移矩阵 | 2 | — | — |
| p1-low-base | 低 TPS 位移矩阵 | 2 | — | — |
| p1-low-fixed | 低 TPS 位移矩阵 | 2 | — | — |
| p1-native-final | 70/86 | **1** | — | — |

全部会话正常结束（server exit=0）；专服客户端/OpenGL 类加载检查计数均为 **0**。最终 native 的 1 次纠正在蜘蛛 case31 的夹具重定位后、动作 START 前一 tick 出现（2681），并非玩家目标浮空用例。夹具仍保留旧动作的位移预算，不能把这一场写成“无纠正”；该复位边界未另行隔离验证。

### PvE：真实实体与原版行为的影响

最终 jar、夜间、关闭环境刷怪、戴南瓜隔离末影人凝视；生命 200、移动属性 0，但仍保留真实 AI、攻击和跳跃。每种 20 次，浮空最高点取整个案例最高 Y 减初始 Y；蜘蛛会包含自身跳跃，因此该列不等于纯 LAUNCH 高度。

| 真实目标 | 普通伤害命中/20 | LAUNCH/20 | 浮空成功率 | 成功案例最高 Δy：最小/最大/平均（格） |
|---|---:|---:|---:|---|
| 僵尸 | 20 | 20 | 100% | 2.363630 / 2.363630 / 2.363630 |
| 蜘蛛 | 7 | 7 | **35%** | 3.077230 / 3.501675 / 3.343938 |
| 小鸡 | 20 | 20 | 100% | 2.363630 / 2.363630 / 2.363630 |
| 末影人（南瓜控制） | 20 | 20 | 100% | 2.363630 / 2.363630 / 2.363630 |

蜘蛛其余 13 次没有命中提交，不能归为效果被拒绝；但自然交锋中的出刀成功率确实不够，**本轮不宣称自然 PvE 全部通过**。初轮白天矩阵僵尸/蜘蛛/小鸡各 20/20，末影人 1/20；独立夜间、未戴南瓜的末影人是 0/20。对照反编译 EntityEnderman 的凝视检测（眼点到目标身体中心、近距小于 4 格触发传送）及南瓜早返回后，做了独立控制，不替换前两批失败。当前扫掠不能保证打中正在传送或跳跃的目标，未修改它来迎合测试。

| 特殊场景 | 实测与结论 |
|---|---|
| 4 次同目标压力接触 | 4 次普通伤害均接受；LAUNCH true/true/true/false，第 4 次继续扣血约 3.68（僵尸护甲后），保护生效 |
| 普通格挡 | guard=1，伤害 1，效果 SKIP，无 LAUNCH；原版受击可产生约 0.96455 格上升，不能把所有上升当作切上 |
| 精准格挡 | guard=2，伤害 0，hit=false，无 LAUNCH，Δy=0 |
| 凋灵 / 恶魂 | 普通命中接受、exempt=true、无 LAUNCH；自身 AI 可上升，不以 Y 变化判断豁免 |
| 创造玩家（真实 GameType） | attackEntityFrom 返回 false，无伤害、无 LAUNCH；早先只改能力 flag 的用例另保留 |
| 同动作多目标 | 南瓜控制 case111：实例 100023、tick2026，两只僵尸都命中并浮空，各自 remaining=2 |
| 小目标后续攻击 | 小鸡/蜘蛛浮空后约峰值时用实际 light_1 扫掠，均未获得追加命中；洞穴蜘蛛实际 launcher 扫掠未命中，另注入真实伤害接触后成功浮空，但后续实际 light_1 仍未命中 |

后续命中只是隔离时刻的一次记录，不是 20 次命中率，也不是 P1-b 空中追击。固定身体高度层、目标峰值高度及后退 knockback 会影响后续几何；不修改 SweepGeometry 或攻击者空中链。

### 玩家浮空、延后提交与发现的预算错误

最终 0/100 ms 各 **20/20 LAUNCH**；受击方每档 20 次在浮空后开启新动作预算；两端 S08=0，包引起 yaw/pitch 变化=0，专服预算纠正=0，未观察到 moved-too-quickly。两档各 61 次玩家命中提交，双截止违例 **0**。最终 PvE 的 75 个普通怪物提交额外等待 tick 最大值为 **0**。普通怪物立即提交、玩家延后提交的既有规则未变。

客户端观察到 S12 速度包，浮空代码没有发位置包；观察器统计的是所有可见实体的 S12，不是目标专属包数，不能用总数证明每次只有一个速度包。玩家最高 Δy：0 ms 为 2.963630–4.374322 格，100 ms 为 2.963630–3.130199 格，包含前置轻击 knockback 与包到达时机，不能拿它与隔离 PvE 的单次 0.6 冲量高度直接等同。

开发版 100 ms 曾出现 14 次预算纠正，独立诊断复现 10 次。证据关键片段：tick1030 LAUNCH 授权 2.4 格；1031 上升；1032 晚到的落地 C03 报 ground=true；1033 又收到上升位置，但原实现已把余额清零；1035 新预算 allowance=0，1037 被纠正。修复是按实际正向 Y 变化消耗余额并按既定 60 tick 截止，不用 ground 标志提前撤销；没有增大任何额度。新增测试复放这条轨迹。代价是未消费的小额余额可留至截止，即使目标已经落地，报告明确保留这点。

100 ms 最终浮空会话整场另有 STALE=24、RATE=7，P0 基线 100 ms 整场 STALE=20、RATE=2，最终常规 100 ms STALE=12；这些发生在初始化/非测量区间，20 个实际浮空案例中拒绝数为 0。它们仍在 JSON 中保留，不据整场统计宣称所有请求均被接受。

### P0 对照及限制

| 指标 | 冻结 ce32fce | 最终 P1-a | 结论 |
|---|---|---|---|
| 常规 0/100 ms 连击 | 每档 20/20 | 每档 20/20 | 本批未下降 |
| 连击 S08 / 包角度变化 | 每档 0 / 0° | 每档 0 / 0° | 本批未下降 |
| 0/100 ms 位移矩阵 5 格水平、5 格纵向 | 每场各纠正 1 次 | 每场各纠正 1 次 | 检出保留 |
| 1.2/1.5 倍短时超速 | 两档均未检出 | 两档均未检出 | 原有灵敏度缺口保留 |
| 低 TPS（START 人为延长 150ms） | 非法水平/纵向各纠正 1 次 | 同左 | 同样矩阵无新增合法纠正，不等于长时间稳定性证明 |
| 0 ms 战斗逻辑均值/最大耗时 | 0.163462 / 6.0639 ms | 0.164466 / 6.7855 ms | 单场最大值上升，未做统计显著性判断 |
| 100 ms 战斗逻辑均值/最大耗时 | 0.157373 / 5.3398 ms | 0.162076 / 5.2351 ms | 单场均值略升，不声称性能改善 |
| 低 TPS 战斗逻辑均值/最大耗时 | 0.263862 / 5.8277 ms | 0.272132 / 12.3775 ms | 最大值升高，不能声称全部历史数字不劣化 |

耗时是现有 P0_PERF 最后累计样本，常规各 1800 样本、低 TPS 为 500/600，主机时序不同且没有反复 A/B。低 TPS 结束前动作启动总数不同（31/38），不是同数量长稳压测。碰撞、相机、完整输入/联网生命周期和历史玩法指标未全量重测，不能由上表推导 P0 全部验收再次通过。

## 保留的失败与未验证项

1. 新 JUnit 首轮错误地认为 light_2 在 tick 35 结束；真实数据是 37。修正测试时刻后完整重跑，生产时序未改。
2. 将 `build` 与验证 init 脚本合在一起会额外检查旧 p0Diagnostics 源集，其 TraceObserver/TraceTransform 通配符 import 被 Checkstyle 拒绝。未修改这些无关文件或关掉生产检查。上面分开的生产 build 与 p0LaunchFiles 命令通过。
3. 原始白天末影人矩阵保留，夜间对照也保留。近距离凝视会触发原版传送，不能把离开刀弧的位置变化归为 effect 失败，也不能只挑命中的一次。佩戴原版南瓜的独立对照用于验证条件成立时的效果，不替换失败批次。
4. 本轮没有重跑 P0 全部历史矩阵：相机构图/碰撞、全部输入与联网生命周期场景、各姿态渲染截图、真人主观感受仍未验证。已有位移与低 TPS 单批对照不代替完整历史验收。
5. 浮空期间死亡/换维度/断线/换物品等真实联网生命周期，以及游泳、所有列表豁免实体、第三方飞行/旁观模组共存未逐项运行；清理路径已实现，未验证部分仍需后续补测。
6. 小目标空中后续命中只记录结果，不改 SweepGeometry 高度层或修补小目标刀弧。P1-b、崩势、SA、正式浮空动画与追击链未做。
7. 非默认 vy/lift 数值只做解析/预算单元测试，未逐档跑真实客户端；空中重置、死亡等清理状态机有逻辑测试，真实联网生命周期仍有上述缺口。效果顺序测试覆盖核心执行顺序，第三方伤害事件模组共存未补测。

## 审核建议

效果、控制预算与最终玩家浮空的限定场景已有实际证据；自然蜘蛛几何、真实联网生命周期、完整 P0 回归及性能最大值仍需审核。暂定入口及新配置不修改设计文档。本轮提交为草稿，停止在 P1-a，不能据此直接开始 P1-b 或 WF-1。
