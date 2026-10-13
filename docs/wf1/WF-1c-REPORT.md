# WF-1c：内容引擎的 Forge 接线、渲染解耦与实机验证

状态：**草稿，等待审核；未推送**。日期：2026-10-11。平台：Windows 11，Java 8 运行时（Zulu 8），Gradle 使用 Zulu 25；专用服务器与两个 Minecraft 1.7.10 客户端均为真实进程（`tools/p0/dual.py`）。

复核结论：接线补缺与最终构建已验证；最终冻结包 0/100 ms 的连击、玩家浮空均为 20/20、S08=0、包视角变化=0。但更早复核批次的候选出现连击 19/20、玩家浮空 18/20 和 1 次 S08，失败未覆盖，**不能宣称整批严格验收通过或已证明不劣化**。本轮不扩大到物理修复，待审核决定。新增复核证据见 §10。

术语：**通过**＝该项预设判据在记录的运行中成立；**失败**＝判据不成立，原运行保留；**未验证**＝本轮没有运行；**观察**＝只记录，不下判据。运行标签（如 `ab-cand-0-1`）与 JAR 哈希见附录 A，原始日志留在忽略的 `build/`，结构化摘要在 [WF-1c-summary.json](WF-1c-summary.json)，截图在 [images/](images/)。

## 1 范围与约束执行

| 项 | 内容 |
|---|---|
| 任务 | 把 WF-1a/1b 的纯逻辑内容层接入 Forge（加载、武器表、命名空间、握手），把渲染从 `KatanaRendering` 解耦为数据驱动，加入纯数据的第二把武器，并在真实游戏中验证生命周期、错误数据包与性能 |
| 分支 | 本地 `prototype/wf1-wire`，基于 `origin/prototype/wf1-core`（`a9f96bc`），独立 worktree |
| 美术合入 | 合并 `origin/claude/upbeat-goodall-klai2o`（PR #15，`c0bf83e`），合并提交 `70df9cc`。美术资源无冲突，保留该分支版本；唯一文本冲突在 `tools/p0/java/com/layue13/clashweave/validation/Validation.java`（两侧各增一行 `Class.forName(...)`），两行均保留 |
| `docs/design/` | 未修改（`git diff --stat origin/prototype/wf1-core..HEAD -- docs/design` 为空） |
| 推送 | 未推送，等待批准 |
| 后续工作 | 未开始 C2a、P1-b |

## 2 基线与对照 JAR

| 名称 | 来源 | SHA-256 | 用途 |
|---|---|---|---|
| 基线 `base` | `origin/work` `743ae6f`（PR #17 合并），独立 worktree 构建 | `6f63e327f25b0a5e18cf89caed3505e9bfdda4f2aec47995312e6695df7f7e1d` | 性能与回归的指定对照；133 项 JUnit |
| 美术对照 `ctrl` | 本分支 `9ab1aef`（`wf1-core` ＋ 美术合入 ＋ Gson 修复，旧接线） | `41930d21df2304bbb0fa96d6f5bbf0cc30c449fec336b0f0fe038e5b2d8234b1` | 把“美术模型变重”与“新接线”分开：`base`→`ctrl` 的差是模型，`ctrl`→`cand` 的差才是接线 |
| 候选 `cand` | 本分支 `e355d8a`（`src/main` 与 `634a01f` 相同） | `846dea5eedd728c394cd776cadd717a7aa15a4cb1a2d772c1f962ac7aebbc140` | 被验证对象 |

所有对照使用同一份 `p0-validation.jar`（本分支的夹具）。夹具对两种 JAR 都可链接：动作 ID 经 `Ids.local` 去命名空间，旧 `Clashweave.config.actions` 与新 `server.rules()` 经反射二选一（`tools/p0/java/.../validation/Ids.java`）。

## 3 实现概述

### 3.1 加载与替换（任务 1）

- **加载**：`ContentLoader`（`content/ContentLoader.java`）在启动时读取类路径上 `assets/clashweave/content/<ns>/*.json`（目录或 JAR；JAR 无目录项时再直接读自身 JAR）与配置目录 `config/clashweave/content/**.json`，每个文件一次读入内存后交给 `ContentRegistry.load`。`ContentRuntime`（`forge/ContentRuntime.java`）负责日志与拒绝启动。
- **启动日志**（候选，服务端，`ab-cand-0-1`，逐字）：
  `Clashweave content: 5 file(s), 1 pack(s), 1 weapon(s), 1 style(s); registry load+validate 34.2 ms, with server overrides 45.8 ms; packGameplay=518dafda0b4e6b102b77cb058c6702c861566b3c86da9a8a5b0e6e45a1cc7c96 presentation=7bef4ba2b818e6eaba7f9c0d735e900c765201cdbc3f9e6c41b93d95579f17ff effectiveGameplay=2314f71c7d568d7b706cc536588ee196dbb0305c7a625eb0ad83afb39b07173e overrides={"effectVelocityLimit":1.2000000476837158,"launchRequiresGround":true,"launchRequiresHit":true,"legacyKatana":false}`
  Gameplay 清单哈希 `518dafda…` 与 WF-1b 记录一致；`effectVelocityLimit` 的长小数来自 Forge 以 float 读取配置，再转 double，两端以同一字符串计算，确定。
- **替换**：删除 `StaticKatanaResolver`、`WeaponResolver`、`CombatWeapons.DEFAULT`、`CombatConfig.actions` 与固定读取 `combat/<actionData>.json` 的代码。`CombatWeapons`（`forge/CombatWeapons.java`）持有服务端权威的 Item→武器表（`IdentityHashMap<Item, Entry>`），`Entry` 在建表时已解析好 `WeaponDefinition`、**按 `weapon.style` 缓存的 `ActionCatalog`** 与客户端载荷 JSON；表来自 `combat.cfg` 的 `weapons.items`（默认 `clashweave:katana=katana:katana`），在 `postInit`（所有物品已注册）建立，未知物品/武器/倍率越界在启动时报错并拒绝启动。`resolve` 不再在每 tick 做校验（旧实现每次调用 `new double[]` 并两次字符串比较）。
- **配置覆盖作用于缓存目录，并进入协商哈希**：`EffectiveRules`（`content/EffectiveRules.java`）对每个风格建一份缓存目录，施加 `launchRequiresHit`、`launchRequiresGround`（`ActionCatalog.launchPolicy`）与 `effectVelocityLimit`（只能收紧风格自身上限；与风格冲突时拒绝启动并点名风格）。**有效 Gameplay 哈希** ＝ SHA-256（`"clashweave-rules-1"`、pack Gameplay 哈希、规范化覆盖文本、按风格 ID 排序的各风格客户端载荷 JSON）。载荷 JSON 即覆盖后的真实动作字段与入口规则，所以覆盖一旦改变规则，哈希必变（`WiringTest.effectiveHashIncludesServerOverridesNotJustThePack`）。未覆盖时 `effectiveGameplay`（`2314f71c…`）≠ `packGameplay`（`518dafda…`）。
- **旧 `katana.json`**：文件原样保留在 `assets/clashweave/combat/katana.json`（旧测试仍读取它），**不再是权威来源**；保留一个版本的回退开关 `combat.cfg` `content.legacyKatanaJson`（默认 `false`）。开启时 `katana:default` 由旧 JSON 构建，有效哈希随之不同（实测 `7e9679e8…`，会话 `legacy-1`），不冒充 pack 规则。
- **约束**：协商载荷限制由 `EffectiveRules` 在启动时检查——动作 ID ≤ 32 字符（`StateMessage`/`SemanticMessage` 的既有上限），单风格载荷 ≤ 20000 字符（`StateMessage` 上限 24000）。

### 3.2 命名空间（任务 2）

| 原写死位置 | 现状 |
|---|---|
| `ClientProxy.java:163-165` 预测入口 | `ClientProxy.java:267` 用 `own.catalog.entryTarget(own.state.sheathed, intent)`，与服务端 Scheduler 同一张入口表；风格无收刀态时自然得到“只有 ready” |
| `KatanaRendering.java:57` `action.equals("sheathe")` | 状态到达时由 `catalog.entersSheathed(action)` 一次算出 `Visual.returnsToSheath`（`ClientProxy.java:486`），渲染只读布尔 |
| `PresentationAssets.java:33` 短 ID 注册表与 `rigid_arc` | 整个类重写：按 Resource/Animation 数据建表，无任何 ID 常量 |
| `CombatPresentation.java:54` 字面量 `"sheathe"` | `sheathed(player, instance, tick, action)`；`CombatServer.advance` 在 tick 前记下将结束的动作 ID（`CombatServer.java:637`） |
| `ActionCatalog.normalize` 的 `"katana:"` | `ActionCatalog.namespace` 为目录自带默认命名空间（内容目录取风格 ID 的命名空间，无该字段的旧数据取 `katana`）；`normalize` 改为实例方法；仅 `EntryRules.legacyKatana` 用包内静态 `legacyNormalize` |

