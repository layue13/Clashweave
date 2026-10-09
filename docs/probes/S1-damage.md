# S1：伤害与无敌帧 — 需改方案

## 假设与做法

原型说明 1.1/S1 建议按（动作实例、目标、段号）去重，仅在本模组伤害前清原版无敌计时。比较原版、直接清计时、临时清计时后恢复 `hurtResistantTime` 和 `lastDamage` 三条路径。每段 2 点、tick 0/7/14；同段重复请求一次。使用专用服务器真实 WorldServer、EntityCow、`attackEntityFrom` 与 `onEntityUpdate`，牛不加入世界实体列表，避免 AI/其它来源污染计时。没有复制伤害公式模拟通过。

对照实际反编译的 `EntityLivingBase.attackEntityFrom`：当 `hurtResistantTime > maxHurtResistantTime / 2`，`amount <= lastDamage` 返回 false；更大伤害只扣两者差额。否则登记 `lastDamage`、重置无敌计时并扣全额。受击视觉 `hurtTime` 与伤害门不是同一变量。源码身份见证据中的 `source-sha256.json`。

## 命令与实测输出

运行总入口的 `probeLaunchFiles`、`run_matrix.py --accept-eula`；服务器启动自动执行 S1，也可 `/cwprobe s1`。矩阵日志中的相同实验可重复核对：

```text
S1_SEGMENT tick=0 vanilla=true naive=true isolated=true duplicate=false hp=98.0,98.0,98.0
S1_SEGMENT tick=7 vanilla=false naive=true isolated=true duplicate=false hp=98.0,96.0,96.0
S1_SEGMENT tick=14 vanilla=true naive=true isolated=true duplicate=false hp=96.0,94.0,94.0
S1_OTHER tick=8 equalVanillaControl=false naive=true isolated=false hp=96.0,92.0,94.0
S1_STRONGER control=true isolated=true hp=94.0,92.0
S1_CANCEL accepted=false hp=100.0 timer=13
S1_SUMMARY vanillaHp=96.0 naiveHp=94.0 isolatedHp=94.0
```

`OTHER` 对照先由原版造成 4 点；tick 7 的本模组命中 2 点后，tick 8 再尝试原版 4 点。直接清计时路径改变了 `lastDamage`，让原版额外伤害通过；隔离路径与对照同样拒绝。原版 6 点强伤害在隔离组与对照都只增扣 2 点（两组总血量仍差本模组那 2 点）。Forge LivingAttackEvent 取消的命中没有扣血，计时恢复到 13。

## 结论与设计影响

7 tick 多段结算和段号去重可行；原描述若只清计时会污染其它伤害来源，因此 S1 结论为“需改方案”。建议修改原型说明 1.1/S1：仅在本模组伤害调用的窄作用域临时绕过原版伤害门，在 `finally` 恢复计时和上次伤害；不要把原版保护永久清零或重置为本模组伤害值。命中账本应绑定实例生命周期，明确取消/保护后是否消耗段号；本夹具将一次合法尝试记为已尝试，P0 需明示此语义。

隔离方案已经验证牛、普通原版伤害的等额/更强两种情况及事件取消；不代表其它模组在回调里递归施伤、所有盔甲/药水或特殊实体覆写均兼容。玩家真实受击在 S3 双人场景运行，但没有宣称完成全部来源组合。正式框架仍须检查继承覆写和 Forge 回调的重入边界。
