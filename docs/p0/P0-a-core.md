# P0-a 核心调度与数据

从最新 `work`（6814404）建立 `prototype/p0-vertical`。生产代码重写于 `core` 包，未合并实验源集。Java 8；核心无 Minecraft 引用。

动作 JSON 明示六个节点、时序、轨迹参数、位移和半开边窗口。横斩沿用 `[7,11)`；其它边窗口为本次可调起点。每类缓冲 4 tick，绑定原实例/目标边，执行前复验；无对应节点、过期、中断和冲突均反馈拒绝。返刃重击的切上未实现，拒绝而不改成重击。缺失原稿完整时序的空中追击、普通回避和模式明确列为后置，未用零恢复补齐。

命令：`$env:JAVA_HOME='C:\Program Files\Zulu\zulu-25'; $env:VERSION='0.1.0-dev'; .\gradlew.bat spotlessApply test check --no-configuration-cache`。

实测：`BUILD SUCCESSFUL in 14s`，30 tasks；JUnit **6 tests、0 failures、0 skipped**，非 NO-SOURCE。覆盖过期派生不回退、账本按目标/段去重及结束清理、最新有效缓冲、非法节点/切上拒绝、中断失效，以及 20 种输入遍历排列下的冻结交换命中。原始结果见 [JUnit XML](evidence/p0-a-junit.xml)。Spotless 与 Checkstyle 通过。

限制：这里验证核心调度和候选统一排序；实际 Forge 伤害、联网生命周期、双方交换命中仍须 P0-b/c 验证。未把单元测试或构建当玩法验收。数据结构是内部实现，未冻结公开 API。

设计取舍：现行用户范围优先于模块旧完整路线；后置节点保留声明。除横斩外，原型第 5 节未给其它边窗口，本次数据提供候选值，不声称平衡已审。P0-b/c 尚在实施。