热路径查找：`ActionCatalog.byId(qualified)` / `require(qualified)` 直接查 `HashMap`，不做 `normalize`；`Scheduler` 的每 tick 路径与客户端渲染/预测都只用已限定 ID。`HotPathAllocationTest` 实测这些查找每百万次调用分配 **0 字节**（§6.8）。

### 3.3 握手（任务 3）

- 客户端在进入世界后第一 tick 向服务端发 `HelloMessage`（判别号 4，两个 64 字符哈希：pack 的 Gameplay 与 Presentation 清单哈希）。服务端在服务端线程比较（`Handshake.check`，纯逻辑，`WiringTest`），并向通过者回 `ContentSyncMessage`（判别号 5：有效规则哈希、规范化覆盖文本、服务端 Item→武器表）。客户端用**自己的 pack ＋ 服务端覆盖**重新推导有效规则，哈希不符则不启用武器（客户端日志 ERROR），相符则安装服务端的武器表（整合服务器同 JVM 时不替换）。
- **决定：Gameplay 清单不一致→拒绝（踢出，附明确消息）；仅 Presentation 不同→允许并记录。**
  理由：(1) 客户端的预测入口、位移预测（`speed/distance`）、半径/时窗判断与相机逻辑都按动作数据计算，数据不同会系统性地预测错，表现为持续纠正与伪迟滞，而不是“稍有偏差”；(2) 没有“无预测的降级客户端”这一模式，“降级”只能是带着已知错误继续，比明确拒绝更难排查；(3) Presentation 只改外观，不影响结果，允许才符合 `extension.md` §15.4 对两类清单的分工。
- 未呈报哈希的连接：`network.helloTimeoutTicks`（默认 400，约 20 秒）后踢出；未验证期间输入返回 `REJECT:UNVERIFIED`。验证按**连接**保存：重生与换维度换状态不换连接，验证随之保留（`CombatServer.restart`）——这是实现中被生命周期验证发现的设计缺口，§4 D-5。
- 信任模型：哈希是兼容性检查，不是防篡改。服务端对战斗数据始终权威，客户端无法借哈希改变服务端规则。

### 3.4 渲染解耦（任务 4）

- `KatanaRendering` → `WeaponRendering`（`git mv`，类内不再有 katana 字样）；`PresentationAssets` 在加载期按 `Resource` 读 `models.held/stowed/stowed_empty`、`texture`、`grip.offset`、`handGrips.right/left`、`mounts`，每个模型文件只加载一次并缓存，纹理在进入世界时统一绑定一次（`PresentationAssets.warm`）。
- 状态消息到达时（不在帧内）把 `appearance.skin`（＝资源 ID）、`appearance.animations`（＝动画 ID）与 `style` 解析成 `Visual.render / preset / catalog / definition / returnsToSheath`；每帧只跟随引用。渲染路径查玩家状态用 `IntHashMap`（`ClientProxy.visualOf`，无装箱），不再 `HashMap<Integer,…>.get`。
- **鞘位**：挂点读 Resource 的 `left_hip` 挂点（katana 数据为 X=0.58 Y=0.55 Z=0.18 Angle=45，`katana/resources.json`）；不再读取 `render.cfg` 的 `[mount]`（见 §9 待决）。**握点**：右手 `handGrips.right` 与 `grip.offset`。**左手握位**：从数据读入 `Render`，当前刚体适配器没有左手姿态可施加，因此**不消费**（§8）。
- **`stowed_empty` 的使用**：拔刀态（刀在手中）画 `stowed_empty`（`sheath_empty.obj`），收刀态画带柄的 `stowed`（`sheath.obj`）；katana 资源的 `stowed_empty` 由原先的 `sheath.obj` 改为 `sheath_empty.obj`（Presentation 数据变更，Gameplay 哈希不变）。没有 `left_hip` 挂点的资源不画鞘。`KatanaPlayerRenderer`（提交偏航的 `RenderPlayer` 子类）逻辑与武器无关，类名未改，只把玩家状态查找换成无装箱的 `visualOf`。
- **动画预设**：`animation.preset` 在加载期映射到预设对象（`rigid_arc` 保留，`sheatheVisibleTicks=6` 现属预设）；未知预设名在加载期 WARN 并回退 `rigid_arc`，不再在渲染时抛异常。`AnimationLookup`（core）不再被客户端使用，类与测试保留。

### 3.5 第二把武器（任务 5）

`src/test/resources/wf1test/glaive.json`（测试资源，不进发布 JAR）：武器 `wf1test:glaive`（伤害倍率 1.25）、风格 `wf1test:reach`（无收刀态，只有 ready；`bufferTicks` 5）、三个动作（`thrust`→`thrust_b`→`sweep`，时窗与伤害均不同于 katana）、动画 `wf1test:arc`、资源 `wf1test:glaive`（模型复用 katana 文件，右手握点高度 0.5，`mounts: []`）。**未写任何 Java**：装备靠 `combat.cfg` 的 `weapons.items` 把原版物品映射到武器 ID（会话内为 `minecraft:stick=wf1test:glaive`），pack 由 `dual.py --content-pack` 复制到服务端与两个客户端的配置目录。

### 3.6 生命周期（任务 6）与错误数据包（任务 7）

生命周期代码改动见 §4 D-4/D-5。错误数据包：`ContentRuntime` 对 `ContentValidationException` 逐条 ERROR 记录（每条含“文件: JSON 路径: 原因”），再抛 `IllegalStateException("Clashweave refuses to start: …")`，走 FML 的模组加载失败路径（§6.4）。

## 4 对已有层的缺陷与处理

| 编号 | 现象与证据 | 处理 | 测试 |
|---|---|---|---|
| D-1 | **内容层在真实构建中无法编译。** WF-1a/1b 的 128 项只在 Gson 2.13.2 下验证；Minecraft 1.7.10 自带 Gson 2.2.4，缺少公开的 `JsonObject.deepCopy/size/keySet`、`JsonParser.parseReader`、`JsonArray.set`。首次 `gradlew build` 输出 `compileJava FAILED … 12 errors`（`deepCopy() is not public in com.google.gson.JsonObject`） | 新增 `content/JsonCompat.java`（复制/键集/解析），`qualifyArray` 改为重建数组 | `ContentRegistryTest`、`ContentEntryTest` 现在随 Gradle 在 Gson 2.2.4 下执行并通过 |
| D-2 | 基线 `spotlessCheck` 失败（7 个文件的格式违规，含 `Scheduler.java`） | 只格式化提交 `a47cf01` | — |
| D-3 | `JsonParser` 恒宽松：`"actions": [,` 被解析成 `null` 元素，报成 `styles[0].actions[0]: null is not allowed` 等误导错误，没有“invalid JSON” | `JsonCompat.parse` 先用严格 `JsonReader` 通读一遍，失败报 `$: invalid JSON: … at line N column M` | `BadPackTest.invalidJsonIsReportedAtTheRoot` |
| D-4 | **死亡玩家（生命 0，原版直到重生才置 `isDead`）仍能开始动作、命中目标、保持 `engaged`。** `consume`/`collect` 只判 `isDead`；`keepInventory` 时武器仍在手。夹具 `life-1` 记录 `death-mid-action: action survived death: katana:light_1`，`life-2` 记录 `still engaged after death`（`hurtTime>0` 把死亡处理器的脱战又推翻） | `CombatServer.dead()`（`isDead \|\| getHealth()<=0`）用于输入拒绝（`REJECT:DEAD`）、命中采集与交战判定 | 生命周期夹具 `death-mid-action`（`life-3`、`life-4` 通过） |
| D-5 | 本轮自己引入后被验证发现：握手验证绑在 `PlayerState` 上，重生/换维度重建状态后验证丢失，客户端不会再发 Hello，输入将全部 `REJECT:UNVERIFIED` 并在 400 tick 后被踢 | `restart()` 在重建前读取并继承验证标志 | `respawn-new-state`、`dimension-change-mid-action`（`life-2`～`life-4` 通过） |
| D-6 | `Scheduler.tick` 在没有缓冲请求时每 tick 仍新建 `ArrayList` 并取迭代器 | 缓冲为空时只做“动作是否结束”并返回；行为不变 | `SchedulerTest`、`ContentEntryTest`（40 种子 × 400 tick 的旧/新目录逐 tick 等价）通过；`HotPathAllocationTest` 空闲 tick 0 字节 |
| D-7 | `ContentRegistry.catalog(styleId)` 用未限定的 ID 查索引：`catalog("default")` 会 `NullPointerException`（读源码发现） | 用 `style.id` | `WiringTest.catalogAcceptsShortStyleIdsLikeEveryOtherLookup` |
| D-8 | `ActionCatalog` 的 `"katana:"` 过渡前缀（WF-1b 已声明待接线处理） | §3.2 | `WiringTest.namespacedCatalogsCompleteShortIdsWithTheirOwnNamespace` |

