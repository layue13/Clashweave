# 战斗运行时与数据定义

> 整理日期：2026-10-08。当前讨论入口；已确认方向与提案分别标明，尚未实现或验证。
> 状态总表：[设计状态](../design-status.md)。原稿章节：[V1.0 设计对照](../archive/v1.0/runtime.md)。

## 继承的技术设计基线

所有普通动作、SA 和模式进入请求共用动作选择、调度、实例与时间轴。内容以动作图和风格配置表达，不在核心到处判断具体武器名称。

| 数据 | 职责 |
| --- | --- |
| ActionDefinition | 静态动作、窗口、轨迹和声明效果 |
| ActionGraph | 条件转换与候选路线 |
| ActionInstance | 本次执行的身份、进度与结果 |
| CombatState | 当前运行状态、资源与模式 |

静态定义与运行实例分开，临时模式修改作用于当前上下文，不随意改写全局动作图。成本、状态变化与动作提交需要一致；客户端表现不能执行服务端规则。

## 当前技术环境

项目已有 Forge 1.7.10 骨架，目标 Java 8。Gradle 与反编译工具使用不同 JDK，具体安装和已有构建验证见[项目 README](../../../README.md)。尚未实现战斗运行时。

原稿的 combat-core、combat-forge1710、combat-client、combat-content、combat-api 是逻辑拆分建议，不代表已经建立对应 Gradle 子项目。

## 依赖与待确认

先由[动作模块](actions.md)定义最小攻击流，再确定解析、调度、模式作用域和状态生命周期。JSON 模式、注册生命周期、条件/效果接口、持久化边界与同 tick 提交语义尚待设计。

下文保留原数据示例与 API 形态；它们仍不是已编译验证的接口契约。

## 原稿细节保留（设计基线，非实现承诺）

以下恢复原稿的具体设计内容，未被明确修改的机制继续作为继承提案，不因整理或精简操作而删除。数值、代码、接口、默认配置及技术断言仍需验证；本模块前文列出的用户调整优先。已调整的键位、单 SA 限制与三类爆气在摘录中改为当前边界，原文仍完整保留。

## 第十部分：底层技术架构

### 10.1 技术基线

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

### 10.2 推荐模块结构

```text
project-katana/
├── combat-core/
│   ├── action/       ActionDefinition, ActionGraph, Scheduler
│   ├── input/        CombatIntent, InputBuffer
│   ├── state/        CombatState, ActionInstance
│   ├── resource/     ResourceRegistry, ResourceContainer
│   ├── special/      SpecialAbility, ActionVariant
│   ├── burst/        模式配置与模式请求解析（名字待定）
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

### 10.3 运行态、静态定义和持久化

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

### 10.4 动作定义与有向图

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

### 10.5 JSON 配置示例

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
