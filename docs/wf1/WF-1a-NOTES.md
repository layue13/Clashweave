# WF-1a 内容层纯逻辑

本轮新增 `com.layue13.clashweave.content`，实现 v1 内容模型、命名空间 ID、跨文件/跨包注册与校验、冻结数据、Gameplay/Presentation SHA-256 清单比较。新增 katana 的五个拆分文件和仅用于测试的 `test:spear`。未修改 core 既有源码、旧 `combat/katana.json`、forge/client 或渲染；工作区交付，不 commit。

已读 `extension.md` §15–17、`assets.md` 和指定 core 源码。仓库不存在任务指定的 `docs/design/as-built-p0.md`，改以 `docs/p0/P0-a-core.md`、`docs/p0/REPORT.md`、`docs/p0/HOOKS.md`、P1-a 报告与现有实现核对，未补造该文件。

## Schema 概览

每个输入文件都是一个 pack 对象：`schemaVersion: 1`、`namespace`，加五种可选数组 `weapons/styles/actions/animations/resources`。可以一个文件包含全部数组，也可以同一 namespace 拆多个文件；加载器将同 namespace 合为一个 `ContentPack`。katana 输入依次为 `weapon.json`、`style.json`、`actions.json`、`resources.json`、`animations.json`，路径均在 `assets/clashweave/content/katana/`。文件名只作诊断来源，不作身份。

| 数组 | 字段与默认值 | 清单 |
| --- | --- | --- |
| weapons | 必填 `id/style/resource/animation`；`damageMultiplier/poiseMultiplier/costMultiplier` 默认 1，必须有限且 >0；`saSlots/effectSlots` 默认空数组，最多各 4 项 | id、style、倍率、槽位属 Gameplay；resource/animation 绑定属 Presentation |
| styles | 必填 `id/actions`（非空、无重复的动作 ID 列表）；`bufferTicks` 默认 4；`effectVelocityLimit` 默认 1.2 | Gameplay |
| actions | 直接使用 `ActionCatalog.Definition`，没有第二套动作字段模型 | Gameplay，含 effects |
| animations | 必填 `id/preset`；preset 是客户端解释的非空程序姿势名，本轮没有关键帧播放器 | Presentation |
| resources | 必填 `id/models/texture/grip/handGrips/mounts`，见下表 | Presentation |

动作必填原有 `id/startup/active/recovery/damage/reach/arc/distance/speed/edges`；`lift/sweepElevation` 默认 0，`effects` 默认空数组。边必填 `input/target/from/until`，`hit/damageHit` 默认 false，`requires` 省略表示无条件，显式值仅 GROUND/AIR。时窗为 `[from, until)`，不能越过 duration；同输入的重叠时窗拒绝。恢复 >=1，其他 tick 非负且总 duration 不溢出；几何/速度/伤害有限且非负，arc <=360，sweepElevation 在 [-90,90]。效果沿用 `HitEffect`，本轮仅 LAUNCH，必填 type/vy；vy 非负、不能重复 LAUNCH。style 中 lift/vy 不得超过其 effectVelocityLimit；每条边目标必须存在且在所属 style 的动作列表内。

| Resource 字段 | 含义 |
| --- | --- |
| `models.held` | 手持模型资源位置 |
| `models.stowed` | 纳刀态模型资源位置 |
| `models.stowed_empty` | 拔刀后空鞘模型资源位置 |
| `texture` | 贴图资源位置 |
| `grip.offset.{x,y,z}` | 模型原点到握点的偏移，katana 沿用握点原点，均 0 |
| `handGrips.right/left.{x,y,z}` | 手部局部挂接偏移，坐标单位 block |
| `mounts[]` | `{slot,x,y,z,angle}`；slot 是客户端适配器解释的小写槽名，角度为度 |

katana：右手 `(-0.0625, 0.55, 0)` 沿用 `KatanaRendering`；左手暂镜像为 `(0.0625, 0.55, 0)`。`left_hip` 挂点按任务指定为 X=0.58、Y=0.55、Z=0.18、Angle=45，不沿用旧渲染配置的 X=0.32。第三人称手/腰空间沿用 Biped 的向下 Y；模型保持 block 单位、握点原点、刀尖 +Y。第一人称额外相机/手臂矩阵及现有旋转、缩放仍在适配器，本轮未迁移成数据。

