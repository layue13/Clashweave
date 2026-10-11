# 连段窗口的有上限延迟补偿

## 原因与实现

JUMP-WINDOWS 中实例 21 在 902 开始，906 按键、912 收包、913 消费：消费年龄 11 超过 light_2 的半开窗口 until=10。现在只对晚到侧使用 `tick - compensation - start < until`；补偿为 `max(0, min(receiptAge, inputEdgeCompensationTicks))`。

生产入口仍先通过 InputGate.validate；采用已有 `receivedTick - InputGate.effectiveStamp(stamp, receivedTick)`，与输入门的 `receivedTick - stamp` 年龄等价，容差内的未来 stamp 夹值后年龄为 0。没有新增客户端字段或协议变更；收包到消费的排队时间不计入收包年龄。

Scheduler 新增带收包年龄和上限的 request 重载，每个请求只计算并保存一次固定补偿；request 选边及 tick 的 until / WINDOW_CLOSED 判断使用相同固定量，不累计。旧调用使用 0。from、bufferTicks / expires、confirmedHit、origin、idle、counter 均保持原规则。动作开始仍是实际执行 tick；未修改精准格挡、输入门、位移、朝向、攻击辅助、扫掠或相机。

## 配置

`network.inputEdgeCompensationTicks` 默认 4，范围 0–8，0 关闭；加载时再夹到 `network.stampAgeLimit`。配置注释说明收包年龄来源、关闭方式、上限防止高延迟无限放宽窗口，以及不得超过 stampAgeLimit。

## 已验证：纯逻辑

先写测试，并仅添加转发旧行为的 API 壳，直接运行 SchedulerTest：实际输出 `Tests run: 14, Failures: 3`，其中复现和上限测试均为预期 START、实际 ILLEGAL_NODE。随后实现补偿。首次测试还暴露了测试夹具在 duration=12 时结束的问题；针对独立 until / 不累计测试延长夹具 recovery，真实 902→913 复现仍使用原动作数据。

最后使用 `javac --release 8`，JUnit 4.13.2、hamcrest-core 1.3、Gson 2.10（均来自 `/opt/gradle-8.14.3/lib`），编译全部 core 和纯逻辑 presentation 源码及下列 core 测试。实际输出 `OK (66 tests)`，失败 0：

| 测试类 | 数量 |
| --- | ---: |
| AnimationLookupTest | 1 |
| AuthorityTest | 6 |
| CameraBearingTest | 12 |
| CameraConstraintsTest | 7 |
| FacingHistoryTest | 5 |
| LockSupportTest | 8 |
| SchedulerTest | 16 |
| SemanticEventsTest | 2 |
| ThirdPersonCameraTest | 9 |

SchedulerTest 新增 7 项：收包年龄 6 / 上限 4 的真实时序复现、上限与有效年龄 9/10 半开边界（同样输入重复 20 次）、关闭补偿逐年龄与旧调用对照、未来 stamp 零补偿、from 与缓冲过期、confirmedHit 与等待时不累计、idle / counter / 结束及替换 origin。原有断言未删改。测试不读取墙钟。

复现时上限 0 返回 ILLEGAL_NODE；上限 4 的有效年龄为 `913-4-902=7`，执行 light_3，start=913。收包年龄 8 仍只补 4：有效年龄 9 接受、10 拒绝。独立 until 边界测试延长原动作 recovery，避免混淆动作结束与窗口边界。

直接编译方式（类名为上表九类，各加包名前缀）：

