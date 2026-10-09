# 原稿保留审计与遗漏修正

日期：2026-10-08。用户指出多处原稿设计被弱化，本页记录对完整 22 章的审计与恢复。原文未删除，原稿保留不等于所有细节已确认或验证。

本页是初轮恢复审计，后续接受的综合方案及本批审核候选以[设计状态](../design-status.md)和[审核入口](../review.md)为准；表中原稿来源和保留关系继续有效。

## 审计结论

此前把模块压成短摘要，再把原章标为历史，实际弱化了没有被用户要求删除的内容。具体遗漏包括轻重击与完整招式表、纳刀/拔刀和居合准备、切上/空中追击、方向派生、SA 多变体与动作图修改器、不同取消规则、动画绑定与反馈、状态字段、网络消息、扩展注册和体验指标。

对应细节现已重新进入负责模块。首套内容单独在[日本刀模块](../modules/katana.md)管理，通用调度与取消在[动作模块](../modules/actions.md)。未被明确调整的原设计继续作为继承提案，不能因“简单操作”或“首个原型”自动从最终内容中删除。

## 调整边界

- 用户要求重新审查输入复杂度、爆气定位和积攒方式；这些可以比较替代方案，但新推荐不自动成为确认规则。
- 用户明确 SA 融入操作，不能简化为单一独立技能；移除单槽/固定 V 的框架约束，保留多能力与变体设计。
- 用户认可弱跟随与可选自动第三人称；在原双视角基线上补充，不删除原版 F5、避障或动画方案。
- 原稿的数值、示例接口、版本 API 和所有原型目标仍待验证。恢复其设计地位不构成实现承诺。

## 22 章去向

| 原章 | 本轮处理 | 当前模块 |
| --- | --- | --- |
| 1. 项目定位与设计目标 | 具体机制已恢复为继承提案 | [overview](../modules/overview.md) |
| 2. 实际战斗体验与交战循环 | 具体机制已恢复为继承提案 | [overview](../modules/overview.md) |
| 3. 操作系统与输入语义 | 操作意图/派生恢复，物理键位与接管默认重审 | [input](../modules/input.md) |
| 4. 基础剑术系统 | 轻重、纳刀/居合、切上和原动作图恢复；取消见动作模块 | [katana](../modules/katana.md) |
| 5. 攻防、间合与崩势 | 具体机制已恢复为继承提案 | [defense](../modules/defense.md) |
| 6. SA 特殊攻击系统 | SA 类型/变体/示例恢复；单槽与单独按钮限制调整 | [sa](../modules/sa.md) |
| 7. Burst 爆气系统 | 用户提出问题，保留原方案对照，当前职责/经济继续重审 | [burst](../modules/burst.md) |
| 8. 双视角相机与动画设计 | 具体机制已恢复为继承提案 | [presentation](../modules/presentation.md) |
| 9. 操作反馈系统 | 具体机制已恢复为继承提案 | [presentation](../modules/presentation.md) |
| 10. 底层技术架构 | 具体机制已恢复为继承提案 | [runtime](../modules/runtime.md) |
| 11. 命中与运动系统 | 具体机制已恢复为继承提案 | [collision](../modules/collision.md) |
| 12. 服务端权威与客户端预测 | 具体机制已恢复为继承提案 | [network](../modules/network.md) |
| 13. Forge 1.7.10 渲染实现 | 具体机制已恢复为继承提案 | [presentation](../modules/presentation.md) |
| 14. 战斗 AI 设计 | 具体机制已恢复为继承提案 | [ai](../modules/ai.md) |
| 15. 框架扩展 API | 具体机制已恢复为继承提案 | [extension](../modules/extension.md) |
| 16. PvE、PvP 与服务器配置 | 具体机制已恢复为继承提案 | [extension](../modules/extension.md) |
| 17. 兼容性与异常处理 | 具体机制已恢复为继承提案 | [extension](../modules/extension.md) |
| 18. 性能目标 | 具体机制已恢复为继承提案 | [validation](../modules/validation.md) |
| 19. 开发计划与验收标准 | 原 M1–M7 内容恢复；SA/Burst及联机阶段标注调整 | [validation](../modules/validation.md) |
| 20. 玩家体验验证 | 具体机制已恢复为继承提案 | [validation](../modules/validation.md) |
| 21. 完整战斗示例 | 有资源冲突，完整例子保留于原稿，待修订后重新进入验收 | [validation](../modules/validation.md) |
| 22. V1.0 范围、风险与架构结论 | 具体机制已恢复为继承提案 | [validation](../modules/validation.md) |

