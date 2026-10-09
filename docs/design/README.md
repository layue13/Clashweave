# Clashweave 设计文档

此目录以[原分享讨论](https://chatgpt.com/share/6ac81533-4cd0-83e8-86d8-fad07e0491c5)为基础，整合本轮明确的调整。整理日期：2026-10-08。

## 阅读顺序

本次提交用于用户审核，审核后再委派原型。先读[本轮审核入口](review.md)与[原型任务说明](proposals/prototype-spec-v1.md)，然后按需要查模块和原稿。

1. [设计状态](design-status.md)：已确认方向、继承提案与未实现边界。
2. [现行交接](handoff.md)：恢复当前上下文。
3. [原讨论对照](sources/original-discussion.md)：最初方向与后续调整的依据。
4. [原稿保留审计](reviews/baseline-retention.md)：22 章、76 个小节/里程碑的保留与调整记录。
5. 模块正文：当前方向及恢复的原稿详细设计。
6. [待讨论问题](open-questions.md)：跨模块依赖和未定事项。

## 现行模块

| 模块 | 负责内容 | 当前状态 |
| --- | --- | --- |
| [项目定位](modules/overview.md) | 独立框架、体验目标、内容范围 | 方向明确，首套范围待定 |
| [日本刀与拔刀剑术](modules/katana.md) | 居合、纳刀、轻重击、切上与空中追击 | 恢复原稿内容骨架，输入/窗口待定 |
| [输入](modules/input.md) | 玩家意图、锁敌、键位、原版交互 | 重新推导，键位未定 |
| [动作与攻击流](modules/actions.md) | 动作图、窗口、路线、收束 | 完整攻击流方向明确，具体图待定 |
| [防御](modules/defense.md) | 格挡、反击、架势、控制保护 | 继承提案，参数未定 |
| [SA](modules/sa.md) | 多能力/入口、派生、变体与成本 | 融入操作的方向明确，清单未定 |
| [Burst](modules/burst.md) | 辅助模式、交锋积攒与消费 | 职责已调整，具体规则未定 |
| [视角与表现](modules/presentation.md) | 弱跟随、自动第三人称、动画与反馈 | 配置方向明确，细则待验证 |
| [模型与资源制作](modules/assets.md) | Blender、刀/刀鞘、挂点、动画与轨迹导出 | 工具与管线推荐，待实际导入验证 |
| [运行时](modules/runtime.md) | 定义、实例、调度与状态 | 原稿架构基线，未实现 |
| [命中与运动](modules/collision.md) | 轨迹、碰撞、转向与位移 | 技术提案，未验证 |
| [网络](modules/network.md) | 权威、预测、同步与线程 | 技术提案，未验证 |
| [AI](modules/ai.md) | 共享动作流、测试敌人与决策 | 技术提案，未实现 |
| [扩展与兼容](modules/extension.md) | 风格组合、配置、API 与生命周期 | 方向继承，接口未冻结 |
| [验证与阶段](modules/validation.md) | 体验验收、原型与压力目标 | 随攻击流重新拆分 |

## 历史与过程资料

用户已接受[原稿攻击流上的推荐方案](proposals/combat-recommendation-v1.md)作为实施基线：保留原稿剑术，以纳刀重整间合，SA 附加效果与主动变体并存，气主要用于临时模式。具体样例、输入、装配、参数仍需细化和验证。

实施前约定见[实施准备](proposals/implementation-readiness-v1.md)，刀模型与 Blender 流程见[资源制作模块](modules/assets.md)。目前仍是设计准备，尚未开始战斗代码或模型制作。

较早的[气、爆气模式与攻击流](proposals/resource-mode-v1.md)保留资源推导，其中“只少量改图”的能力范围不再当作范围定案，脱战衰减也保留为备选。具体当前规则归属以模块和设计状态为准。

- [完整 V1.0 基线](clashweave-v1.0.md)与[原稿分章目录](archive/v1.0/README.md)：保留全部 22 章；归档不表示废止，未明确修改的机制继续作为继承设计提案，数值和已调整项单独标注。
- [原交接说明](archive/v1.0/handoff.md)：保留原讨论上下文，当前交接已单独重写。
- [交互 V2](proposals/interaction-v2.md)、[攻击流 V2](proposals/attack-flow-v2.md)：讨论过程与取舍，当前状态以模块正文为准。
- [爆气审查](reviews/burst-review.md)、[积攒讨论](proposals/burst-discussion.md)、[相机讨论](proposals/presentation-discussion.md)：保留分析和候选细则。

## 维护约定

模块正文维护当前方向及原稿细节，明确区分用户确认、继承提案、助手新建议和验证结果。跨模块规则引用负责模块。阶段性提案保留推导过程，原稿文本不随讨论改写。

文档整理不缩减设计，减少按键不删除动作与战术选择。若原型暂不实现某项内容，应标注阶段后置；修改机制须说明依据，未经确认的新建议不能替代整套原稿。

具体键位、招式清单、资源公式、时间窗和 API 尚未冻结。原型说明补齐了可供审核的输入/交互候选、原稿时序与交付范围。当前只有 Forge 骨架；这些文档不表示功能已经接入代码或通过玩法测试。
