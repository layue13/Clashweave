# S1c 事件帧伤害门：失败

## 假设与做法

本轮用户审核的候选是：包装调用只登记当前 `(目标, DamageSource 对象)`，不提前修改字段；`LivingAttackEvent LOWEST` 清计时，`LivingHurtEvent HIGHEST` 恢复计时和 lastDamage，`finally` 兜底。通过标准保持其它来源递归施伤的同步返回值、实际扣血和原版伤害门一致，不取消递归、不延迟递归结算。

独立实验模组 `tools/probes2/s1c/java`，由 opt-in Gradle init script 建立 `s1cExperiment` 源集；不在 `src/main`，不进入生产 jar。真实 Forge 专用服务器、WorldServer、EntityCow 与 EntityPlayerMP，实际调用原版 `attackEntityFrom`，不复制伤害公式。每组最大血量 100，generic 先造成 4 点伤害，7 tick 后 hp=96、timer=13、lastDamage=4。实体不加入世界 tick 列表以隔离 AI；玩家为真实 EntityPlayerMP 实例，非联网登录玩家。玩家夹具清除初始 60 tick 出生保护，并按其 `onUpdate` 原生字段递减逻辑推进 7 次计时（EntityLivingBase.onEntityUpdate 对 MP 不递减）。这不是客户端/PvP 联网验收。

Attack 回调配对完全沿用 S1b：外层请求 2 点，递归其它来源 4 点。每个嵌套调用创建不同的 DamageSource 对象，事件钩子不将其识别为登记来源，也不替它清计时。

Hurt 回调的 vanilla 对照外层请求为 6 点，利用已有 lastDamage=4 的原生差额分支进入 Hurt（事件 amount=2），候选请求 2 点。两组 Hurt 时欲保留的伤害门都是 timer=13、lastDamage=4，事件伤害量同为 2；这项不宣称外层原始请求值相同，而是控制 Hurt 回调的事件负载和原版门。若对照仍用 2 点，会在伤害门提前返回，根本没有 Hurt 回调，不能作为有回调的对照。

在 NORMAL Attack、HIGH/LOW Hurt 与 LOWEST Attack/HIGHEST Hurt 的同优先级两种注册顺序下分别测试。固定 2/4/6 点与 7 tick 是边界负载，不是冻结玩法配置。

## 命令与实测输出

仓库根 PowerShell，EULA 已由用户接受：

```powershell
$env:JAVA_HOME='C:\Program Files\Zulu\zulu-25'
$env:VERSION='0.1.0-dev'
.\gradlew.bat -I tools/probes2/s1c/experiment.gradle s1cLaunchFile --no-configuration-cache
& 'C:\Users\layue13\AppData\Local\Programs\Python\Python312\python.exe' tools/probes2/s1c/run.py --accept-eula --label s1c-final
```

重跑使用新 label，脚本拒绝覆盖世界/证据。仅绑定 localhost:25571。最终实验构建成功；服务器正常退出 0，`-verbose:class` 中 Minecraft 客户端/OpenGL 类加载 0。完整原输出、真实启动命令及 SHA-256：[measurements.log](evidence/s1c-final/measurements.log)、[server.log](evidence/s1c-final/server.log)、[summary.json](evidence/s1c-final/summary.json)、[玩家/监听列表额外源码哈希](evidence/s1c-final/additional-source-sha256.json)。Forge 日志中的 FixtureException 是实验故意抛出并同步捕获的异常，不是服务器崩溃。

关键原输出（省略 Forge 日志前缀）：

```text
CW2 S1C_PAIR scenario=attack-normal listenerRegisteredBeforeGate=true controlNested=false candidateNested=false controlLoss=0.0 candidateLoss=0.0 callbacks=1,1 equivalent=true
CW2 S1C_NESTED scenario=attack-lowest-after path=candidate at=attack-LOWEST timer=0 last=4.0 accepted=true loss=4.0
CW2 S1C_PAIR scenario=attack-lowest-after listenerRegisteredBeforeGate=false controlNested=false candidateNested=true controlLoss=0.0 candidateLoss=4.0 callbacks=1,1 equivalent=false
CW2 S1C_NESTED scenario=hurt-highest-before path=candidate at=hurt-HIGHEST timer=20 last=2.0 accepted=true loss=2.0
CW2 S1C_PAIR scenario=hurt-highest-before listenerRegisteredBeforeGate=true controlNested=false candidateNested=true controlLoss=0.0 candidateLoss=2.0 callbacks=1,1 equivalent=false
CW2 S1C_NESTED scenario=player-attack path=candidate at=attack-NORMAL timer=13 last=4.0 accepted=false loss=0.0
CW2 S1C_NESTED scenario=player-attack path=candidate at=attack-NORMAL timer=0 last=4.0 accepted=true loss=4.0
CW2 S1C_PAIR scenario=player-attack listenerRegisteredBeforeGate=true controlNested=false candidateNested=true controlLoss=0.0 candidateLoss=4.0 callbacks=2,2 equivalent=false
CW2 S1C_THROW at=attack-LOWEST timer=0 last=4.0
CW2 S1C_BOUNDARY scenario=throw-attack-after-clear accepted=false threw=true loss=0.0 hp=96.0 deadFlag=false timer=13 last=4.0 restored=true noExtraLoss=true
CW2 S1C_THROW at=hurt-HIGHEST timer=20 last=2.0
CW2 S1C_BOUNDARY scenario=throw-hurt-before-restore accepted=false threw=true loss=0.0 hp=96.0 deadFlag=false timer=13 last=4.0 restored=true noExtraLoss=true
CW2 S1C_LEDGER first=true duplicate=false secondSegment=true beforeEnd=2 afterEnd=0 nextAction=true final=0
CW2 S1C_RESULT pairs=9 mismatches=3 frameDepth=0 ledger=0 verdict=FAIL
```

