S1b 的真实 Forge 递归施伤对照推翻了“在整个 attackEntityFrom 调用外临时清计时并 finally 恢复即可隔离其它来源”的候选。同样的 LivingAttackEvent 回调，原版 generic 4 点被拒绝，候选组接受并损血 4；最终 timer/lastDamage 已正确恢复。按用户与原型说明 1.2 的停止条件，S2b/S3b/S5b/S4b 及 P0 未执行，等待审核。

从已合并 PR #4 的最新 work / 83d6eb8 建分支。首轮代码只作参考，没有合并；本轮重写实验在 tools/probes2/java 独立源集，显式 init script 才构建独立实验 jar。src/main、生产构建配置没有接入实验。

### 第 8 节：构建与实际运行

- Java 25 / Gradle 9.7.1，Java 8u504 / Forge 10.13.4.1614；实验编译、Checkstyle、生产 Spotless/Checkstyle 与 build 通过；test=NO-SOURCE。
- 实际一场新建专用服务器 WorldServer、EntityCow、原版 attackEntityFrom / LivingAttackEvent 递归对照；exit=0，客户端/OpenGL class loads=0。无客户端/玩家进入该场景。
- 固定剂量为实验边界负载，未冻结玩法数值/API。生产 jar 内容隔离已检查；首轮与本轮夹具均未并入生产源码。
- 原版首次空目录警告、Forge 版本查询异常与开发签名警告保留。死亡、玩家目标、异常恢复、账本清理未跑：首个必要条件失败即停。无新资源/截图，没有声称握持通过。

### 第 8.1 节：实测与差距

| 指标 | 实测与限制 |
| --- | --- |
| S1b 递归其它来源一致 | 失败：control loss=0，scoped loss=4；正常返回后的字段恢复=true |
| 专用服务器加载客户端类 | 本轮一场所测路径 0 |
| 合法输入执行率、过期派生、最长控制 | 未验证，P0 未开始 |
| 位移纠正、最小格挡提前量、交锋误判 | 未验证，后续探针未执行；0ms age=2 来源仍待查 |
| 战斗每 tick 耗时 | 未验证，不填造性能数字 |

建议审核来源/调用帧限定的伤害门接入，再验证递归、死亡、玩家与异常边界；未擅自取消递归、改异步或降低标准。设计文档仅加失败指针，保留“原稿细节保留”和全部后置范围。持刀键位、可配置与待确认默认值约束未改变。

逐项短报告、复现命令、实测输出与源码/产物哈希见 docs/probes/round2/REPORT.md。该 PR 在失败闸门交付后停止，等待用户审核，不进入 P0。
