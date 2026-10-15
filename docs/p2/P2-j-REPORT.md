# P2-j 命中形状数据化(A/B 计划 B1)

状态:已实现并做了实机检查。**所有数值是起点。**

## 1 做了什么
命中检测原先只有"以玩家为中心的扇形扫掠"(`reach`、`arc`、`sweepElevation`),引擎里写死。现在动作数据可声明 `shape`:

| `shape` | 字段 | 含义 |
|---|---|---|
| `sector`(默认) | `reach`、`arc`、`sweepElevation` | 与以前完全相同的扇形扫掠(默认值,已有数据不变) |
| `thrust` | `reach`、`width` | 一条宽 `width` 的直线,随有效帧向前伸到 `reach`;目标须与身体高度段重叠,中间有方块则不命中 |
| `burst` | `offset`、`width`(= 半径,必须大于 0) | 攻击者前方 `offset` 处、半径 `width` 的圆;只打站在地面附近的目标,中间有方块则不命中 |

- 纯几何在 `core/HitShapes`(点到盒、线段到盒、突刺、冲击),高度与方块遮挡在 `forge/SweepGeometry`;`CombatServer.collect` 按形状分派并扩大候选范围。
- 数据校验:未知 `shape`、`width`/`offset` 越界(0–32)、没有半径的 `burst` 都在加载时报错(`ActionCatalog` 与 `ContentRegistry`)。新字段进入目录线上数据与哈希。
- 单测 `HitShapesTest`(几何数值、数据解析、两种非法数据)。

## 2 实机
第二把武器仍用 WF-1c 的 `wf1test:glaive`(只有数据,复用刀的模型),两份测试包在 `tools/p2/shape-hit`、`tools/p2/shape-miss`(与 `glaive.json` 同结构,只改形状):`--wf1c weapon2 --content-pack tools/p2/shape-<hit|miss> --weapon-items clashweave:katana=katana:katana,minecraft:stick=wf1test:glaive`。僵尸固定在攻击者前方 2 格。

| 标签 | 数据 | 结果 |
|---|---|---|
| `p2shape4-hit-092922` | `thrust`(reach 3.6、width 0.6)、`burst`(offset 1.6、width 1.8) | 冻结命中 39 次:`thrust` 19、`thrust_b` 5、`sweep`(burst)15 |
| `p2shape4-miss-093026` | `thrust`(reach 0.3)、`burst`(offset 7、width 1) | 三种动作起手,冻结命中 **0** 次 |

## 3 失败与更正(保留)
- `p2shape-*`:误用了旧 jar(没有新形状),进程被我停掉,作废。
- `p2shape2-miss-092544`:对照包 `thrust` 的 reach 设为 1.0,结果**仍然命中**。服务端日志(新加的 `FREEZE ... shape= reach= at=`)显示形状与够距都读对了,是动作自带前冲 0.5 格,命中时攻击者已站在 z=0.45–0.8,线伸到 1.45–1.8 再加半个宽度碰到 z=1.7 的僵尸。**对照设计有误,不是代码问题**;对照包改为 reach 0.3 后(`p2shape4-miss`)0 命中。
- `p2shape2-hit-092439`:命中也都对,但因对照有误,该批作为"命中"一半的证据只作参考。

## 4 未验证 / 限制
- 突刺没有为"宽度随进度变化"等做更多形状参数;冲击不随有效帧扩大;两者都不考虑 `sweepElevation`。
- 没有拍画面:新形状只改命中,没有对应的特效或轨迹,`motion2.py` 的"刀尖在判定扇形内"检查只对 `sector` 有意义。
- 只测了一个固定僵尸、攻击者面向正前方:没有测侧向、空中目标(对 burst 的高度限制)、墙后目标(方块遮挡)和玩家对打。
- 100 ms 没有测。