```bash
CP=/opt/gradle-8.14.3/lib/junit-4.13.2.jar:/opt/gradle-8.14.3/lib/hamcrest-core-1.3.jar:/opt/gradle-8.14.3/lib/gson-2.10.jar
mkdir -p /tmp/combo-tests
mapfile -t TEST_SOURCES < <(rg --files src/test/java/com/layue13/clashweave/core | rg 'Test.java$' | rg -v '/(Appearance|SoulsComposition|CameraTransform|CorrectionMarkers|LockSelection)Test.java$' | sort)
TEST_CLASSES=()
for file in "${TEST_SOURCES[@]}"; do
  name=${file##*/}
  TEST_CLASSES+=("com.layue13.clashweave.core.${name%.java}")
done
javac --release 8 -cp "$CP" -d /tmp/combo-tests src/main/java/com/layue13/clashweave/core/*.java src/main/java/com/layue13/clashweave/presentation/*.java "${TEST_SOURCES[@]}"
java -cp "/tmp/combo-tests:src/main/resources:$CP" org.junit.runner.JUnitCore "${TEST_CLASSES[@]}"
```

## 未验证与限制

按要求只尝试一次 `./gradlew spotlessCheck checkstyleMain checkstyleTest test`，失败在 wrapper 创建 `/root/.gradle/.../gradle-9.7.1-bin.zip.lck`：`Read-only file system`，尚未访问 Maven Central。完整构建、Spotless、Checkstyle 及 Forge 集成编译未通过验证；手工按邻近格式整理，`git diff --check` 通过。

直接测试排除了 AppearanceTest、SoulsCompositionTest、CameraTransformTest、CorrectionMarkersTest、LockSelectionTest；network / forge / presentation 测试未运行。因此不能声称所有 JUnit 或 Forge 配置加载已经通过。没有提交构建产物或日志，没有修改 docs/design。

提交限制：实际沙箱将当前克隆的 `.git` 设为只读，git add 创建 index.lock 即失败，与任务描述的可写权限不一致。因此在 `/tmp/clashweave-combo-review` 的本地克隆提交相同五个文件；原目录保留工作树修改，未 push、未开 PR。该克隆的提交可用 `git fetch /tmp/clashweave-combo-review codex/combo-window-compensation` 后 cherry-pick 引入（需在可写环境执行）。

容器无法运行真实 Minecraft 双客户端；100 ms RTT 连击回归、真人体感及低 TPS 未验证。Windows 上先运行完整构建，再首次运行各标签并保留失败结果：

```powershell
$env:JAVA_HOME='C:\Program Files\Zulu\zulu-25'
$env:VERSION='0.1.0-dev'
.\gradlew.bat spotlessCheck checkstyleMain checkstyleTest test --rerun p0LaunchFiles -I tools/p0/validation.gradle --no-configuration-cache --console=plain
python tools/p0/dual.py --label combo-compensation-zero --accept-eula --souls-view
python tools/p0/playfix_metrics.py --label combo-compensation-zero
python tools/p0/dual.py --label combo-compensation100 --accept-eula --souls-view --rtt 100
python tools/p0/playfix_metrics.py --label combo-compensation100
python tools/p0/analyze.py --label combo-compensation100
```

建议另将服务端配置设为 0，用新标签做同条件对照，不能重跑失败场景择优。默认 4 不保证 20/20。

可选夹具容错未修改、未编译、未运行：停滞位于 `tools/p0/java/com/layue13/clashweave/validation/ClientReplay.java` 的 viewReplay（只有到达 light_3 后待机才复位），validation 源集通过 tools/p0/validation.gradle 引入，汇总由 playfix_metrics.py 读取 P0_VIEW_SEQUENCE。该夹具直接依赖 Minecraft、ClientProxy 和客户端视觉状态，本容器缺少可用的完整编译类路径，无法安全验证拒绝回执与预测状态的复位时序。原夹具仍可能因一次失败阻断余下序列，完成数不能被当作独立 20 次尝试的成功率。

## ORIGIN_CHANGED 边界

补偿后仍可能“按下时窗口内，到达时动作已结束或被替换”，依旧返回 ORIGIN_CHANGED，不恢复旧实例。测试分别断言结束与替换后的拒绝。

