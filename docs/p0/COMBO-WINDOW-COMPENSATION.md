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
