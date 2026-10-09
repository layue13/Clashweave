# S1b 伤害：失败，停止闸门

## 假设与做法

验证原型说明 1.1/S1 的窄作用域候选：仅本模组调用前将目标 `hurtResistantTime` 清零，调用结束在 `finally` 恢复该计时与 `lastDamage`。原型说明 1.2/S1b 要求其它来源递归施伤与原版对照一致。成功判据预先取为：相同初始伤害门下，回调中其它来源伤害的接受结果与扣血量一致。

使用真实 Forge 专用服务器、WorldServer、EntityCow、LivingAttackEvent、`attackEntityFrom`。没有复制伤害公式。两只牛各最大血量 100，先由原版 generic 造成 4 点伤害，再调用 `onEntityUpdate` 7 次，实际得到 hp=96、timer=13、last=4。牛不加入实体列表，避免 AI/其它来源污染这个同步实验。

两组使用相同的 `clashweave.s1b` DamageSource、相同 2 点外层请求及同一事件监听器。监听器只在该来源攻击当前目标时递归调用一次 `DamageSource.generic` 的 4 点伤害；它不清计时、不取消事件、不修改任何字段。原版对照直接调用 `attackEntityFrom`；候选组通过窄作用域调用。数据是固定边界负载，不是玩法参数。新实验包为 `com.layue13.clashweave.experiments`，位于 `tools/probes2/java`，独立源集/实验模组 jar；没有复制首轮代码到生产目录。

## 命令与实测输出

在仓库根 PowerShell（EULA 已由用户接受）：

```powershell
$env:JAVA_HOME='C:\Program Files\Zulu\zulu-25'
$env:VERSION='0.1.0-dev'
.\gradlew.bat -I tools/probes2/experiment.gradle round2LaunchFile build --no-configuration-cache
& 'C:\Users\layue13\AppData\Local\Programs\Python\Python312\python.exe' tools/probes2/run_s1b.py --accept-eula --label s1b-reentry
```

重跑请换新的 `--label`，脚本拒绝覆盖已有世界/证据。启动信息从本次 Gradle 的 runServer 任务导出，不复用首轮启动文件；新测试世界只绑定 localhost:25567。实验结束自动正常停止服务器。

下面省略原生日志前缀，逐条原输出见 [measurements.log](evidence/s1b-reentry/measurements.log)：

```text
CW2 S1B_BEFORE path=vanilla-control hp=96.0 timer=13 last=4.0
CW2 S1B_REENTRY path=vanilla-control event=LivingAttackEvent source=generic amount=4.0 timerAtCallback=13 lastAtCallback=4.0 accepted=false loss=0.0
CW2 S1B_AFTER path=vanilla-control outerAccepted=false hp=96.0 timer=13 last=4.0
CW2 S1B_BEFORE path=scoped-candidate hp=96.0 timer=13 last=4.0
CW2 S1B_REENTRY path=scoped-candidate event=LivingAttackEvent source=generic amount=4.0 timerAtCallback=0 lastAtCallback=4.0 accepted=true loss=4.0
CW2 S1B_AFTER path=scoped-candidate outerAccepted=false hp=92.0 timer=13 last=4.0
CW2 S1B_RESULT recursiveEquivalent=false controlNested=false scopedNested=true controlNestedLoss=0.0 scopedNestedLoss=4.0 controlOuter=false scopedOuter=false controlCallbacks=1 scopedCallbacks=1 finalGateRestored=true verdict=FAIL stopBeforeOtherProbes=true
server_exit=0 client_or_opengl_class_loads=0
```

这是一次对照配对，不是统计成功率。已出现决定性反例后按用户要求停止，没有继续跑后续场景。

## 结论、原因与设计影响

**失败。** 原版对照中，递归 generic 4 点因既有伤害门被拒绝；窄作用域中，相同调用看见 timer=0，结算了全部 4 点。它又将计时设为 20、last 设为 4，使外层 2 点模组伤害被拒绝。`finally` 最后确实恢复了 timer=13、last=4，但已扣除的 4 点生命并未恢复。此处失败并非异常恢复漏写，也不是 dedup 账本缺失造成。

实际反编译 `EntityLivingBase.java`：822 行先调用 `ForgeHooks.onLivingAttack`，854–869 行随后执行无敌门与 lastDamage 更新。外层清零发生在 822 行之前，所以事件监听器可观察到清零后的共享实体字段，并在递归调用中消费它。源码与实验源码 SHA-256 见 [summary.json](evidence/s1b-reentry/summary.json)。对照调用与候选调用使用完全相同的回调入口。

这推翻的是 **原型说明 1.1/S1 将整个 `attackEntityFrom` 调用包在临时字段修改中即可隔离其它来源的假设**；1.2/S1b 的递归一致性标准保持有效。普通顺序施伤恢复成立的首轮结果，不覆盖递归期间的字段可见性。

建议待审核修订：伤害门绕过必须限定于本模组当前来源/调用帧，不能让外部事件回调与嵌套其它来源共享被清零的原版门。下一轮评估只在原版伤害门判断点按来源/调用上下文绕过、保留其它来源所见字段的接入；若涉及字节码接入，需显式评估实体覆写、Forge 事件顺序与兼容代价。仅在外层加一个重入布尔值仍挡不住监听器直接调用实体的原版方法；不要把递归伤害静默取消或改为异步队列，因为它们改变返回值和结算语义。上述方案均未实现/未验证，等待审核，不擅自采用。

目标死亡、玩家目标、异常抛出后的 finally 恢复、LivingHurtEvent 重入、账本结束清理均**未验证**；正常返回后的 finally 字段恢复已在本配对中观察到。S1b 已失败，不以这些未跑项补成通过，也不继续 S2b/S3b/S5b/S4b 或 P0。