风险依据：light_2 的 duration=12、until=10，现有收包年龄通常 2–5、本次 6，加上消费排队。若按下年龄 a、收包延迟 d、排队 q，正常逐 tick 下在消费前原动作已结束的条件为 `a+d+q >= 13`（consume 在 advance 之前，消费年龄 12 时仍可能赶在结束前替换）。本次 a=4、d=6、q=1，年龄 11，能补救；a=9、d=3、q=1 则消费年龄 13，原动作已结束，补偿无效。中断或其它请求替换也能提前改变 origin。

没有完整按键年龄/延迟分布，无法给出可信实测概率。仅作风险量级估计：假设 a 在 6–9、d 在 2–5 各整数独立均匀，q=1、无中断，则 16 种组合有 6 种在消费前结束（37.5%）；这是假设模型，**不是实测失败率**。真实夹具常在年龄 4–5 预输入，且失败后停止，其历史完成数不能验证上述分布；需 Windows 逐请求核对才能估计实际概率。

## 服务端延迟上限

本节记录 HEAD `2ee5076` 之后的工作树修正；前文是上一轮实现与验证的历史记录。客户端可以在输入门允许的 8 tick 内持续自报旧 stamp，因此只取收包年龄与配置上限，低延迟玩家也能获取默认 4 tick 的窗口补偿。现在额外使用服务端玩家 RTT 值限制补偿：

```text
receiptAge = receivedTick - effectiveStamp
latencyCap = ceil(pingMillis / 2 / 50) + inputEdgeCompensationJitterTicks
compensation = max(0, min(receiptAge, inputEdgeCompensationTicks, latencyCap))
```

纯逻辑位于 `core.LatencyCompensation.limit(long receiptAge, int configCap, double pingMillis, int jitterTicks)`，返回整数 tick。`CombatServer` 在原输入门验证之后读取玩家 ping、调用此方法，将结果作为原 Scheduler request 重载的上限；Scheduler 文件及窗口判断语义没有修改。收包到消费的排队时间仍不参与补偿。协议、输入门、精准格挡、相机与 `docs/design/` 均未修改。

新增配置 `network.inputEdgeCompensationJitterTicks`，默认 1，范围 0–4；注释说明它是单程延迟向上取整后额外增加的抖动余量，只有正 RTT 时使用。默认 1 是本任务确定的保守策略，代表名义 20 TPS 下 50 ms 余量，并非双客户端实测调优结果。原 `inputEdgeCompensationTicks` 仍默认 4、范围 0–8、受 stampAgeLimit 限制；配置为 0 时补偿恒为 0，仍走旧窗口行为。

ping 为 0 或未知时采用 **0 补偿**，即使抖动余量为正也不开窗口。这会让刚连入、尚无有效样本的诚实玩家暂时得不到补偿，以避免无样本时给予满额窗口。负年龄、负配置上限、非正 ping、负抖动余量均返回 0；非有限 ping 返回 0。整数年龄与配置参数本身无法表示 NaN/Infinity，ping 参数接受 double 以便对未知或非有限值进行防御测试。纯逻辑将超范围正抖动余量夹到 4，并在转整数前取上限，避免极大输入溢出。

字段核对结果：**字段名未验证**。仓库 `gradle.properties` 明确依赖 Minecraft `1.7.10`、Forge `10.13.4.1614`、mappingsVersion `12`；搜索仓库以及 `/root/.gradle`、`/tmp`、`/opt`、`/home`，未找到 EntityPlayerMP / NetHandlerPlayServer 源码、Minecraft / Forge 开发 jar、sources jar、fields.csv 或 SRG 映射文件；仓库原有代码也没有 `.ping` 或 `field_71138_i` 的引用。因缺少依赖，不能确认 MCP `ping` 与 SRG `field_71138_i` 的对应关系、字段类型/初始值、keepalive 更新方式（包括是否平滑）或发送/更新频率，不将记忆当作验证依据。按任务指定的后备方案，将候选字段读取隔离在很薄的 `CombatServer.serverPingMillis(EntityPlayerMP)` 中，目前直接读取 `player.ping`（null 返回 0）。在实际依赖核实前，Forge 集成编译与该读取路径仍属未验证。

