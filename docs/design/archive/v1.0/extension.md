# 历史 V1.0：扩展 API、配置与兼容

> 原稿对照资料，归档不表示整章废止。对应[完整基线](../../clashweave-v1.0.md)第 15、16、17 章；后续讨论以[现行模块](../../modules/extension.md)为入口。正文中的键位、数值和“冻结”措辞不代表当前结论。

# 第十五部分：框架扩展 API

## 15.1 三级扩展

| 等级 | 目标作者 | 能力 |
|---|---|---|
| Level 1 数据扩展 | 内容包作者 | 武器、招式、动作图、SA、轨迹、动画引用、Burst Profile |
| Level 2 Java API | 复杂附属作者 | 自定义条件、效果、资源、输入映射、AI 策略 |
| Level 3 子系统插件 | 高级开发者 | 新碰撞体、渲染适配、运动模型、特殊资源规则 |

第一版应稳定核心 Level 1/2 注册接口，不必立即公开任意替换底层服务器计算的危险能力。

## 15.2 API 概念草案

```java
public interface CombatStyle {
    String getId();
    ActionGraph getActionGraph();
    MovementProfile getMovementProfile();
    DefenseProfile getDefenseProfile();
    BurstProfile getBurstProfile();
}

public interface SpecialAbility {
    String getId();
    ActionDefinition resolve(CombatContext context);
}

public interface CombatInputProvider {
    InputFrame sample(CombatContext context);
}

public interface ActionCondition {
    boolean test(CombatContext context);
}

public interface ActionEffect {
    void execute(CombatContext context);
}
```

这些代码是结构意图，不是经过测试的最终编译接口。最终 API 需要区分只读上下文、服务端可提交效果、客户端纯表现回调，并明确定义注册和生命周期。

## 15.3 注册表和命名空间

```text
ActionRegistry       StyleRegistry       SpecialRegistry
BurstRegistry        ResourceRegistry    AnimationRegistry
EffectRegistry
```

示例 ID：`katana:iai_slash`、`katana:dimensional_slash`、`spear:thrust`、`greatsword:heavy_cleave`。注册完成后冻结静态定义；运行时不允许无控制地覆盖正在执行的动作。

## 15.4 客户端/服务端内容校验

将资源分为：

- **Gameplay Manifest**：动作图、碰撞轨迹、合法时间窗、资源及战斗规则；必须与服务端权威版本匹配。
- **Presentation Manifest**：动画、音效、刀光、相机预设；可以有受控的客户端视觉差异。

V1.0 连接时验证内容 ID、版本或规则哈希；暂不实现完整远程下载或战斗中热更新。

---

# 第十六部分：PvE、PvP 与服务器配置

## 16.1 PvE

保证原版僵尸、骷髅和其他普通怪物可以直接被战斗武器攻击。范围攻击可以命中多实体，但不应让普通怪物无限击飞。Boss 可自定义受击和崩势，防御 Burst 可提供有限的被围攻脱困收益。

## 16.2 PvP

PvP 更强调反制：连招受控时长上限、伤害衰减、目标浮空保护、Burst 脱困、防御判定公平性与服务端位移校验。先手获得合理收益，但不能“一次命中控制到死”。PvE / PvP 可使用不同倍率及控制保护，但同一个按键的基础语义应相同。

## 16.3 配置示例

```ini
# combat-server.cfg （概念示例）
[combat]
enableCombat=true
enablePvPCombat=true

[defense]
enablePosture=true
enablePerfectGuard=true

[burst]
enableBurst=true
maxBurst=100

[pvp]
enableComboProtection=true
enableLaunchProtection=true

[network]
enableLagCompensation=false

[debug]
enableCombatLogging=false
```

服务器决定规则；客户端设置只影响表现、按键和可访问性，不得调整攻击范围、资源消耗、格挡时窗或伤害。

---

# 第十七部分：兼容性与异常处理

## 17.1 必须定义的生命周期

死亡、重生、维度切换、下线重连、换刀、扔掉武器、打开 GUI、骑乘、传送、实体卸载、战斗中掉落物品。对每一种情况制定：动作取消、预测清理、资源持久化、追击预算重置、动画停止与服务器重新同步规则。

例如跨维度不得继承原维度的突进斩；玩家实体替换时只继承明确允许的持久数据，而不是整个 `ActionInstance`。

## 17.2 兼容优先级

优先兼容标准 Forge 伤害链、原版实体、普通装备及非渲染类内容模组。专项测试自定义玩家模型、第一人称手臂、相机、动作核心修改和实体碰撞盒。高级相机与专门渲染注入提供显式降级开关，而不能使核心战斗全部无法运行。

---
