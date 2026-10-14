# B1:接管状态的帧率无关追赶与关节面皮肤

状态:**草稿,等待审核**。日期:2026-10-13。基线:`origin/work` 9019537(PR #20–#22 之后),分支 `b1/body-rig`。

## 1 结论
- 战斗姿态对身体的接管与释放,由"线性 3 tick 斜坡"改为**每 tick 追赶一个比例**(接管 0.4、释放 0.28),与帧率无关:一帧 50 ms 与两帧 25 ms 的结果相同(`SmoothingTest`、`BodyRigTest`)。状态挂在实体的 `Visual` 上(`BodyRig`),跨状态消息保持。
- 肘/膝两段方块相接的两个面原先采样了皮肤上错误的区域,弯曲时露出深色楔形;现在取该肢体自己皮肤上下相邻行的颜色。真实画面(`b1-pose-90-1`)的弯肘处颜色连续,未见楔形。
- 构建 `BUILD SUCCESSFUL`,JUnit 226 项,0 失败(新增 7 项)。

## 2 实现
| 文件 | 内容 |
|---|---|
| `client/body/Smoothing` | `alpha(perTick, ticks)=1-(1-perTick)^ticks`,`toward`,`angleToward`(绕过 2π 切口取短路)。 |
| `client/body/BodyRig` | `advance(wanted, nanos, tickPeriod)`:接管量向 1 或 0 追赶,末端收敛到精确的 0/1。 |
| `client/PoseDriver`、`ClientProxy.Visual` | 用 `visual.rig` 取代 `takeover`/`takeoverNanos`。 |
| `client/CombatBiped.cap` | 关节面 UV 改为肢体皮肤取样点;四边形按 Y 位置(最高/最低面)而非 `ModelBox` 的构造顺序识别;按类型而非字段名反射取 `quadList`(开发/混淆环境名字不同)。 |

## 3 限制与未做
- 没有按 Mo' Bends 那样把"每个部件持续追赶目标"做成通用机制:战斗层的目标是烘焙轨迹,仍由权重混合到原版角度之上;通用的逐部件追赶随 B2 的移动层一起做才有意义(那时才有程序化目标)。
- 关节面取样点是固定的(手臂 (46, 25.5)/(46, 28.5),腿 (6, 24.5)/(6, 27.5)),只适用 64×32 旧皮肤布局。
- 肘/膝弯曲时外侧仍有两段方块之间的几何缺口(只是颜色不再突兀)。
- 未跑战斗回归与性能 A/B(纯客户端外观改动,按现行口径放到里程碑)。