另有一项**观察而未改动**：服务端在动作进行中把玩家传送走（`setPlayerLocation`），位移校验把它当作非法位移拉回并计一次纠正（§6.2 `teleport-mid-action`）。这与 P1-a 的“5 格非法位移被检出”是同一机制，无法区分外部传送与作弊，故不改；见 §9。

## 5 构建与单元测试

```powershell
$env:JAVA_HOME='C:\Program Files\Zulu\zulu-25'
$env:VERSION='0.1.0-dev'
.\gradlew.bat spotlessApply cleanTest spotlessCheck checkstyleMain checkstyleTest test build --no-configuration-cache --console=plain
.\gradlew.bat p0LaunchFiles -I tools/p0/validation.gradle --no-configuration-cache --console=plain
```

`BUILD SUCCESSFUL`。（与 P1-a 报告一致，`build` 不与验证 init 脚本合并运行，后者会检查旧 `p0Diagnostics` 源集的通配符 import。）`cleanTest` 之后 JUnit **实际执行 185 项，failure/error/skipped 均为 0**：基线 133 项；移除 `WeaponResolverTest` 3 项（被替换）；加入 WF-1a/1b 内容测试 26 项（`ContentRegistryTest` 20、`ContentEntryTest` 6，现于 Gson 2.2.4 下执行）；本轮新增 29 项：`WiringTest` 14、`BadPackTest` 7、`CombatWeaponsTest` 6、`HotPathAllocationTest` 2。`133 − 3 + 26 + 29 = 185`。

`tools/wf1/run-content-tests.sh`（WF-1a 的容器内脚本，路径为 Linux 容器）本轮未使用；Gradle 的 `test` 是权威口径，因为只有它使用真实的 Gson 2.2.4。

## 6 实机验证

### 6.1 第二把武器（任务 5，`w2-4`，通过）

| 检查 | 证据（服务端/客户端日志，逐字） | 判定 |
|---|---|---|
| 能装备、出招、风格与外观同步 | `WF1C_WEAPON2 start action=wf1test:thrust tick=381 style=wf1test:reach appearance=wf1test:glaive/wf1test:arc`；随后 `thrust_b`（tick 387）、`sweep`（tick 392） | 通过 |
| 动作图与时窗来自数据 | `thrust→thrust_b` 需要真实命中确认，链路在命中后进行（`hit action=wf1test:thrust damage=4.6015625 tick=385` 之后 `start action=wf1test:thrust_b tick=387`） | 通过 |
| 武器倍率生效 | thrust 伤害 4×1.25×0.92＝4.6015625，sweep 7×1.25×0.92＝8.046875（0.92＝僵尸 2 点天然护甲）；夹具按 `getTotalArmorValue` 计算期望并核对 | 通过 |
| 观察者客户端解析到新资源/预设/目录 | `WF1C_SEEN observer=P0B entity=0 style=wf1test:reach skin=wf1test:glaive animation=wf1test:arc resolvedRender=wf1test:glaive presetResolved=true catalogNamespace=wf1test`（P0A 的客户端同样） | 通过 |
| 渲染 | [w2-glaive-first](images/w2-glaive-first.png)、[w2-glaive-front](images/w2-glaive-front.png)、[w2-glaive-side](images/w2-glaive-side.png)、[w2-observer-glaive](images/w2-observer-glaive.png) | 观察（见 §8：模型复用 katana，不能证明另一套模型的渲染） |

夹具共 `WF1C_COMPLETE mode=weapon2 passed=2 failed=0`。**失败史（保留）**：`w2-1` 记录 `weapon2-actions-and-chain ok=false`、`weapon2-damage-multiplier ok=false`，原因是夹具没有在动作之间把攻击者拉回原点，根运动让它走到 z=19，目标不在范围内（`pos=0.0,64.0,19.1`）；`w2-2` 记录 `damage … expected thrust 5.0 sweep 8.75`，期望值漏算了僵尸护甲。两者都是夹具缺陷，已修；`w2-3`、`w2-4` 通过。

### 6.2 生命周期验收矩阵（任务 6，`life-4`，16/16 通过）

夹具（`Wf1cScenario`/`Wf1cReplay`）让 P0A 通过真实输入路径持续攻击一只固定的僵尸，服务端在**动作进行中**对 P0A 施加事件，再读回权威状态。每项“before/after”为逐字 `WF1C_CHECK` 摘要（全部在 `WF-1c-summary.json` 的 `lifecycle.life-4`）。

| 事件 | 预期规则 | 观察到的结果 | 判定 |
|---|---|---|---|
| 传送（动作中，服务端 `setPlayerLocation`） | 不踢出、不崩溃、战斗继续 | 会话不变，之后继续出招；**位移校验把传送到 (18, 64, 18) 的玩家拉回原点附近（核对时 z≈0.3）并计 1 次纠正**（`WF1C_NOTE teleport corrections 1->2 position 0.0,0.29999999999999993`） | 通过（不踢出/可继续）；纠正行为见 §4 末与 §9 |
| 打开 GUI（动作中，服务端 `displayGUIChest`） | 缓冲清空、脱离防御、关闭后恢复 | `gui-open-mid-action`：容器已开、防御未持有；`gui-close-resume`：关闭后新动作开始 | 通过 |
| 骑乘（动作中，`mountEntity` 猪） | 状态不重建、无位移纠正 | `riding=true corrections 2->2`；`dismount-resume`：下骑后继续出招 | 通过 |
| 扔掉武器（动作中，`dropOneItem`） | 命中停止、动作收尾 | 手持为空；动作在窗口内结束；放回武器后 `weapon-back-resume` 通过 | 通过 |
| 换刀（katana→glaive→katana，动作中） | 当前动作跑完才换风格；定义重发并确认；姿态按新风格 | `style=wf1test:reach … sentDef=true sheathed=false`；换回 `style=katana:default … sentDef=true` | 通过 |
| 死亡（动作中） | 动作取消、脱战、接触清空、死后输入拒绝 | `action=- … engaged=false contacts=0`；服务端日志出现 `result=REJECT:DEAD`（回放客户端在死亡画面期间仍发的输入，`life-4` 7 条） | 通过 |
| 重生 | 新会话、无继承动作、收刀态、无继承纠正、验证保留 | `respawn-new-state`：新 `session`、`action=-`、`sheathed=true`、`corrections=0`、`verified=true`；`respawn-combat-resumes` | 通过 |
| 换维度（动作中，`travelToDimension(1)` 再返回） | 不继承原维度的动作；无纠正；验证保留 | 新 `session`、`corrections=0`、`verified=true`，新维度内开始新动作；返回后亦换会话 | 通过 |
| 断线重连（动作中，服务端踢出） | 状态清除、待提交接触清除；重连为新会话并重新验证 | `player still listed` 不成立、`combat state survived` 不成立、无该攻击者的待提交接触；重连后新会话且 `verified=true`、开始新动作 | 通过 |

**失败史（保留，未择优）**：`life-1`（12/16，失败 4 项）：`death-mid-action: action survived death`（产品缺陷 D-4）；`respawn-new-state` 与 `reconnect-new-state` 的 `session reused`，是夹具把“本步开始时的会话”当作“事件前会话”来比较（夹具缺陷）；`disconnect-mid-action: player still listed`，是夹具在玩家断开期间不推进步骤，等到客户端重连后才核对，核对到的是新玩家（夹具缺陷）；`life-2`（15/16）：`death-mid-action: still engaged after death`（D-4 的第二半，已修）；`life-3`（15/16）：`reconnect-new-state: no action started after reconnecting`——证据：服务端日志中 `WF1C_CHECK step=reconnect-new-state ok=false` 先于重连后的第一次 `CW START player=P0A id=1 action=katana:iai tick=1685`（同为 18:20:57）；回放客户端在入世后等待 60 个客户端 tick 才开始攻击，而该步只等 60 tick。产品行为没有问题，夹具余量不足；将该步等待改为 160 tick 后得到 `life-4`。`life-4` 不替换前三者。