| 负载 | 实测结果 |
| --- | --- |
| 牛，NORMAL Attack 中其它来源 4 点 | 对照/候选均拒绝、扣血 0；候选外层 2 点正常结算 |
| 牛，HIGH 与 LOW Hurt 中其它来源 4 点 | 均与对照一致，拒绝、扣血 0 |
| LOWEST Attack 同优先级监听器先于清门 | 一致；后于清门时额外扣血 4，失败 |
| HIGHEST Hurt 同优先级监听器后于恢复 | 一致；先于恢复时额外扣血 2，失败 |
| 玩家 NORMAL Attack 两次回调 | 对照两次均拒绝；候选第二次递归扣血 4，外层另扣 2，最终 hp=90，失败 |
| 玩家 HIGH Hurt 回调 | 对照/候选均拒绝嵌套伤害，扣血 0 |
| Attack 取消、Hurt 取消、0 点伤害 | 字段恢复、扣血 0；Hurt 取消与 0 点牛伤害外层返回 true，保留原方法语义，不偷改返回值 |
| 已死亡 hp=0、致死 hp=1 请求 2 点 | 分别扣血 0 与 1，字段恢复；致死调用走原版 onDeath；未推进死亡动画后续 tick，isDead 仍 false |
| Attack 普通/清门后抛异常，Hurt 普通/恢复前抛异常 | 共四个入口，均同步传播并由夹具捕获；字段恢复、frame 栈空、扣血 0 |
| 同实例同目标同段及实例结束 | first=true、duplicate=false、secondSegment=true；结束前账本 2，结束后 0；下一实例可命中并再清为 0 |

这是确定性边界配对，不是统计成功率。最终证据为 `s1c-final`；开发首跑因玩家夹具未按 MP 的独立倒计时推进而中止，修正后完整重跑，未将首跑当通过或最终证据。后续联网玩家死亡/重生、盔甲/药水、第三方实体覆写与事件模组兼容均未验证。

## 结论与设计影响

**失败。** 满足普通牛 NORMAL Attack 的 S1b 递归反例并不足以隔离调用帧：同优先级注册顺序仍暴露清零后或原生改写后的共享字段。FML 实际 `ListenerList` 在同优先级列表按注册顺序追加，优先级端点不能保证相对所有监听器永远最后/最先。不同来源对象不会触发本实验清门钩子，却依然读到当前实体上已改变的字段；“匹配来源对象”不是调用帧隔离。

另一个与注册顺序无关的失败是玩家：`EntityPlayer.attackEntityFrom` 1105 行先派发 Attack，1161 行调用 super；`EntityLivingBase.attackEntityFrom` 822 行再次派发 Attack。候选在第一次 LOWEST 清零，第二次 NORMAL 回调立即可见 timer=0。实验记录 callbacks=2,2，候选第二次其它来源递归扣 4，原版为 0。不能用跳过这次递归或静默取消事件修补。

推翻的是**用户本轮 S1c 待审事件方案作为通用伤害门隔离的假设**；同时再次证明原型说明 **1.1/S1 的共享字段窄作用域方向、1.2/S1b 的 P0 伤害前提**还缺可满足递归一致性的接入点。原通过标准不降低。`finally` 在清门后及恢复前异常下确实恢复了字段，但不能撤销前述已经扣血的副作用。S1c 失败不阻止用户本轮独立探针继续；全部通过的 P0 闸门仍未满足。

## 字节码 / Mixin 接入代价（评估，未采用）

源码层面需要把“是否绕过门”和“原版门字段是否更新”限定在原版门本身（EntityLivingBase 854–870 行），使外部 Attack/Hurt 回调始终看见原版字段。只重定向 hurtResistantTime 读取仍不足以解决 lastDamage 赋值与前后回调可见性；只覆盖整个方法则复制大量原版事件、盔甲、死亡与击退逻辑，兼容面更大。需另审具体注入契约与原版覆写覆盖范围。

| 接入 | 新增工作与风险 |
| --- | --- |
| Forge coremod / ASM | 需独立装载入口、开发/SRG/混淆名称映射、准确的门判断与赋值字节码匹配；必须验证变换次数和目标指令形状，遇不匹配明确失败，不能偷偷使用旧方案。需处理其它 coremod 的变换顺序、EntityPlayer 双事件、绕开 super 的第三方实体覆写。 |
| Mixin | 当前仓库 `gradle.properties` 为 `usesMixins=false`。启用需额外依赖/启动与构建接入、配置和映射产物，并在 1.7.10 实际 LaunchWrapper/Java 8/专用服务器与混淆 jar 验证；注入定位与覆写兼容问题仍存在，工具本身不会证明伤害语义正确。未选择版本、未改构建开关。 |
| 两者共同验收 | 复跑本页全部场景，再补玩家联网/死亡重生、同时多来源与同来源复用、药水盔甲吸收、Forge事件取消/异常、第三方监听器/实体覆写。应比较原版返回值与所有副作用，不只恢复两个字段；不采用异步队列或取消递归。 |

以上是对现有源码接入点的工程分析，未实装、未测兼容、没有工时数字。不修改生产代码、不冻结 API，等待用户审核下一轮方案。
