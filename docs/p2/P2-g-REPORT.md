# P2-g 主动 SA 例子与延迟斩修正

## 1. 修正:延迟斩被目标无敌帧吞掉
P2-f 记录的"每次居合总有一段 `hit=false`"原因不是位置,而是目标上一击留下的受击无敌帧使 `attackEntityFrom` 拒绝延迟斩。修法:延迟斩出手前清 `hurtResistantTime`(预定的延迟斩本就是独立一击)。
复测(0 ms 与 100 ms 的 `--special` 运行):所有 `DELAYED_STRIKE` 均 `hit=true`(0 ms 9/9 影缝 + 1 次居合延迟斩;100 ms 见下)。**未复测**模式内的双段(p2f 夹具);同类原因,预计同样修好,但未跑。

## 2. 主动 SA 例子:影缝(`katana:shadowrend`)
- 数据:`sa.json` 的 `active`(range 6、cooldown 200、strikes 3、interval 4、damage 4、posture 12),已装到 katana 的 `saSlots`。校验范围写在 `ContentRegistry`。
- 规则:V(`Intent.SPECIAL`)在拔刀、无动作、不在格挡时,对前方 90° 锥内 6 格内最近的活体连斩 3 刀;冷却 200 刻;结果 `SPECIAL` / `REJECT:SPECIAL_COOLDOWN` / `REJECT:SPECIAL_NO_TARGET` / `REJECT:SPECIAL_BUSY` / `REJECT:NO_SA`。模式内每刀按 P2-f 规则加倍。
- 刀数与伤害走既有的 `DELAYED_SLASH` 路径(受相同的存活、距离、格挡判定约束)。

## 3. 实机(`--special`)
| 运行 | 结果 |
|---|---|
| p2g-sp1(0 ms) | 3 次 `SPECIAL`,各 3 刀,共 9/9 命中;间隔 158 刻时 `REJECT:SPECIAL_COOLDOWN`。 |
| p2g-sp100(100 ms) | 4 次 `SPECIAL` 被接受;命中 10(影缝与居合延迟斩合计)、未命中 0。 |

## 4. 未验证 / 失败
- 夹具第一次按键时武器仍为收刀状态,服务端仍接受(`armed` 只判断持有武器);是否要求出鞘未定。`SPECIAL_BUSY`、`SPECIAL_NO_TARGET`、`NO_SA` 路径无实机覆盖。
- 无专门动作与轨迹:影缝目前是无动画的瞬时三刀,只有既有的延迟斩表现;无音效、无出手姿态。
- 数值未经手感验证。