### 6.3 浮空期间的死亡、换维度、断线（P1-a 报告 §“保留的失败与未验证项” 第 5 项，通过）

夹具以注入的 launcher 接触（P1-a 夹具同一技术）把 P0B 打上天，待其上升 6 tick 后施加事件，再核对新状态并**再次浮空**。判据：新会话、无动作、无纠正、无继承的发射额度/位移预算，且第二次浮空被完整接受且预算为满。

| 会话 | 事件 | 新状态检查 | 再次浮空 | `CW_EFFECT LAUNCH …`（两次，逐字） | 判定 |
|---|---|---|---|---|---|
| `float-death-2` | 浮空中死亡（`attackEntityFrom` outOfWorld） | 通过 | 通过 | `accepted=true … remaining=2`；再次 `accepted=true … remaining=2` | 通过 |
| `float-dimension-2` | 浮空中 `travelToDimension(1)`，之后返回 | 通过 | 通过 | `remaining=2`；`remaining=2` | 通过 |
| `float-disconnect-2` | 浮空中被服务端踢出，客户端自动重连 | 通过 | 通过（目标实体 ID 已变） | `remaining=2`；`remaining=2` | 通过 |

“`remaining=2` 两次”是预算被清理的直接证据：若旧记录残留，第二次应为 `remaining=1`。同一批次的首版 `float-*-1`（`cand-3`）同样通过，保留。

### 6.4 错误数据包（任务 7，通过）

`tools/wf1/bad_pack_start.py` 对真实专用服务器、候选 JAR，各启动一次，每次把一份有一处缺陷的 pack 放入 `config/clashweave/content/wf1test/glaive.json`（均为 `src/test/resources/wf1test/glaive.json` 的单点改动）。六个案例均在约 4 秒内以 FML 的模组加载失败路径结束：写出 `crash-reports/crash-….txt`、**未进入 `Done`**、不挂起、进程不继续运行；每条错误含**文件与 JSON 路径**，随后一条明确的拒绝启动信息。

| 案例 | 日志中的错误（逐字，去掉时间戳前缀） |
|---|---|
| 缺字段 | `config/clashweave/content/wf1test/glaive.json: actions[0].damage: missing field` |
| 未知动作 | `config/clashweave/content/wf1test/glaive.json: styles[0].actions[3]: unknown action "wf1test:ghost"` |
| 拼错 entry 字段 | `…: styles[0].entry.reedy: unknown field`、`…: styles[0].entry.ready: missing field` |
| 重复 ID | `…: actions[1].id: duplicate ID "wf1test:thrust"`（并带出两处派生的未知引用） |
| 非法 JSON | `…: $: invalid JSON: Use JsonReader.setLenient(true) to accept malformed JSON at line 20 column 20` |
| 未知边目标 | `…: actions[0].edges[1].target: unknown action "wf1test:nowhere"` 与 `…: styles[0].actions[0]: wf1test:thrust: edge target outside style graph "wf1test:nowhere"` |

拒绝启动信息（逐字，缺字段案例）：`Clashweave refuses to start: content validation failed with 1 error(s); the first is "config/clashweave/content/wf1test/glaive.json: actions[0].damage: missing field". All errors are logged above.` 同一缺陷类别另有 7 项 JUnit（`BadPackTest`），并验证“同一文件的多个错误一并报告”。注意：服务端进程退出码是 0（FML 的既有行为），判断拒绝启动要看日志与崩溃报告，不看退出码。脚本首次产出的 JSON 把 `crash_report` 记为 false，是检测字符串写错（FML 写的是 `This crash report has been saved to`，不是控制台横幅）；日志保留，已修脚本并用 `--reparse` 从留存日志重算（`build/wf1c/bad-b1-reparsed.json`），没有重跑。

### 6.5 握手（任务 3，通过）

| 会话 | 设置 | 结果（逐字） |
|---|---|---|
| `hs-presonly` | P0B 的配置目录多一个只含动画的 pack（Presentation 哈希不同、Gameplay 不变） | 服务端：`Clashweave: P0B has different presentation content only (server 7bef4ba2…, client f975d6dc…); allowed`；两客户端 `joined with negotiated rules 2314f71c…`；连击 20/20、S08 0/0 |
| `hs-gamediff` | 两个客户端都多一个含武器/风格/动作的 pack（Gameplay 哈希不同） | 服务端：`Clashweave: refusing P0A: gameplay content differs (gameplay server 518dafda… client a815dc1f…; presentation …)`；客户端被踢，消息 `Clashweave: gameplay content differs. Install the same content packs as the server (server gameplay 518dafda0b4e, yours a815dc1f1083).`；结束时 `WF1C_HANDSHAKE connected=0 withState=0 verified=0` |
| `legacy-1` | 服务端 `content.legacyKatanaJson=true` | 两客户端从服务端覆盖推导出同一有效哈希 `7e9679e8…` 并加入；连击 20/20、S08 0/0 |

### 6.6 双客户端回归，`--souls-view`，0/100 ms（验收，通过）

三次重复，基线与候选交替（每轮顺序 base、ctrl、cand、base-100、cand-100），共 15 个会话，**全部**：连击 **20/20**（P0A），区间 S08 **0**/0，包引起视角变化 yaw/pitch **0°**/0°，位移纠正 0，服务端退出 0，专服客户端/OpenGL 类加载检查 0。数据与逐会话值在 `WF-1c-summary.json` 的 `ab.perf`。整场输入门拒绝（`REJECT:STALE/RATE`）在 0 ms 为 0～2，在 100 ms 为 2～32，**基线同样出现**（`ab-base-100-3`：STALE 17、RATE 8），不是候选独有。

### 6.7 P1-a 玩家浮空回归（验收，**严格口径未满足，是否回归尚未查明**）

`--launcher player`，每会话 20 个玩家浮空案例；“接受”＝该案例至少一次 `CW_EFFECT LAUNCH … accepted=true`。S08 与包视角变化在全部会话均为 0/0°。

| 会话 | JAR | RTT | 接受/20 | `CONDITION_CHANGED` 次数 | 备注 |
|---|---|---:|---:|---:|---|
| `p1f-base-0` | base | 0 | 20 | 0 | |
| `p1f-cand-0` | cand | 0 | **20** | 0 | |
| `p1f-base-100` | base | 100 | 20 | 0 | |
| `p1f-cand-100` | cand | 100 | **19** | 1 | **不满足 20/20 判据**，原运行保留 |
| `p1f-base-100-r2` | base | 100 | 20 | 0 | 诊断重复 |
| `p1f-cand-100-r2` | cand | 100 | 20 | 0 | 诊断重复 |
| `p1f-base-100-r3` | base | 100 | **15** | 5 | 基线同样退化 |
| `p1f-cand-100-r3` | cand | 100 | **13** | 7 | |
| `p1f-base-100-r4` | base | 100 | 20 | 0 | 诊断重复 |
| `p1f-cand-100-r4` | cand | 100 | 20 | 0 | 诊断重复 |

`p1f-cand-100` 的未接受案例（case 3）：服务端日志 `CW ACK player=P0A seq=50 result=CONDITION_CHANGED`——HEAVY→launcher 边要求 `GROUND`，请求在 tick 907 被消费时服务端的 `P0A.onGround` 为 false。基线在 `p1f-base-100-r3` 出现同一签名且更严重（5 次、15/20）。诊断重复（r2～r4，基线与候选交替）是**在首个失败之后追加**的，不替代首个结果：5 次 100 ms 会话中基线接受数为 20/20/15/20/20，候选为 19/20/13/20/20，退化在基线和候选中均出现；这不能证明与接线无关。**未查明**触发会话级退化的原因（客户端的 `onGround` 标志与 100 ms 延迟下的位置包时序；r3 期间主机上同时有未受控的短时任务，不能排除）。该项如实记为：严格口径（0/100 ms 各 20/20）**未满足**，根因与因果归属尚未确定，需要审核裁决。进一步复核见 §10。

### 6.8 性能

