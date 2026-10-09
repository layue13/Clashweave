# S5：键位接管 — 需改方案

## 假设与方法

包装实际 `MovementInputFromOptions`，交锋态同时去掉 `sneak` 和原版 0.3 移动乘数；平时态恢复原输入。真实物理键状态跨 GUI/换物品/状态切换保留边沿，旧映射释放持续输入，已保持的 Shift 不合成新精准按下。鼠标前端取消持刀轻/重的原版路径；实体交互/攻击与挖掘在 Forge 事件层再拦截，平时可交互方块才放行。

服务端夹具使用真实 ItemInWorldManager、EntityPlayer.interactWith、LivingAttackEvent 和门/箱子状态，而不是只测试返回常量。友好保护与平时方块放行分别可以关闭；默认均 true，**仍待用户确认**。

## 命令与实测输出

运行总入口和 S2 页的 `boundary2 --negative-cases`。每个矩阵客户端运行原版移动提供器回放：

```text
S5_REPLAY assertions=10 presses=2 releases=4 passed=true provider=MovementInputFromOptions
S5_WORLD_FIXTURES peacefulDoor=true engagedDoor=false noToggle=true peacefulChest=true engagedChest=false configClosed=true villagerInteract=false protectedLoss=0.0 unprotectedLoss=2.0 leftBlocked=true passed=true
```

回放检查 W＋Shift 在平时为 0.3、交锋为 1.0；跳跃保留；保持 Shift 进入交锋、关闭 GUI、重新装备不产生按下；松开后重新按下生效；持续保持不重复刷新。仅有两次真实的新按下，映射切换没有新增精准时机。服务端实际开箱/开门在平时成功，交锋拒绝且门不再次翻转；禁止方块配置生效；村民不交互，保护 true 时损血 0，关闭后损血 2。左键方块事件被取消。鼠标意图只是夹具挥刀，没有退化实现完整攻击流。

关键负例：真实 Zombie 在服务器距玩家 4 格且设置玩家为 attackTarget，等待客户端收到实体后检查：

```text
S5_TARGET_SERVER entity=10 target=8 distance=4
S5_TARGET_REPLICA entityPresent=true localAttackTarget=false clientEngaged=false serverTargetConfirmed=true
```

## 结论与设计影响

移动输入包装和所测切换边沿可行；**不能在客户端直接读取怪物 `getAttackTarget()` 来判定交锋态**。实际 EntityLiving 的 attackTarget 是普通服务器字段，未通过 DataWatcher 同步；实体存在不表示 AI 目标可读。按该方案客户端会错误放行潜行/箱子，违反原型说明 8.1 的零容忍条件，因此整体 S5 为“需改方案”。

建议原型说明第 3 节与 input/network 模块补充：服务器根据动作、防御、锁敌、刚受击和敌对目标计算交锋态/迟滞，并同步明确的状态/修订；客户端只预测本地已知触发并按权威状态和解，服务端交互拦截使用同一判定。禁止用准星或客户端 AI 副本补洞。普通怪物的威胁同步、真实锁敌与完整动作承诺尚未建立，本次命令里的 engage/peace 只是状态夹具，不是正式状态机；没有悄悄将夹具视为 P0。

原型说明第 3 节首段“Shift 不再触发原版潜行”与随后平时态表格容易被误读；按用户已决定的优先级，包装只在持刀**交锋态**屏蔽潜行，平时态放行。保留原稿细节不删。

所有实体的右键保持重入口，左键始终攻击，不挖掘；没有 F 临时键。鼠标按下/释放与持续挖掘/GUI的完整人工游戏测试、短于一个 tick 的按键、服务器状态包晚到时的纠正、同队玩家/宠物组合、死亡/维度/断线及第三方移动提供器兼容均未验证。10 条回放断言不能当成 prototype 8.1 的全玩法 ≥95% 合法输入执行率。
