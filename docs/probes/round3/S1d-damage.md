# S1d 目标级最大无敌计时：通过（所测生命周期与伤害负载）

## 假设、源码与做法

实际反编译的 Forge 10.13.4.1614 `EntityLivingBase.java`：第 86 行 `public int maxHurtResistantTime = 20`；第 854 行比较 `hurtResistantTime > maxHurtResistantTime / 2.0F`；855–861 行拒绝不高于 lastDamage 的伤害，更强伤害只结算差值；865–870 行完整命中更新 lastDamage、设置 hurtResistantTime=max，再调用 damageEntity。第 822 行 Attack 事件在门前，玩家 EntityPlayer 第 1105 行另触发一次，1161 行调用 super。hurtTime/maxHurtTime 的 10 是表现计时，不是本次配置字段。源码哈希见运行 summary。

独立 `tools/probes2/s1d` 源集：进入交锋记录原 max，持久设为 JVM 配置 `cw.s1d.max`（默认 8）；重复进入不覆盖原值；退出时只有当前值仍等于本模组所写值才恢复。死亡 Forge 事件、FML 玩家换维度/下线事件和 END 存活/维度检查负责清理。包装 `apply` 只有一次普通 attackEntityFrom，不写 hurtResistantTime、lastDamage 或任何调用帧字段。命中账本按动作/目标 UUID/段去重，结束清理。

两组目标状态完全相同，直接调用与包装使用相同伤害数值；不同于 S1c 为绕过门改变候选数值/字段。递归基线是原版先打 4 点、推进 7 tick 留 timer=13，再双方进入 max=8。28 组配对覆盖牛/真实 EntityPlayerMP、Attack NORMAL/LOWEST、Hurt HIGH/LOW/HIGHEST，以及两种同优先级注册顺序；只读同优先级 witness 不修改字段。玩家 Attack NORMAL 的双事件由真实原版方法产生。

## 命令与实测输出

```powershell
$env:JAVA_HOME='C:\Program Files\Zulu\zulu-25'
$env:VERSION='0.1.0-dev'
.\gradlew.bat -I tools/probes2/s1d/experiment.gradle s1dLaunchFile --no-configuration-cache
python tools/probes2/s1d/run.py --accept-eula --label new-s1d
```

使用新 label；拒绝覆盖旧世界；EULA 已由用户接受。最终证据：[完整测量](evidence/s1d-final/measurements.log)、[启动/异常/停止](evidence/s1d-final/server.log)、[summary 与命令/源码哈希](evidence/s1d-final/summary.json)。

```text
S1D_RESULT pairs=28 mismatches=0 owned=0 ledger=0 verdict=PASS
S1D_INTERVAL player=false gap=4 successes=3 loss=6.0 restored=20
S1D_INTERVAL player=false gap=7 successes=3 loss=6.0 restored=20
S1D_INTERVAL player=true gap=4 successes=3 loss=6.0 restored=20
S1D_INTERVAL player=true gap=7 successes=3 loss=6.0 restored=20
S1D_LEDGER first=true duplicate=false second=true beforeEnd=2 afterEnd=0
S1D_TRANSITION oldTimerOnEnter=13 firstFullDamageAfterEnter=9
```

28/28 递归配对的返回结果、递归损血、外层生命、原生 timer 与 lastDamage 一致；玩家 Attack NORMAL 各两次回调一致。72 组边界配对（牛/玩家×两种注册顺序×9 场景×无历史/首轮timer=13历史）包括取消 Attack/Hurt、0、已死亡、致死、Attack 普通/LOWEST 抛异常、Hurt 普通/HIGHEST 抛异常；直接与包装结果完全一致。保留原版对零伤害/取消 Hurt 的返回语义，未改成 false；异常同步传播由夹具捕获。历史标签 after-clear/before-restore 只标相同事件负载，本方案没有清零或瞬时恢复。

独立生命周期测试：退出、死亡清理、玩家维度/下线事件、非玩家维度变化均恢复20且账本 ownership 为0；原 max=30 恢复30；其它模组改为12则保持12。致死由真实 onDeath 路径触发；其它玩家维度/下线是向正确 FML 总线发布真实事件类型的夹具，不是实际联网换维度/断开。EntityPlayerMP 是真实原版类，但没有网络登录；推进时按 MP 原生独立规则倒计时。未冒称联网死亡/重生已测。

## 用户已接受的代价：其它来源

每档使用新目标，原版先打2点，分别等待1..10 tick 再以等强度来源攻击。持续火伤使用 DamageSource.onFire；第二攻击者使用两个不同的真实 EntityPlayerMP 对象分别产生来源。两类目标、两类来源结果相同：

| max | gap 1–3 | gap 4–9 | gap 10 | 首次完整2点伤害 |
| --- | --- | --- | --- | --- |
| 原版20 | 拒绝/扣血0 | 拒绝/扣血0 | 接受/扣血2 | 10 tick |
| 交锋8 | 拒绝/扣血0 | 接受/扣血2 | 接受/扣血2 | 4 tick |

等强度有效拒绝窗口从10缩至4 tick，缩短6 tick（20TPS约300ms）。这是对所有来源的目标级变化，非仅本模组攻击；更强来源仍保留原版差值语义，不宣称所有伤害都获得4tick免疫。进入时已有 timer=13 不修改，需再9tick降到4才能受等强伤害；退出恢复 max 不补长当前 timer。外部写入与本模组值恰好相同无法仅凭字段值辨识，这是条件恢复的限制。

## 结论、限制与设计影响

**通过所测负载**：瞬时隔离失败的问题已转为用户明确接受的持久目标状态；不再尝试 S1b/S1c 共享字段瞬时修改，也未采用 ASM/Mixin。原型说明1.1/S1、1.2/S1d 的方向得到支持，应补充进入/退出旧 timer 的过渡行为、等强来源缩短6tick和同值外部写入不可检测限制。

最终服务器 exit=0、客户端/OpenGL 实际类加载0。首次开发运行牛在默认坐标夹具出现额外1点，改为空中固定测试位置后全量复跑；源码有不透明方块内的inWall伤害分支，但该次额外来源未单独记录；未将其伪报通过。盔甲/药水、第三方实体覆写、真正联网生命周期及实际交锋检测接入尚未验证。参数可调，没有冻结公开 API，P0 未开始。