**方法。**服务端：`NetworkScenario` 现有的 `P0_PERF`（每 tick 战斗逻辑耗时累计的均值/最大值，1800 样本），`--souls-view`，0 ms 与 100 ms 各三次；JAR 交替。客户端：`RenderProbe`（`tools/p0/java/.../validation/RenderProbe.java`）在 Forge 渲染事件 `RenderPlayerEvent.Specials.Post`、`RenderHandEvent`、`RenderWorldLastEvent` 上各挂一个 `HIGHEST` 与一个 `LOWEST` 处理器（`receiveCanceled=true`）夹住所有其他处理器，测其间的 CPU 墙钟耗时；`frame` 为 `RenderTickEvent` START→END。夹具不依赖被测 JAR，同一份用于 `base`、`ctrl`、`cand`。时间是**提交绘制的 CPU 时间**（OBJ 以立即模式绘制，受 CPU 限制），不是 GPU 时间；客户端 `maxFps:60`，所以不用帧间隔作指标。分类取自该玩家的权威状态：`sheathed-idle`、`draw`（`iai`）、`drawn-idle`、`combo`。

**服务端战斗逻辑（ms，`P0_PERF` 末值，三次）：**

| RTT | JAR | 样本 | 均值（三次） | 均值中位 | 最大值（三次） | 最大值中位 |
|---|---|---|---|---:|---|---:|
| 0 ms | 基线 `743ae6f` | 1800×3 | 0.0965 / 0.0993 / 0.1019 | 0.0993 | 3.87 / 3.47 / 4.17 | 3.87 |
| 0 ms | 美术对照 `9ab1aef` | 1800×3 | 0.1083 / 0.0944 / 0.0967 | 0.0967 | 4.38 / 2.77 / 4.36 | 4.36 |
| 0 ms | 候选 | 1800×3 | 0.1068 / 0.1000 / 0.0976 | 0.1000 | 4.63 / 3.84 / 4.31 | 4.31 |
| 100 ms | 基线 | 1800×3 | 0.1045 / 0.1055 / 0.0920 | 0.1045 | 3.99 / 4.67 / 5.21 | 4.67 |
| 100 ms | 候选 | 1800×3 | 0.0944 / 0.1078 / 0.0916 | 0.0944 | 4.92 / 4.96 / 3.80 | 4.92 |

- 均值：候选区间与基线区间重叠，本批无法区分噪声与微小劣化；区间重叠不能证明性能等效。
- **最大值是否上升：** 0 ms 中位 3.87→4.31 ms、100 ms 中位 4.67→4.92 ms，区间重叠。解释依据：(1) 服务端逻辑与基线相同的 `ctrl`（只含美术与 Gson 修复）本身的最大值中位为 4.36 ms，高于基线、与候选相当，提示会话间波动可能解释该量级，但不是因果证明；(2) 每个会话最终最大值首次出现的采样窗口（`P0_PERF` 每 100 样本输出一次）：基线 0 ms 为 200/200/200，基线 100 ms 为 200/700/400，`ctrl` 为 500/200/600，候选 0 ms 为 200/200/400，候选 100 ms 为 200/200/200——集中在第 100～200 个样本（首轮战斗的预热与类加载），其余出现在 400～700。**没有证据表明最大值的上升来自本改动**，但也没有逐 tick 分布可以排除；P0_PERF 只有累计最大值。
- 热路径新增/删除项：每 tick 的武器解析从“校验＋两次字符串比较＋`new double[]`”变为一次 `IdentityHashMap` 查找；`Scheduler` 空闲 tick 不再分配；`snapshot` 只在需要时取载荷字符串。

**热路径分配（`HotPathAllocationTest`，`ThreadMXBean.getThreadAllocatedBytes`，每项 100 万次调用，实测）：**

| 调用 | 每百万次分配字节 |
|---|---:|
| `CombatWeapons.entry` / `armed` | 0 / 0 |
| `ActionCatalog.byId` / `require` | 0 / 0 |
| `ActionCatalog.entryTarget` / `entersSheathed` / `normalize`（已限定 ID） | 0 / 0 / 0 |
| `Scheduler.tick`（空闲，无缓冲） | 0 |

**客户端渲染（µs，CPU 提交时间）。**0 ms `--souls-view`，两客户端合并；每格为“各会话均值的中位 [最小–最大] / 各会话 p95 的中位”。基线 3 个会话；`ctrl` 与 `cand` 各 6 个会话（前 3 个在主批次，后 3 个为补充批次 `render`，顺序反转为 cand、ctrl 交替，以抵消顺序/漂移）。

| 阶段/分类 | 基线（3 会话）均值中位 [范围] / p95 | 美术对照（6 会话） | 候选（6 会话） | 候选/对照 |
|---|---|---|---|---:|
| specials/sheathed-idle | 35.5 [35.4–39.0] / 18.5 | 101.9 [95.2–107.4] / 166.1 | 104.1 [95.6–109.2] / 170.4 | 1.02 |
| specials/draw | 68.4 [63.7–73.2] / 87.7 | 103.6 [97.7–113.1] / 132.9 | 93.8 [92.3–99.2] / 119.3 | 0.91 |
| specials/drawn-idle | 60.8 [59.5–66.7] / 75.4 | 99.3 [96.0–104.4] / 126.3 | 96.6 [86.9–111.9] / 122.8 | 0.97 |
| specials/combo | 71.1 [70.2–74.0] / 86.5 | 106.8 [100.7–116.1] / 138.4 | 100.7 [97.9–111.5] / 133.0 | 0.94 |
| waist/sheathed-idle | 9.4 [8.9–9.7] / 11.5 | 69.2 [66.7–70.8] / 83.8 | 69.0 [62.1–75.0] / 86.2 | 1.00 |
| hand/sheathed-idle | 2.2 [2.0–2.3] / 3.6 | 2.1 [2.1–2.2] / 3.2 | 2.1 [1.9–2.4] / 3.2 | 1.00 |
| frame/sheathed-idle | 348.7 [329.5–357.9] / 914.7 | 397.2 [388.1–405.0] / 936.1 | 406.4 [386.5–433.8] / 963.4 | 1.02 |
| frame/draw | 661.7 [609.6–778.5] / 749.5 | 753.0 [640.3–899.8] / 808.8 | 628.1 [618.6–798.9] / 759.8 | 0.83 |
| frame/drawn-idle | 522.1 [511.5–543.8] / 694.3 | 563.3 [551.1–587.1] / 715.9 | 568.9 [535.2–643.0] / 745.3 | 1.01 |
| frame/combo | 607.5 [587.5–620.0] / 792.3 | 642.5 [617.2–687.3] / 803.2 | 638.2 [616.6–700.0] / 796.9 | 0.99 |

- **接线的影响（候选/对照）：**各分类的比值在 0.83～1.02；`specials`（玩家身上的武器绘制）在拔刀、连击中略低（0.91、0.94、0.97），符合预期——拔刀态用 380 面的空鞘代替 556 面的带柄鞘；收刀态、`waist`、`hand` 在 1.00～1.02。`frame` 的整帧值在 0.99～1.02，只有 `draw`（约 24 帧/会话，样本少）为 0.83。本批接线对照未见一致的渲染耗时上升；不能仅凭区间重叠证明等效。
- 美术模型本身的代价：基线→美术对照，`specials/sheathed-idle` 35.5→101.9 µs、`waist` 9.4→69.2 µs（鞘从占位模型变为 556 面带柄模型），这是资源变化，不属于本改动。
- 主批次前 3 个候选会话的 `frame/drawn-idle` 偏高（578～643 µs），补充的 3 个为 535～559 µs，6 会话中位 568.9 对 563.3；会话间漂移（同一 JAR 内范围约 100 µs）大于两 JAR 的差。服务端战斗逻辑在这 6+6 个会话中：对照均值 0.0943～0.1083 ms、最大 2.77～5.07 ms，候选均值 0.0949～0.1068 ms、最大 3.84～5.17 ms，全部 20/20、S08=0。

**加载器耗时（只在启动与握手时运行）：**

| 项 | 次数 | 最小 / 中位 / 最大 |
|---|---:|---|
| 服务端 registry 加载＋校验（5 文件） | 15 个会话 | 30.9 / 34.2 / 37.4 ms |
| 服务端加上配置覆盖（每风格目录、哈希） | 15 | 42.9 / 45.8 / 49.1 ms |
| 客户端 registry 加载＋校验（5 文件） | 27 | 30.5 / 34.2 / 48.0 ms |
| 客户端加入时按服务端覆盖推导有效规则 | 24 | 1.5 / 1.9 / 3.4 ms |

服务端从首条带时间戳的日志行到 `Done` 为 4～5 秒（15 个会话），加载器约占 1%。冷启动含类加载；`WiringTest` 在已预热的 JVM 中读 6 个文件为 2.2 ms（`WF1C_LOAD`）。

### 6.9 截图（拔刀、收刀、第一/第三人称；与 C 组挂点对照）