内容身份严格为 `[a-z0-9_]+:[a-z0-9_]+(?:/[a-z0-9_]+)*`；namespace 不含斜杠。声明与引用可写短 ID，在加载时补当前 pack namespace；声明 ID 必须归属该 namespace，引用允许跨 namespace。注册表查找短 ID 使用旧 katana 兼容规则（`light_1 → katana:light_1`）；第二武器调用请传完整 ID。资源位置另用 Minecraft 文件路径形式，允许扩展名点号/横线，例如 `clashweave:models/katana.obj`；它们不作为内容 ID 注册。

未知字段报错；以下划线开头的说明字段忽略且不入哈希。可选字段请省略，不接受显式 null。必填向量分量与挂点分量须齐全，数值必须有限。

## API 与加载生命周期

```java
ContentRegistry registry = ContentRegistry.load(
    new ContentRegistry.Source("weapon.json", weaponReader),
    new ContentRegistry.Source("style.json", styleReader),
    new ContentRegistry.Source("actions.json", actionsReader),
    new ContentRegistry.Source("resources.json", resourcesReader),
    new ContentRegistry.Source("animations.json", animationsReader));
ContentPack.Weapon weapon = registry.weapon("katana:katana");
ActionCatalog actions = registry.catalog(weapon.style);
ContentManifest manifest = registry.manifest();
```

`Source` 同时支持 Reader 和 UTF-8 InputStream，不访问文件系统，不关闭调用方流；外层适配器负责打开/关闭。可传 `List<Source>` 加载多个包。重复 ID 在各注册表内拒绝，不覆盖；同一 ID 可分别用于不同类别（例如 style 和 resource）。未知合法 ID 查找返回 null；非法 ID 抛 IllegalArgumentException。失败统一抛 `ContentValidationException`，`errors()` 返回不可变错误列表，每项为“来源名: JSON 路径: 原因”。各文件错误聚合；同一条动作存在类型错误时仍检查独立的范围错误，解析失败的文件报告 `$`，继续校验其余文件。

五类 ID 都在加载期建立 Map，单条定位不遍历注册项，是相对于注册项数量 O(1)。内部 JSON 树完全私有且无写入口；对外 DTO（包括 core 的可变 Definition/Edge/HitEffect）都是脱离注册表的深副本；pack 返回的批量 Map 本身不可修改。`packs().get(namespace)` 得到冻结 pack，pack 的 `actions()` 等是批量快照，生成成本与该类别内容量成正比。单条查找有与该条定义大小有关的复制/分配成本，但没有文件读取、JSON 文本解析、全局验证或重新哈希。

`load` 仅在初始化/明确重建时使用；`catalog(styleId)` 会构造可配置的独立 `ActionCatalog`，也应在初始化时创建并缓存，不能放在每 tick。返回目录与注册表隔离，`launchPolicy` 等旧配置修改不会反向改写注册表。服务器配置若修改实际 Gameplay，接线阶段必须把这些覆盖纳入最终双方协商的规则清单，不能用原始 pack 哈希冒充覆盖后的规则哈希。

## 清单规范化

加载后对实际解析结果（包含缺省值与限定后的 ID）生成清单：对象键按字典序、注册项按 ID 排序；数组保留声明顺序，尤其 effects/edges 的顺序；数字按去尾零十进制格式，-0 规范成 0。字段书写顺序、空白、文件顺序、等值数字和 `_comment` 不影响哈希。SHA-256 输入为规范文本的 UTF-8，输出小写 64 位 hex，结果缓存。

Gameplay 包含 schemaVersion、全部动作字段（含 effects/lift/sweepElevation/边条件）、style 成员与缓冲/速度限制、weapon 的 ID/style/倍率/槽位。Presentation 包含 schemaVersion、全部资源/动画、weapon 的资源和动画绑定；模型/贴图/握点/挂点改动不改变 Gameplay。

`registry.manifest().compare(other.manifest())` 返回 MATCH、GAMEPLAY_MISMATCH、PRESENTATION_ONLY_DIFF；Gameplay 不同时优先报告 GAMEPLAY_MISMATCH。本轮只比描述数据，不读取 OBJ/PNG 文件内容，因此资产字节变化但资源位置不变，不会改变 Presentation 哈希。