Windows 最小核对办法（在已有可用开发依赖的环境）：

1. IDE 对 `serverPingMillis` 中的 `player.ping` 跳转到声明，确认 EntityPlayerMP 的公开整数类型、初始值，记下依赖 jar/source 路径与版本。若 MCP 名不同，只改此读取方法。
2. 在对应映射包的 `fields.csv` 查找 `field_71138_i`，确认其 MCP 名为 `ping`；如使用 SRG→MCP 文件，也检查 EntityPlayerMP 字段对应行。映射若在 zip 内，先解压到临时目录。可对已定位文件运行 `Select-String -Path <fields.csv或映射文件> -Pattern 'field_71138_i|/ping'`。
3. 在依赖源码中查看 NetHandlerPlayServer 对此字段的赋值，并追踪 S00PacketKeepAlive 发送和 C00PacketKeepAlive 应答处理。核对时间单位、应答 ID 校验、RTT 计算/平滑、发送的 tick 条件与实际更新条件；频率必须从源码得出，不能将 ping 展示刷新频率当作采样频率。
4. 运行 `.\gradlew.bat compileJava test --no-configuration-cache --console=plain`，验证本项目 mappings 下读取可编译。编译成功只能确认字段可访问，更新方式与频率仍需步骤 3。

本轮先写新增测试，再加入仅保留旧公式的 API 壳运行 JUnit，实际结果为 `Tests run: 10, Failures: 8`。随后实现新公式；未删改任何旧测试文件或断言。最终直接使用 `javac --release 8`、JUnit 4.13.2、hamcrest-core 1.3 和 Gson 2.10，实际结果 **`OK (77 tests)`，失败 0**：上一轮九类的 66 项 + `FeedbackPolicyTest` 1 项 + 新增 `LatencyCompensationTest` 10 项。新增覆盖零/未知 ping、100/200/400 ms RTT、40 ms RTT 下伪报年龄 8、较小真实年龄、配置关闭、负数、非有限 ping、ceil 边界、极大输入及抖动范围。

```bash
CP=/opt/gradle-8.14.3/lib/junit-4.13.2.jar:/opt/gradle-8.14.3/lib/hamcrest-core-1.3.jar:/opt/gradle-8.14.3/lib/gson-2.10.jar
mkdir -p /tmp/combo-latency-green
mapfile -t TEST_SOURCES < <(rg --files src/test/java/com/layue13/clashweave/core | rg 'Test.java$' | rg -v '/(Appearance|SoulsComposition|CameraTransform|CorrectionMarkers|LockSelection)Test.java$' | sort)
TEST_SOURCES+=(src/test/java/com/layue13/clashweave/presentation/FeedbackPolicyTest.java)
TEST_CLASSES=()
for file in "${TEST_SOURCES[@]}"; do
  name=${file#src/test/java/}
  name=${name%.java}
  TEST_CLASSES+=("${name//\//.}")
done
javac --release 8 -cp "$CP" -d /tmp/combo-latency-green src/main/java/com/layue13/clashweave/core/*.java src/main/java/com/layue13/clashweave/presentation/*.java "${TEST_SOURCES[@]}"
java -cp "/tmp/combo-latency-green:src/main/resources:$CP" org.junit.runner.JUnitCore "${TEST_CLASSES[@]}"
```

本轮没有运行 Gradle；Forge 配置加载、Forge 完整编译、Spotless/Checkstyle、此前排除的五类 core 测试及 network/forge 测试仍未验证。容器无法运行真实 Minecraft 双客户端，因此真实双客户端回归、keepalive 采样表现及低 TPS 行为均未验证。`git diff --check` 通过；只修改工作树，没有 git add/commit、push 或 PR，产物保留在 `/tmp`，完成后等待负责人审核。