## 逐节保留记录

以下对应原稿编号或里程碑；章节 18、21 无编号小节，见上表。动作原章 4.4 的具体取消表同时恢复至[动作模块](../modules/actions.md)。

| 原节 | 保留/调整情况 | 入口 |
| --- | --- | --- |
| 1.1 项目概述 | 恢复原稿细节，继续作为待验证设计基线 | [overview](../modules/overview.md) |
| 1.2 产品与引擎分层 | 恢复原稿细节，继续作为待验证设计基线 | [overview](../modules/overview.md) |
| 1.3 六条不可妥协的原则 | 恢复原稿细节，继续作为待验证设计基线 | [overview](../modules/overview.md) |
| 2.1 战斗循环 | 恢复原稿细节，继续作为待验证设计基线 | [overview](../modules/overview.md) |
| 2.2 普通 Minecraft 实体的兼容层级 | 恢复原稿细节，继续作为待验证设计基线 | [overview](../modules/overview.md) |
| 3.1 默认按键 | 旧键位表重审；轻重、纳刀等动作意图保留，物理映射未定 | [input](../modules/input.md) |
| 3.2 抽象输入意图 | 恢复原稿细节，继续作为待验证设计基线 | [input](../modules/input.md) |
| 3.3 输入形式与缓冲 | 缓冲策略保留；原 tick 值为测试参考，Burst 类别改为明确模式请求 | [input](../modules/input.md) |
| 3.4 Minecraft 原生交互的冲突解决 | 两种接管方案保留对比，原默认值重审 | [input](../modules/input.md) |
| 3.5 方向攻击与目标辅助 | 方向派生保留；F/X/G 示例改为语义输入 | [input](../modules/input.md) |
| 4.1 打刀的两种武器状态 | 纳刀/拔刀与居合恢复保留；原 R/左键映射不冻结 | [katana](../modules/katana.md) |
| 4.2 基础动作初始数值 | 完整招式与初始参数表恢复；数值仍待验证 | [katana](../modules/katana.md) |
| 4.3 第一版动作图 | 原轻重动作图恢复；去掉对旧键表的依赖 | [katana](../modules/katana.md) |
| 4.4 取消类型与基本约束 | 命中/挥空与六类转换保留；Burst 取消权限单独重审 | [katana](../modules/katana.md) |
| 5.1 三种防御结果 | 恢复原稿细节，继续作为待验证设计基线 | [defense](../modules/defense.md) |
| 5.2 间合与攻击路径 | 恢复原稿细节，继续作为待验证设计基线 | [defense](../modules/defense.md) |
| 5.3 架势稳定度 | 恢复原稿细节，继续作为待验证设计基线 | [defense](../modules/defense.md) |
| 5.4 高速追击与抗无限连 | 恢复原稿细节，继续作为待验证设计基线 | [defense](../modules/defense.md) |
| 6.1 SA 的定位 | 能力类型保留；一武器一 SA/V 键不作为框架限制 | [sa](../modules/sa.md) |
| 6.2 SA 多上下文变体 | 多上下文变体表恢复；按明确请求而非单键猜测选技 | [sa](../modules/sa.md) |
| 6.3 首个 SA：次元斩 | 次元斩具体设计与原型参数恢复，非最终能力清单 | [sa](../modules/sa.md) |
| 6.4 SA 是连招的转换器 | SA 动作图修改器保留；形态入口与爆气职责继续协调 | [sa](../modules/sa.md) |
| 7.1 目标与资源 | 爆气旧规格重审；原文完整保留，不把助手替代方案当作确认 | [burst](../modules/burst.md) |
| 7.2 防御 Burst（Defensive Burst） | 爆气旧规格重审；原文完整保留，不把助手替代方案当作确认 | [burst](../modules/burst.md) |
| 7.3 进攻 Burst（Offensive Burst） | 爆气旧规格重审；原文完整保留，不把助手替代方案当作确认 | [burst](../modules/burst.md) |
| 7.4 中立 Burst（Neutral Burst） | 爆气旧规格重审；原文完整保留，不把助手替代方案当作确认 | [burst](../modules/burst.md) |
| 7.5 Burst 优先级与统一执行 | 爆气旧规格重审；原文完整保留，不把助手替代方案当作确认 | [burst](../modules/burst.md) |
| 7.6 Burst 与 SA 的边界 | 爆气旧规格重审；原文完整保留，不把助手替代方案当作确认 | [burst](../modules/burst.md) |
| 8.1 统一规则、两套表现 | 恢复原稿细节，继续作为待验证设计基线 | [presentation](../modules/presentation.md) |
| 8.2 四个方向量必须解耦 | 恢复原稿细节，继续作为待验证设计基线 | [presentation](../modules/presentation.md) |
| 8.3 第一人称相机规范 | 原第一人称规范保留，结合用户认可的弱跟随 | [presentation](../modules/presentation.md) |
| 8.4 第三人称与高级动作相机 | 原版/越肩、避障与参数保留；增加弱跟随与可选自动第三人称 | [presentation](../modules/presentation.md) |
| 8.5 共享 Gameplay Timeline 与独立表现 | 恢复原稿细节，继续作为待验证设计基线 | [presentation](../modules/presentation.md) |
| 8.6 动画制作工作流 | 恢复原稿细节，继续作为待验证设计基线 | [presentation](../modules/presentation.md) |
| 9.1 反馈由语义事件驱动 | 语义反馈表恢复；Burst 反馈按模式职责重审 | [presentation](../modules/presentation.md) |
| 9.2 Hitstop 与服务器逻辑隔离 | 恢复原稿细节，继续作为待验证设计基线 | [presentation](../modules/presentation.md) |
| 9.3 可访问性与可调节性 | 恢复原稿细节，继续作为待验证设计基线 | [presentation](../modules/presentation.md) |
| 10.1 技术基线 | 恢复原稿细节，继续作为待验证设计基线 | [runtime](../modules/runtime.md) |
| 10.2 推荐模块结构 | 恢复原稿细节，继续作为待验证设计基线 | [runtime](../modules/runtime.md) |
| 10.3 运行态、静态定义和持久化 | 恢复原稿细节，继续作为待验证设计基线 | [runtime](../modules/runtime.md) |
| 10.4 动作定义与有向图 | 恢复原稿细节，继续作为待验证设计基线 | [runtime](../modules/runtime.md) |
| 10.5 JSON 配置示例 | 恢复原稿细节，继续作为待验证设计基线 | [runtime](../modules/runtime.md) |
| 11.1 连续刀刃扫掠 | 恢复原稿细节，继续作为待验证设计基线 | [collision](../modules/collision.md) |
| 11.2 移动分类与 Root Motion | 恢复原稿细节，继续作为待验证设计基线 | [collision](../modules/collision.md) |
| 11.3 全局分阶段战斗 Tick | 恢复原稿细节，继续作为待验证设计基线 | [collision](../modules/collision.md) |
| 12.1 职责与安全边界 | 恢复原稿细节，继续作为待验证设计基线 | [network](../modules/network.md) |
| 12.2 网络消息协议（建议） | 恢复原稿细节，继续作为待验证设计基线 | [network](../modules/network.md) |
| 12.3 预测与校正 | 恢复原稿细节，继续作为待验证设计基线 | [network](../modules/network.md) |
| 12.4 RTT 与精准格挡 | 恢复原稿细节，继续作为待验证设计基线 | [network](../modules/network.md) |
| 12.5 网络线程和输入安全 | 恢复原稿细节，继续作为待验证设计基线 | [network](../modules/network.md) |
| 13.1 可用的集成点 | 恢复原稿细节，继续作为待验证设计基线 | [presentation](../modules/presentation.md) |
| 13.2 渲染兼容原则 | 恢复原稿细节，继续作为待验证设计基线 | [presentation](../modules/presentation.md) |
| 14.1 玩家与 AI 共享 ActionScheduler | 恢复原稿细节，继续作为待验证设计基线 | [ai](../modules/ai.md) |
| 14.2 最小 AI 模型 | 恢复原稿细节，继续作为待验证设计基线 | [ai](../modules/ai.md) |
| 14.3 两种测试对象 | 恢复原稿细节，继续作为待验证设计基线 | [ai](../modules/ai.md) |
| 15.1 三级扩展 | 三级扩展保留；API 冻结时机未定 | [extension](../modules/extension.md) |
| 15.2 API 概念草案 | 恢复原稿细节，继续作为待验证设计基线 | [extension](../modules/extension.md) |
| 15.3 注册表和命名空间 | 恢复原稿细节，继续作为待验证设计基线 | [extension](../modules/extension.md) |
| 15.4 客户端/服务端内容校验 | 恢复原稿细节，继续作为待验证设计基线 | [extension](../modules/extension.md) |
| 16.1 PvE | PvE 完整要求保留；防御 Burst 不直接继承 | [extension](../modules/extension.md) |
| 16.2 PvP | PvP 完整保护要求保留；脱困与模式职责重审 | [extension](../modules/extension.md) |
| 16.3 配置示例 | 配置形态保留，资源上限与字段为示例 | [extension](../modules/extension.md) |
| 17.1 必须定义的生命周期 | 恢复原稿细节，继续作为待验证设计基线 | [extension](../modules/extension.md) |
| 17.2 兼容优先级 | 恢复原稿细节，继续作为待验证设计基线 | [extension](../modules/extension.md) |
| Milestone 1：基础操作原型 | 恢复原稿细节，继续作为待验证设计基线 | [validation](../modules/validation.md) |
| Milestone 2：双视角与动画 | 恢复原稿细节，继续作为待验证设计基线 | [validation](../modules/validation.md) |
| Milestone 3：攻防与崩势 | 恢复原稿细节，继续作为待验证设计基线 | [validation](../modules/validation.md) |
| Milestone 4：SA | 多入口 SA 阶段保留；不约束单 SA 按键 | [validation](../modules/validation.md) |
| Milestone 5：Burst | 阶段目标重审：三类 Burst 改为积攒/模式原型，未定案 | [validation](../modules/validation.md) |
| Milestone 6：第二武器验证框架 | 恢复原稿细节，继续作为待验证设计基线 | [validation](../modules/validation.md) |
| Milestone 7：多人压测与兼容 | 恢复原稿细节，继续作为待验证设计基线 | [validation](../modules/validation.md) |
| 20.1 三种必测环境 | 三种必测场景恢复，爆气部分随职责调整 | [validation](../modules/validation.md) |
| 20.2 推荐体验指标 | 完整体验指标恢复，三类 Burst 分布改为模式机会指标 | [validation](../modules/validation.md) |
| 22.1 V1.0 与后续版本的边界 | 原范围矩阵恢复；Burst 行按新方向修正 | [validation](../modules/validation.md) |
| 22.2 已知关键风险 | 原风险清单恢复，控制保护不默认依赖防御 Burst | [validation](../modules/validation.md) |
| 22.3 最终架构 | 原架构与三项基线恢复；撤去已冻结接口的暗示 | [validation](../modules/validation.md) |

共核对 22 章、76 个编号小节/里程碑。所有原章仍完整保存于[原稿分章目录](../archive/v1.0/README.md)。

## 后续维护

先对照原稿确认当前机制如何保留，再讨论修改理由。文档整理可以改变结构，不能未经说明缩减内容。原型暂不实现某功能要标注阶段后置，不把它从总体设计中删除；未确认的两按钮语义、一条气槽或模式消费策略也不能覆盖原稿全部机制。
