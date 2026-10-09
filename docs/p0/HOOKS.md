# P0 口子补丁（B）

在 A 修复提交 ea10a8b 后，同一分支接入内部接口和默认实现；不冻结公开 API，不实现第二武器、SA、模式、匠魂、皮肤或特效系统。

## 武器定义解析

Forge 边界 `WeaponResolver.resolve(ItemStack)` 返回纯 `WeaponDefinition`：风格ID、动作数据引用、damage/poise/cost有界倍率、SA/效果槽占位、外观ID。P0唯一实现 `StaticKatanaResolver` 仅接受注册的静态打刀，忽略客户端物品NBT中的自带定义/倍率。`CombatWeapons` 负责服务端注册类型/数据引用校验与倍率范围（默认0.5–2，可配置）。动作数据文件从定义的引用加载；CombatServer不再判定KatanaItem/固定物品或读固定文件名。

生产调度通过解析结果与其服务端动作目录构造/更新；空闲时可用定义，动作中拒绝换定义。更换目录时清理旧目录缓冲，不清恢复/账本/实例编号；P0默认目录与定义不变化。材料倍率只作用于伤害等数值，不改变时序、位移、弧段/高度几何；poise/cost与SA/效果槽是明确占位，P0没有对应系统，不隐式实现。

## 外观ID及安全点

StateMessage给持有者和观察者同步skin/effects/animations三个ID与风格ID，不同步模型、贴图或可执行定义。ID有字符/64长度限制；观察者仍不收到会话凭据与动作数据。AppearanceState.Pending只在动作之间提交；不会重启动作或清命中去重。渲染从外观ID解析默认模型/贴图，不再在挂点适配器里直接写死资产；未知ID回退默认。现在服务器只产出默认值，没有外观选择系统。

## 语义事件与默认表现

`SemanticEvents`发布DRAW、SWING、HIT、BLOCK、PARRY、SHEATHE、HURT；P0权威接触按原有确定性提交顺序生成。拔刀先于该实例挥刀；部分格挡为BLOCK→HIT→HURT，精准为PARRY且无伪造HIT；纳刀在权威完成时生成。表现监听器的异常被隔离并记数，不允许停止其他监听器或改变结算。

`CombatPresentation`及`SemanticDispatcher`从CombatServer拆出与口子直接有关的结果投递，不修改伤害、输入或位置。原版来源仍同步结算，格挡表现仅在本tick完整事件回调结束后检查取消状态，再投递语义事件；这不是伤害队列。原版来源玩家受击由已结算HP下降观测，目标来源未知时为-1；其他模组直接改HP也可能被归为HURT，纯吸收盾损耗未补全，此限制不影响P0命中调用/结算。P0命中由attackEntityFrom返回值触发HIT/HURT。

自定义单向SemanticMessage带递增序号、角色/目标、实例/动作与时刻。客户端主线程去重、按顺序派发，默认订阅者保持P0本地命中/格挡声；部分格挡的HIT不会额外再响一声。原版来源格挡的新语义事件保留instance=0，默认声不新增，保持旧行为。本地非权威挥刀声继续独立；未收到权威事件不产生伤害。观察半径默认64，可配置，不作用于游戏判定。

## 动画查找

渲染以动画集ID+动作ID查找，精确动作优先于该集通配默认；整套回退顺序是武器专属→类型默认→全局默认。P0六动作都注册现有rigid_arc程序姿势，表达式与原先一致。动画集与资源只在表现层使用，不参与轨迹/窗口。

## 测试与运行

Spotless、Checkstyle、build通过（hooks-final-validation-build：BUILD SUCCESSFUL in 3s）；JUnit实际32项，原15项仍在，新增武器解析/伪造NBT与倍率、外观ID编解码/安全点、事件顺序/监听失败隔离/编码、动画回退、部分格挡默认声等测试均0失败。第一次构建的唯一Checkstyle错误是星号import，已改明确import；不以失败构建作验收。

联调过程中一次重建覆盖了正在运行的开发jar，造成客户端及专服类加载失败，该次hooks-replay100作废。验证器现在每轮快照所有工作区构建jar（含夹具），所有角色共用同一快照并记录SHA256，不加载后续重建产物。

最终专服双客户端运行如下，各轮退出0，客户端/OpenGL类加载0；每轮runtime-snapshot.json记录实际构建SHA256。命令：`python tools/p0/dual.py --label review-final-zero --accept-eula`，100ms加`--rtt 100`，渲染加`--render-only`，低TPS加`--movement --low-tps --rtt 100`；回放后执行`analyze.py --label <label>`及`semantic_metrics.py --label <label>`。

| 场景 | 实测 |
| --- | --- |
| review-final-zero | 合法60/60，非法3条均拒绝；35接触，玩家格挡/截止违例0；128语义事件顺序违例0；A/B默认声音35/35、32/32，无新增重复声。 |
| review-final100 | 合法59/59，非法3条均拒绝；39接触，玩家格挡/截止违例0；130语义事件顺序违例0；A/B默认声音39/39、36/36。 |
| review-view-reset-zero/100 | 各20完整连击、61 START，序列内S08=0，START yaw/pitch变化=0，全包视角违例0。 |
| review-final-low100 | 持续每tick睡眠150ms，30动作，仅5格横移和5格上移造成2次预算纠正；轻度超速仍不可检出，未改变已有负面结论。 |
| review-final-render | 每客户端16张实际PNG；默认外观ID同步，侧后四图逐张判读见P0-c。 |

客户端事件接收顺序违例均0；B较晚上线，未收到上线前13条事件，不要求接收全服历史。外观日志确认两客户端读取skin=katana、effects=default、animations=katana、style=katana。普通怪物额外提交tick均0，0/100ms冻结→提交均值0.4859/0.32107ms。主线程本地声音源回调均值0.13615/0.12786ms，物理扬声器时延未验证。

1000tick战斗逻辑采样均值0.257352/0.256281ms、最大12.0616/12.8795ms。与此前A100ms均值约0.224ms相比有约0.032ms均值增加，样本非配对，不能宣称性能完全不变或优化；报告保留实测差异。合法执行率、拒绝、格挡、视角、位移纠正指标无新增退化。证据在evidence同名目录，只收摘要、逐项JSON、关键日志及PNG。

## 仍保留的固定部分与原因

P0只注册katana风格及静态打刀；默认资源路径在PresentationAssets默认注册项中集中定义。Scheduler的空闲入口身份仍是iai/light_1/heavy/sheathe，P0只有一个风格，未引入多风格入口系统。根移动和碰撞使用P0已有单武器目录；SA/effect槽最多4个是内部数据界限，未承诺公开协议。默认反馈仍是占位音色，rigid_arc没有骨骼/IK。未来类型注册、材料和效果系统须另行审核；本补丁不把任意客户端描述解析成新动作。