## 下一步 Forge / 客户端接口点

- `CombatConfig` 固定加载 `combat/<actionData>.json` 的部分：初始化时打开五份 Reader/流交给 registry，再按 weapon.style 缓存 `catalog`。旧 `combat/katana.json` 仍可维持旧接线。
- `StaticKatanaResolver` / `CombatWeapons.DEFAULT`：将 Item→注册武器 ID 的服务端映射作为权威来源，从 `registry.weapon` 取得 style/倍率/资源/动画，再构造已有 `WeaponDefinition` 和 `AppearanceState`。不要从客户端任意 NBT 创建权威定义；旧 effects 外观 ID/槽位的语义本轮未扩展。
- `CombatServer` 仍有 DEFAULT 启动路径；改由已解析武器与其缓存目录驱动。网络握手须接入清单比较；既有 StateMessage 的动作数据与外观 ID 同步仍要审查。
- `Scheduler` 的空闲入口与纳刀判断仍写死短 ID `iai/light_1/heavy/sheathe`。新 catalog 返回完整 ID，不能仅替换加载调用就直接运行；下一步必须统一其入口/比较与命名空间身份（并补风格入口规则）。本轮按范围未改变 core 行为，spear 只是证明数据能注册，未宣称现有 Scheduler 能执行第二风格。
- `PresentationAssets` 固定 OBJ/PNG 和 rigid_arc 注册项：使用 resource 的模型/贴图位置，animation.preset 接入现有 AnimationLookup 回退。按需要使用完整动作 ID 注册；launcher 当前仍依赖旧回退。
- `KatanaRendering` 固定手部偏移和全局腰挂点：读取 handGrips/grip/mounts；处理模型握点偏移与 Biped 单位/轴变换。不要把逻辑轨迹绑定到视觉模型。本轮无客户端/OpenGL 测试。

## 未做与待确认

无资源自动发现、文件读取、远程下载、热重载、网络协议、实际 Item 注册、渲染播放器、模型存在性校验或资产字节哈希。saSlots/effectSlots 是沿用 core 的身份占位，无可执行槽位注册系统；poise/cost 仍是现有 core 的占位语义。动画最小 schema 只有 preset，无骨骼/clip 曲线。

现有资产只有 katana.obj 与 sheath.obj，纳刀与空鞘暂都引用 sheath.obj，无新增完整纳刀组合网格；客户端接线须确认状态显示。左手镜像握点没有旧实现可比对，也未实机验收。内容 ID 没有额外 64 字符限制；接入现有 AppearanceState/网络时须协调旧身份长度约束。Spotless/Eclipse 与 Forge 完整构建未在容器运行，不以 javac 结果声称客户端或完整 Gradle 构建通过。

## 真实验证

复现：在仓库根执行 `bash tools/wf1/run-content-tests.sh`。脚本使用任务指定的本地 JDK25/Gson 2.13.2/JUnit 4.13.2/Hamcrest，`javac --release 8` 编译**全部 core 主源码、content 主源码和 content 测试**，再运行 JUnitCore；临时 class 目录位于 /tmp 并在退出时清理。core 全部是纯逻辑，本次没有因 Minecraft 依赖而跳过的 core 文件。未用 Gradle，未运行旧 core 测试集。

20 项内容测试覆盖迁移动作全部字段、P1-a 字段与边、第二武器和跨包引用、Reader/InputStream 生命周期、ID/版本/冲突/缺失引用、效果类型/速度、窗口与图闭包、形状与语义错误聚合、资源 schema、冻结快照、清单三种比较和各动作/边/倍率规则哈希。

耗时测试仅打印，不设阈值；计时包含 Reader 文本解析、注册、验证、冻结和两份 SHA-256，资源流准备在计时外，不外推每 tick 耗时或冷启动性能。最终输出记录见下方。

```text
warning: [options] source value 8 is obsolete and will be removed in a future release
warning: [options] target value 8 is obsolete and will be removed in a future release
warning: [options] To suppress warnings about obsolete options, use -Xlint:-options.
3 warnings
JUnit version 4.13.2
.....katana load+validate+SHA-256: 20.058 ms; actions=7; gameplay=190b07d90feeecfa3ce5928ae402cc9ec94271c20a5a70424c213c60afcbe998
...............
Time: 0.547

OK (20 tests)
```