会话 `art-cand`（候选）与 `art-ctrl`（美术对照），同一 `--art-verify` 夹具，原始游戏截图 960×540。收刀态与 C 组参考图（`docs/art/sheath-mount-sweep/C-*.png`，同夹具、`bfeae81` 模型、X=0.58）逐像素比较（`tools/wf1/ImageCompare.java`；差异像素＝任一通道差 > 24）：

| 取景（收刀） | 候选 vs C 组：均值绝对差 / 差异像素 | 对照 JAR vs C 组：均值绝对差 / 差异像素 | 候选 vs 对照 JAR：差异像素 | 图 |
|---|---|---|---|---|
| 正面 | 0.619 / 0.1030% | 0.811 / 0.0947% | 0.0110% | [cmp](images/cmp-sheathed-front-C-vs-cand.png) |
| 侧面 | 0.621 / 0.0372% | 0.881 / 0.0368% | 0.0010% | [cmp](images/cmp-sheathed-side-C-vs-cand.png) |
| 背面 | 0.678 / 0.1154% | 0.895 / 0.1107% | 0.0066% | [cmp](images/cmp-sheathed-back-C-vs-cand.png) |
| 鞘口近景 | 0.702 / 0.2585% | 0.817 / 0.2558% | 0.0100% | [cmp](images/cmp-sheathed-detail-C-vs-cand.png) |
| 蹲下 | 0.549 / 0.0559% | 0.821 / 0.0534% | 0.0029% | [cmp](images/cmp-sheathed-crouch-C-vs-cand.png) |
| 跑步 | 0.953 / 0.6038% | 0.727 / 0.0511% | 0.6215% | [cmp](images/cmp-sheathed-run-C-vs-cand.png) |

- **挂点结论：**候选与 C 组、候选与旧接线（代码内默认 X=0.58）的收刀态在五个静止取景下几乎逐像素一致（差异像素 ≤ 0.26%，且候选与旧接线之间 ≤ 0.011%），数据里的 X=0.58 Y=0.55 Z=0.18 Angle=45 与 C 组挂点相符。**跑步**取景的 0.60% 来自跑动相位不同（真实按键、非固定相位），不能当作挂点偏差，也不能当作挂点一致的证据。差异图里的红点是人物轮廓的 1 像素边缘（相机位置的亚像素差）。
- **`stowed_empty` 的使用（拔刀态）：**候选在拔刀态的鞘不带柄；旧接线在拔刀态仍画带柄的鞘，于是手里有刀、腰间鞘口还露出一截缠柄（重复的柄）。对比图 [cmp-drawn-detail](images/cmp-drawn-detail-ctrl-vs-cand.png)：左为旧接线（鞘口露出菱形缠柄），右为候选（空鞘，差异图的红色区域正是那段柄）。另见 [cmp-drawn-third](images/cmp-drawn-third-ctrl-vs-cand.png)、[cmp-drawn-side](images/cmp-drawn-side-ctrl-vs-cand.png)、[cmp-drawn-first](images/cmp-drawn-first-ctrl-vs-cand.png)（第一人称几乎相同，因为腰挂鞘不在视野内）。收刀态使用带柄的 `stowed`：[cand-sheathed-detail](images/cand-sheathed-detail.png)。**`stowed_empty` 在拔刀态被正确使用。**
- 第一人称拔刀 [cand-drawn-first](images/cand-drawn-first.png)、第三人称拔刀 [cand-drawn-third](images/cand-drawn-third.png)、挥击 [cand-swing-first](images/cand-swing-first.png)、观察者视角 [cand-observer-drawn](images/cand-observer-drawn.png) 与 [cand-observer-sheathed](images/cand-observer-sheathed.png)。

## 7 失败与保留（汇总）

| 项 | 内容 | 状态 |
|---|---|---|
| `p1f-cand-100` | 19/20；`CONDITION_CHANGED`；同批基线在另一会话同样退化（§6.7） | 严格口径未满足，原因未查明，见 §9 |
| `w2-1`、`w2-2` | 夹具缺陷（攻击者走出范围、期望值漏算护甲） | 已修，保留 |
| `life-1`、`life-2` | D-4（已修）与夹具缺陷 | 保留 |
| `life-3` | `reconnect-new-state`：夹具等待不足 | 保留，`life-4` 为修后补充 |
| 错误数据包脚本首轮 JSON | `crash_report` 检测字符串错误 | 日志保留，重算 |
| Spotless、Gson 编译失败 | 既有层缺陷 | 已修，§4 |

## 8 未验证项

- **左手握位**数据已读入但未被渲染消费（刚体适配器没有左手姿态）；未验证双手持握的视觉。
- **第二把武器复用 katana 的模型与贴图**，只证明了数据驱动的资源 ID/握点/预设分派；不同网格的另一套模型、不同挂点位置（`mounts` 非空且位置不同）未验证。
- 收刀鞘仅验证站立、蹲下、跑步；盔甲外层、其他姿态未验证（与 `SHEATH-MOUNT-SWEEP.md` 相同的限制）。
- 骑乘只用一只静止的猪；真实马、会移动的坐骑、船未验证。GUI 只验证了服务端打开的容器；玩家按键打开背包、`Esc` 菜单未验证。
- 传送只验证了“服务端 `setPlayerLocation`”；末影珍珠、传送模组未验证。
- `REJECT:UNVERIFIED` 与 400 tick 超时踢出的代码路径未在实机触发（所有会话中 Hello 都先于第一条输入到达，`REJECT:UNVERIFIED` 出现次数为 0）。
- 整合服务器（单人世界）路径只有代码保护（同 JVM 不替换武器表），**未运行**。
- 延迟只测了 0 与 100 ms；低 TPS、更高延迟、长时间稳定性未验证。
- 服务端 `P0_PERF` 只有累计最大值，没有逐 tick 分布；`RenderProbe` 度量的是 CPU 提交时间，GPU 时间未测。
- 只有一个内置 pack 与一个测试 pack；pack 数量/大小很大时的加载耗时未验证。
- Windows 之外的平台、Linux 专服未运行。
- `hs-gamediff` 的客户端侧只验证了服务端日志与踢出消息；客户端断线画面的文案未截图。

## 9 待审核决定

1. **握手策略**：Gameplay 不同＝拒绝、Presentation 不同＝允许（§3.3）。如需“降级”（例如只读观战），需要先有无预测的客户端模式。
2. **`render.cfg` 的 `[mount]` 覆盖已移除**：挂点以 Resource 数据为准；美术调试需要新的数据覆盖手段时另行设计。
3. **`content.legacyKatanaJson` 回退开关**保留一个版本，之后删除开关与 `combat/katana.json`。
4. **服务端传送被位移校验拉回**（§4 末）：是否需要区分“服务端发起的传送”（例如在传送前取消动作并重置预算）。本轮未改。

5. **`p1f-cand-100` 的 19/20**：是否要求先查明 100 ms 下攻击者 `onGround` 的来源。
6. **`Hello` 超时**默认 400 tick；以及 `weapons.items` 默认只含 katana，其余映射由服务器管理员配置。


## 10 沿用现有成果后的复核（未推送）

按用户确认沿用 `c698ac2` 的独立 worktree，不重做 WF-1a/1b、既有 Forge 接线或美术合并。远端美术分支仍为 `c0bf83e`，已包含于 `70df9cc`；当前 `origin/work` 仍为指定基线 `743ae6f`。原报告、失败批次和截图保留。复核不修改 `docs/design/`，不进入 C2a/P1-b。

### 10.1 接线小修与测试

- 观察者未收到目标 Visual 时，原 `WeaponRendering.render` 从 `minecraft.thePlayer.getHeldItem()` 回退，实际取观察者自己的物品；改为传入被渲染实体的 ItemStack。已收到 Visual 则仍优先使用权威缓存 Render，不因换物品打断当前承诺动作外观。
- `ClientProxy` 原已在第一个客户端 tick 预热全部纹理；删除 `RenderHandEvent` 内第二次预热及其独立 warmed 标志。帧内不再执行这份重复的资源遍历，没有新增每帧解析、拼 key 或加载。
- 新增查找/空物品/百万次分配三项 JUnit；该阶段完整检查和 build 实际 **188 项通过，failure/error/skipped=0**。新增查找的百万次分配为 **888 字节**，不是 0；低于既定 64 KiB 测试阈值，不声称绝对无分配。
- 尝试在无图形 JUnit 运行时直接构造 WeaponRendering 的第四项测试失败：`NoClassDefFoundError: org/lwjgl/LWJGLException`。这项新测试移到真实客户端的第二武器夹具，未删除既有 185 项测试，未引入 OpenGL 测试依赖。失败阶段为 189 项/1 failure；该阶段完整测试恢复到 188 项/0 failure。`rev-w2` 的真实调用探针因使用 `getField` 读取非公开字段失败（`NoSuchFieldException: render`），武器两项服务端断言通过。随后只修夹具为 `getDeclaredField`，另用新标签 `rev2-w2`；两项服务端断言通过，P0B 的 `WF1C_FALLBACK ok=true observer=P0B local=katana:default target=wf1test:glaive` 确认真实方法的两条分支均正确。旧失败不覆盖。
- 冻结 `cand-review.jar` SHA-256：`525ac208d5e35c503ef9fc152c1749e421abb050c0ef9532feebf0df526064b3`。与 `cand-4.jar` 逐项 ZIP 比较，仅 `WeaponRendering.class` 的内容不同；其它生产类和资源字节相同。

