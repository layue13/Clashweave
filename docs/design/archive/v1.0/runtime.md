# 历史 V1.0：战斗运行时与数据定义

> 原稿对照资料，归档不表示整章废止。对应[完整基线](../../clashweave-v1.0.md)第 10 章；后续讨论以[现行模块](../../modules/runtime.md)为入口。正文中的键位、数值和“冻结”措辞不代表当前结论。

# 第十部分：底层技术架构

## 10.1 技术基线

| 组件 | 建议 |
|---|---|
| Minecraft / Forge | 1.7.10 / 10.13.4.1614 |
| Java | Java 8 兼容基线 |
| 核心模拟 | 与 Minecraft 类解耦的普通 Java |
| 网络 | Forge `SimpleNetworkWrapper` |
| 附加实体持久化 | `IExtendedEntityProperties` 等 1.7.10 机制 |
| 动作资源 | JSON，启动时编译为不可变定义 |
| 服务端时间轴 | 固定逻辑 tick（通常 20 TPS） |
| 客户端动画 | 渲染帧插值 |
| 自动测试 | JUnit ＋ Forge 集成测试 |

## 10.2 推荐模块结构

```text
project-katana/
├── combat-core/
│   ├── action/       ActionDefinition, ActionGraph, Scheduler
│   ├── input/        CombatIntent, InputBuffer
│   ├── state/        CombatState, ActionInstance
│   ├── resource/     ResourceRegistry, ResourceContainer
│   ├── special/      SpecialAbility, ActionVariant
│   ├── burst/        BurstProfile, BurstResolver
│   ├── collision/    HitShape, HitTrace, CollisionResolver
│   └── combat/       Damage, Posture, Defense, Resolver
├── combat-api/       public contract and registries
├── combat-forge1710/
│   ├── entity/       Minecraft Entity adapter
│   ├── network/      packet and threading bridge
│   ├── event/        input / tick / lifecycle bridge
│   └── movement/     collision and position bridge
├── combat-client/
│   ├── animation/ camera/ hud/ rendering/ effects/
├── combat-content/
│   ├── katana/ styles/ specials/ enemies/
└── combat-tests/
```

此处是**逻辑分层**；实际 Gradle 子工程可更少，避免 Forge 1.7.10 构建链过度复杂。

## 10.3 运行态、静态定义和持久化

```java
public final class CombatState {
    public WeaponState weaponState;
    public StanceState stanceState;
    public ActionInstance currentAction;
    public DefenseState defenseState;
    public MovementState movementState;
    public CombatResources resources;
    public int stateRevision;
}

public final class ActionInstance {
    public int instanceId;
    public int actionId;
    public long startTick;
    public int elapsedTicks;
    public int sourceInputSequence;
    public ActionResult lastResult;
}
```

以上是结构示意，不能视为可直接编译的最终 API。实际实例还需追踪当前动作内已命中目标、资源保留、取消结果及网络确认记录。武器 NBT 保存耐久、附魔、内容标识及需要持久化的内容；玩家战斗运行态保存实时动作。死亡、重生、跨维度时重建瞬时运行态，显式复制应继承的持久资源。

## 10.4 动作定义与有向图

```text
Action Node
├── 静态 ActionDefinition
└── 运行中的 ActionInstance

Transition Edge
├── 触发 CombatIntent
├── TimeWindow
├── StateCondition
├── ResultCondition（命中 / 格挡 / 挥空）
├── ResourceCondition
├── Priority / Exclusivity
└── Next Action
```

`ActionResolver` 按状态选候选，`ActionScheduler` 作为**唯一动作提交入口**验证并完成转换。避免普通攻击、SA、Burst、位移各维护互相冲突的状态机。不同状态域正交存在，但状态冲突由调度器集中裁决。

## 10.5 JSON 配置示例

```json
{
  "id": "katana:horizontal_slash",
  "category": "NORMAL",
  "duration": 13,
  "timeline": {
    "startup": [0, 4],
    "active": [4, 6],
    "recovery": [6, 13]
  },
  "hit": {
    "shape": "katana:horizontal_arc",
    "damageMultiplier": 1.0,
    "postureDamage": 12,
    "maxHitsPerTarget": 1
  },
  "movement": { "profile": "katana:light_step" },
  "transitions": [
    {
      "input": "LIGHT_ATTACK",
      "target": "katana:return_slash",
      "window": [7, 11],
      "condition": "ON_HIT",
      "priority": 100
    },
    {
      "input": "HEAVY_ATTACK",
      "target": "katana:kesagiri",
      "window": [9, 13],
      "priority": 50
    }
  ],
  "presentation": {
    "firstPerson": "katana:horizontal_fp",
    "thirdPerson": "katana:horizontal_tp"
  }
}
```

所有时间区间均为左闭右开；JSON 只是自定义建议 Schema。加载时校验区间合法性、目标动作存在性、互斥规则、相同优先级歧义、资源 ID 及动画引用，然后编译为不可变定义。第一版不开放正在战斗中的任意热重载。

---
