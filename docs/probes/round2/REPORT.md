# 第二轮探针：S1b 失败，停止等待审核

从 PR #4 已合并的最新 `work`（83d6eb8）建立 `probe/round2`。首轮分支只作源码参考，没有 cherry-pick/merge，没有将探针合入 `src/main`。P0 未开始。

| 探针 | 状态 | 实测或停止原因 |
| --- | --- | --- |
| [S1b 伤害](S1b-damage.md) | 失败 | 同一 LivingAttackEvent 递归 generic 4 点：原版拒绝/损血 0，窄作用域接受/损血 4；最终字段已恢复 |
| [S2b 位移](S2b-movement.md) | 未执行 | S1b 失败后按闸门停止 |
| [S3b 格挡](S3b-guard.md) | 未执行 | 同上；0ms age=2 来源未验证 |
| [S5b 交锋态](S5b-engagement.md) | 未执行 | 同上 |
| [S4b 握持](S4b-grip.md) | 未执行 | 同上；无新截图 |

## 第 8 节：构建、运行与限制

Windows、Zulu Java 25 启动 Gradle 9.7.1；Zulu Java 8u504 编译/运行 Minecraft 1.7.10 / Forge 10.13.4.1614，GTNH 2.0.33。未升级依赖。本轮 `round2LaunchFile build --no-configuration-cache` 成功（28 tasks，含实验编译与 Checkstyle、生产 Spotless/Checkstyle）；`test` 为 NO-SOURCE，未宣称 JUnit 通过。构建输出见 [build.log](evidence/build.log)。

实际运行一场新建 WorldServer 专用服务器，真实原版实体伤害、Forge 事件与递归调用；不是伤害模拟器，也没有真实客户端/玩家进入此场景。服务器正常保存/停止，exit=0，实际 class-load trace 的 Minecraft 客户端/OpenGL 类加载=0。原版初次空目录的列表文件缺失警告、Forge 版本检查 JSON 异常与开发签名警告保留在 [server.log](evidence/s1b-reentry/server.log)，没有把它们隐藏或当成功能通过。

实验源集只由显式 `-I tools/probes2/experiment.gradle` 创建，独立实验 jar 仅复制进新建测试服务器 mods；正常构建不注册实验源集。`src/main`、生产构建配置和主模组入口没有改动。生产 jar 内容隔离结果见 [packaging.json](evidence/packaging.json)。默认构建与实验 jar 分离，没有冻结公开 API。

另外运行不带 init script 的 `.\gradlew.bat build --no-configuration-cache`，`BUILD SUCCESSFUL in 1s`、24 tasks up-to-date，见 [normal-build.log](evidence/normal-build.log)。用 Python `zipfile.ZipFile(...).namelist()` 检查发布/dev/sources 三个 jar 的 `/probe/`、`/experiments/`、`cw_s1b`、`tools/probes2` 条目均为 `[]`；`git diff origin/work -- src/main build.gradle.kts dependencies.gradle` 输出为空。包哈希与这些检查结果记录在 packaging.json。

未运行死亡路径、玩家目标、异常恢复与账本清理：在 S1b 的第一个递归一致性必要条件上已失败，依用户要求停止。后续探针及 P0 的动作流、同步、输入、资源和美术均未实现/未验证。没有用连点加技能替代完整攻击流，SA、模式、切上/空中追击、第二武器、正式美术仍后置保留。

## 第 8.1 节：实测与验收差距

| 指标 | 本轮实测/差距 |
| --- | --- |
| S1b 其它来源递归一致 | 不满足：对照拒绝，候选接受且损血 4；一组同步配对已构成反例 |
| 专用服务器加载客户端类 | 所测单场路径 0 次；不外推到未实现的客户端/玩家场景 |
| 合法输入执行率 ≥95% | 未验证；无动作/缓冲实现 |
| 过期派生拒绝 100% | 未验证；无调度器/JUnit |
| 单次最长受控时间 | 未验证；无控制逻辑 |
| 100ms 预测纠正频率 | 未验证；S2b 未执行 |
| 最小格挡提前量 vs 单程延迟 | 未验证；S3b 未执行，固定 age=2 来源未查明 |
| 交锋误判、交锋中开箱/潜行 | 未验证；S5b 未执行 |
| 服务端战斗 tick 耗时 | 未验证；不把启动/夹具运行时间当玩法性能 |

## 设计影响与优先级

原型说明 1.1/S1 的外层临时字段修改不足以实现递归隔离；1.2/S1b 的一致性标准没有改变。建议改成来源/调用帧限定的伤害门绕过，并在下一轮评估实际接入与回调可见性；具体代价和未验证方案见 S1b 报告。没有静默取消递归伤害、放宽通过标准或实现未经审核的替代方案。

按用户本次停止条件和 1.2 执行，优先于第 8 节沿用首轮“全部有结论后进入 P0”的旧阶段表述。未执行项如实标记未验证，不强行判成通过/失败。所有模块“原稿细节保留”未删除或改为链接；第 3 节 Shift 首段继续按用户的平时态例外取舍。等待本次失败项审核后再决定如何继续。