- 旧 JSON 回退目录复现了短 ID 进入权威状态的缺陷：新增 `legacyProductionCatalogAndWireUseFullyQualifiedActionIds` 先失败，随后仅在回退目录构建时规范化 action.id / edge.target。数值、入口与核心 Scheduler/ContentRegistry 的语义不变，旧短 ID 别名仍保留。`rev2-legacy` 实际连击 20/20、S08=0、包视角变化=0，`CW_ALIGN sameAction` 均为 true；默认目录的有效哈希不变。
- 规则协商的拒绝策略补齐：旧客户端在有效规则哈希不符时只清空武器表，仍在线。`rev2-sync-red` 给两名已正常协商的客户端发送错误有效哈希，50 tick 后在线数 2，拒绝断言失败。修复后 `rev3-sync` 同路径在线数 0、断言通过。派生规则或物品表构建异常也走同一断开函数；成功时才安装派生规则。不新增协议字段。
- 最终完整命令：`$env:JAVA_HOME='C:\Program Files\Zulu\zulu-25'; $env:VERSION='0.1.0-dev'; .\gradlew.bat spotlessApply cleanTest spotlessCheck checkstyleMain checkstyleTest test build --no-configuration-cache --console=plain`，输出 `BUILD SUCCESSFUL`，**189 项，failures/errors/skipped 均为 0**。相比已有 185 项新增 4 项（3 项渲染查找＋1 项旧目录规范化）；不是把 133 与 128 两个重叠集合直接相加。
- 最终冻结 `cand-rechecked.jar` SHA-256：`f79eaf3af7d2c71ef804758d6d0afa9571b4fa4ef90fd69415f36eae2738c3ae`。与 review 阶段相比只涉及 `ClientProxy` 的冷协商路径和 `EffectiveRules` 的冷回退路径；编译器生成的 `ClientProxy$CombatMovementInput` 访问器字节也变化，但其 Java 源码没有修改。动作、预算、格挡、浮空、相机生产源码均未修改。

### 10.2 浮空复核与诊断（失败保留）

新旧真实专服与双客户端各 0/100 ms 一场；100 ms 打开验证夹具的审计开关，只读取原状态，不改参数、发送时刻、伤害或位置。未调参数或重试择优。

| 会话 | LAUNCH/20 | 预算纠正 | 区间 S08 A/B | 包 yaw/pitch 最大变化 |
|---|---:|---:|---:|---:|
| rev-p1-base-0 | 20 | 0 | 0/0 | 0° |
| rev-p1-review-0 | 20 | 0 | 0/0 | 0° |
| rev-p1-base-100 | **19** | 0 | 0/0 | 0° |
| rev-p1-review-100 | **18** | **1** | 0/**1** | 0° |

结论：**本轮仍未满足 100 ms 玩家浮空验收**，不能由其它会话的成功推翻这四行。

拒绝样本（候选 case3/4）：请求消费前后 A 的脚点为 Y=64、health=200、hurtTime=0，客户端请求时与服务端消费时 onGround 均为 false；原始 request 的客户端动作年龄约为 6.00017 / 6.01040。对照原版 Entity.moveEntity：`onGround = d7 != y && d7 < 0.0D`，而 P0 客户端根位移调用为 `moveEntity(dx, 0, dz)`，纯水平调用会清空此标志，即使角色仍站在地面。这是已有客户端位移的明确副作用；尚未在本轮修改其行为。不能把这些样本描述成已证明“真正没落地”，也不能据基线出现同签名直接判定本改动无回归。

候选 case1 的 S08：tick671–673，B 的服务端位置从 Y=64.365946 进到 67.223500，2.4 格 launch 余额耗尽；新预算就绪时 allowance=0。之后累积上升约 1.25403 格，超过原 1.25 格跳跃额度，tick679 纠正。这里只证明余额和位置的时序，**尚未证明额外上升的根因**；不得通过放大全局余量掩盖它。

用户任务限定“只接线”。是否在本轮进一步修复已有位移副作用，已请求范围确认；未经确认不扩展物理/授权规则。其它预定的性能、第二武器、截图和生命周期验证已完成，结果见后续各节；没有修改物理副作用或增加额度。

### 10.3 全部交替性能／连击复核

同机、每个子场景新启动专服和双客户端，同一验证夹具。基线 `base.jar` 对 review 阶段冻结包；每场 1800 个 `P0_PERF` 样本，均值/最大为 ms。此批先运行，之后只补了冷回退目录与协商拒绝；不把它冒称为最终冻结包的三次性能测试。最终冻结包另做正常连接/连击回归，结果见下文。

| 标签 | 完成/20 | 战斗均值 ms | 最大 ms | S08 A/B | 包角度变化 |
|---|---:|---:|---:|---:|---:|
| rev-ab-base-0-1 | 20 | 0.171224 | 6.0684 | 0/0 | 0° |
| rev-ab-review-0-1 | 20 | 0.178724 | 7.5101 | 0/0 | 0° |
| rev-ab-base-100-1 | 20 | 0.247932 | 26.8637 | 0/0 | 0° |
| rev-ab-review-100-1 | 20 | 0.177961 | 8.1531 | 0/0 | 0° |
| rev-ab-base-0-2 | 20 | 0.168435 | 5.2167 | 0/0 | 0° |
| rev-ab-review-0-2 | **19** | 0.236353 | 35.1746 | 0/0 | 0° |
| rev-ab-base-100-2 | **19** | 0.178442 | 5.1782 | 0/0 | 0° |
| rev-ab-review-100-2 | 20 | 0.204388 | 29.3126 | 0/0 | 0° |
| rev-ab-base-0-3 | **19** | 0.301522 | 177.3919 | 0/0 | 0° |
| rev-ab-review-0-3 | 20 | 0.227865 | 30.3663 | 0/0 | 0° |
| rev-ab-base-100-3 | 20 | 0.252480 | 44.9662 | 0/0 | 0° |
| rev-ab-review-100-3 | 20 | 0.199146 | 13.3506 | 0/0 | 0° |

失败序列原文摘要：review-0-2 的 sequence13、base-100-2 的 sequence16、base-0-3 的 sequence10 均为 `NO_DERIVATIVE_REQUEST`。这是夹具记录的结果，不是已查明的生产根因。没有换标签挑选全绿数据覆盖。

0 ms 均值范围：base 0.168435–0.301522，review 0.178724–0.236353；最大范围：base 5.2167–177.3919，review 7.5101–35.1746。100 ms 均值范围：base 0.178442–0.252480，review 0.177961–0.204388；最大范围：base 5.1782–44.9662，review 8.1531–29.3126。最大值有会话间明显波动，不能以单次最大值判断接线的因果效果。**严格的每场 20/20 不满足，性能等效亦未证明。**客户端 CPU 绘制分类的完整逐场汇总见新增复核 JSON，测量方式与 §6.8 一致；没有 GPU 时间数据。

### 10.4 生命周期、第二武器与渲染证据补缺

`rev-life` 服务端夹具 16/16；`rev-float-death`、`rev-float-dimension`、`rev-float-disconnect` 各 2/2。这些只证明夹具列出的状态清理／恢复断言，不扩大到未检查的玩法保证。特别是 `teleport-mid-action` 从 corrections=0 变为 1；传送保留目的地这项行为未满足，仍列为已有预算限制，不静默修改。专服客户端/OpenGL 类加载均为 0。

纯数据第二武器 `rev2-w2` 2/2，但该会话有 **1 次预算纠正**，夹具未采集 S08 区间／包角度数据（摘要为 null，不是 0）；这些断言不能替代第二武器的移动回归。权威动作仍为 `wf1test:*`，不同皮肤 ID 与动作图，没有为测试武器写 Java。更正的实际渲染回退探针通过；以前的 `rev-w2` 探针失败仍保留在摘要。

本轮实际截图 `rev2-art` 使用相同资源／挂点，未调整模型或相机生产参数：

| 状态／视角 | 截图 | 判读 |
|---|---|---|
| 收刀／第一人称 | [截图](images/recheck-sheathed-first.png) | view=0、pitch=65°，向下看到左腰露出的柄和刀镡，未在手中画第二把刀；视野只覆盖局部鞘，不能据此证明鞘尾位置 |
| 收刀／第三人称正面 | [截图](images/recheck-sheathed-front.png) | 使用带柄 stowed；挂点仍来自数据 C 组，未退回 render.cfg |
| 拔刀／第一人称 | [截图](images/recheck-drawn-first.png) | 掌内柄适配和刀镡可见，沿用已审核的刚体动画 |
| 拔刀／第三人称 | [截图](images/recheck-drawn-third.png) | held 在手、腰间 stowed_empty；模型使用区别还由加载日志及缓存资源路径验证，不能仅凭一帧证明所有动作期都正确 |

原侧面／背面／蹲跑证据仍见 §6.5；本轮没有改几何、坐标或挂点。真实渲染加载错误检查与最终正常连接结果列于 [WF-1c-recheck-summary.json](WF-1c-recheck-summary.json)，包含所有批次与必要关键摘录，原始进程日志不入库。

### 10.5 最终冻结包的连接与回归

全部子场景使用最终 `cand-rechecked.jar`，新进程／新标签，未重跑覆盖。`rev3-presonly` 是补齐握手小修后的正常兼容性验证，不用于覆盖其它性能会话。

| 标签 | 场景 | 完成 | 预算纠正 | S08 A/B | 包 yaw/pitch 最大变化 |
|---|---|---:|---:|---:|---:|
| rev3-view-0 | 0 ms 连击 | 20/20 | 0 | 0/0 | 0°/0° |
| rev3-view-100 | 100 ms 连击 | 20/20 | 0 | 0/0 | 0°/0° |
| rev3-presonly | P0B 仅 Presentation 不同，允许 | 连击 20/20 | 0 | 0/0 | 0°/0° |
| rev3-p1-0 | 0 ms 玩家浮空 | 20/20 | 0 | 0/0 | 0°/0° |
| rev3-p1-100 | 100 ms 玩家浮空 | 20/20 | 0 | 0/0 | 0°/0° |
| rev3-gamediff | 两客户端 Gameplay 不同 | 拒绝断言 1/1、在线 0 | 0 | 未采集 | 未采集 |
| rev3-sync | 已协商后发送错误有效规则哈希 | 拒绝断言 1/1、在线 0 | 0 | 未采集 | 未采集 |

最终普通目录仍为有效规则哈希 `2314f71c7d568d7b706cc536588ee196dbb0305c7a625eb0ad83afb39b07173e`。例如 `rev3-presonly` 服务端启动：registry load+validate **39.1 ms**，含配置覆盖 **53.5 ms**；P0B 的 Presentation 哈希不同但被允许。动作时序、位移额度与判据未调整。

33 场复核（22 主批次＋3 补充＋8 最终批次）原返回码都为 0；**返回码不是验收结论**，`rev2-sync-red` 拒绝断言失败、原渲染探针失败及回归未达标均单列。全部专服客户端/OpenGL 类加载数 0，客户端记录模型加载日志且模型／纹理错误匹配数 0。结构化摘要按每场保存完成数、失败序列、请求拒绝分布、渲染分类、性能值、生命周期断言及关键摘录；未采集值为 null，绝不转换为 0。

**复现命令与证据：**

```powershell
$env:JAVA_HOME='C:\Program Files\Zulu\zulu-25'
$env:VERSION='0.1.0-dev'
.\gradlew.bat spotlessCheck checkstyleMain checkstyleTest test build --no-configuration-cache --console=plain
.\gradlew.bat p0LaunchFiles -I tools/p0/validation.gradle --no-configuration-cache --console=plain
python tools/wf1/run_ab.py recheck build/wf1c/recheck-spec.json
python tools/wf1/run_ab.py recheck-supp build/wf1c/recheck-supp-spec.json
python tools/wf1/run_ab.py recheck-final build/wf1c/recheck-final-spec.json
python tools/wf1/recheck_summary.py --output docs/wf1/WF-1c-recheck-summary.json recheck recheck-supp recheck-final
```

运行前的 spec 及冻结包留在本机忽略的 `build/wf1c/`。精确顺序、JAR SHA-256 和每条 args 已收入摘要的 `batches` / `jars`，可据此重建 spec；复现必须换新的标签前缀，脚本会拒绝覆盖已有运行。新增图片共约 0.12 MB，仅原始 framebuffer PNG；没有逐帧 JSON 或整份进程日志入库。

**仍未满足／不确定：**

- 严格“所有预定会话均 20/20、S08=0”的批次判据未满足。最终会话全绿不证明更早的退化已消除；仅凭基线也失败不能判定接线无回归。
- 动作中服务端传送仍被预算拉回；第二武器补测会话也出现 1 次预算纠正。没有扩大额度或静默忽略。
- 纯水平根位移对 onGround 的副作用已由原版公式核实，但未在本轮改物理；浮空 S08 的额外上升根因尚未确认。范围扩展需用户审核，不把诊断假设当结论。
- 最终冷协商补丁没有另重复三次性能矩阵；三次对照属于 review 阶段包。源码／类差异和冻结哈希已列明，不混称同一冻结包。
- 左手握位仍仅加载，刚体适配器尚不驱动左手；大量 pack、GPU 时间、其它相机／Netty 模组共存仍未验证（沿用 §8）。

接线补缺本地提交 `5046830`；实机 JAR 在提交前冻结，生产源文件与该提交一致，哈希以表为准。报告与证据另行本地提交。本轮未推送，等待用户审核／批准，不开始 C2a 或 P1-b。

## 附录 A 运行清单

主批次 `main`（29 个会话，顺序、无重试）、诊断批次 `diag`（`p1f-*-r{2,3,4}` 与 `life-4`）、补充渲染批次 `render`（`ab-cand-0-{4,5,6}`、`ab-ctrl-0-{4,5,6}`）的状态见 `WF-1c-summary.json` 的 `batches`。其余单独运行：`smoke-1`、`w2-1`、`w2-2`、`life-1`（`w2-1`/`w2-2`/`life-1` 在夹具修复前）。`func-1` 批次：`life-2`、`float-*-1`、`w2-3`。

| 标签 | JAR | 参数 |
|---|---|---|
| `ab-base-0-{1,2,3}`、`ab-base-100-{1,2,3}` | `base` | `--souls-view --render-probe [--rtt 100]` |
| `ab-ctrl-0-{1,2,3}` | `ctrl` | 同上，0 ms |
| `ab-cand-0-{1,2,3}`、`ab-cand-100-{1,2,3}` | `cand` | 同上 |
| `p1f-*` | `base`/`cand` | `--launcher player [--rtt 100]` |
| `art-ctrl`、`art-cand` | `ctrl`/`cand` | `--art-verify` |
| `hs-presonly` | `cand` | `--souls-view --client-content-pack P0B=tools/wf1/handshake-packs/presentation-only` |
| `hs-gamediff` | `cand` | `--wf1c handshake --client-content-pack tools/wf1/handshake-packs/gameplay-diff` |
| `legacy-1` | `cand` | `--souls-view --server-cfg content:legacyKatanaJson=true` |
| `life-4` | `cand` | `--wf1c lifecycle --content-pack src/test/resources/wf1test --weapon-items clashweave:katana=katana:katana,minecraft:stick=wf1test:glaive` |
| `float-{death,dimension,disconnect}-2` | `cand` | `--wf1c float-<事件>` |
| `w2-4` | `cand` | `--wf1c weapon2` ＋ 同 `life-4` 的 pack 与映射 |
| `b1-*`（错误数据包） | `cand` | `tools/wf1/bad_pack_start.py --jar build/wf1c/cand-4.jar --prefix b1` |

工具：`tools/wf1/run_ab.py`（交替批次，不重试不覆盖）、`tools/wf1/summarize.py`、`tools/wf1/ab_report.py`、`tools/wf1/collect_summary.py`、`tools/wf1/bad_pack_start.py`、`tools/wf1/ImageCompare.java`；夹具 `Wf1cScenario`、`Wf1cReplay`、`RenderProbe`、`Ids`；`dual.py` 新增 `--wf1c`、`--content-pack`、`--client-content-pack [ROLE=]`、`--weapon-items`、`--server-cfg`、`--render-probe`。
