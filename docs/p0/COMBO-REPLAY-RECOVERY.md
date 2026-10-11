# 连击回放容错与补偿验证准备

本提交仅改实验夹具和汇总器，不修改生产调度、网络、输入或数值。

## 夹具容错

单条序列收到ILLEGAL_NODE、WINDOW_CLOSED、EXPIRED_OR_CHANGED、ORIGIN_CHANGED或REJECT结果时，记录失败与ack、实例、修订、动作及elapsed，再退出该序列。动作仍按服务器状态自然结束，夹具在空闲后继续下一条；不发送额外取消、不修改玩家或服务端状态。

已观察到动作但在未到达light_3的情况下返回空闲时，若最后一个动作实例未发送派生，记录NO_DERIVATIVE_REQUEST；已发请求但未启动后继则记录DERIVATIVE_NOT_STARTED。不进入正常完成统计。每条最长等待60客户端tick，超过后记TIMEOUT，避免夹具永久停住；没有收到初始START也不得当作完成。

每条打印P0_VIEW_RESULT；最终P0_VIEW COMPLETE分别打印attempted/completed/failed。该COMPLETE仅表示回放结束，不表示20/20成功。原P0_VIEW_SEQUENCE completed仅成功序列保留；汇总器新增failed_sequences、unresolved_sequences、failure_reasons，失败段内的位置包同样统计。未完成且无结果的序列保持未决，不将其算成功。

验证命令：

```powershell
python tools/p0/test_view_metrics.py
.\gradlew.bat p0ValidationJar -I tools/p0/validation.gradle --no-configuration-cache --console=plain
```

合成日志测试1项通过：失败→成功→漏发失败→未决分别统计为2失败/1成功/1未决，后续序列没有被吞掉。夹具编译通过。**真实Windows双客户端恢复路径尚未验证**，须在补偿A/B矩阵中验证，不能用合成汇总测试替代。

## 构建门槛与暂时阻塞（历史记录，已解决）

后续用户允许仅格式修正；17b0f2d完整检查通过，107项JUnit、ping字段验证成立。固定0/100ms矩阵已执行，基线一次漏发后继续完成剩余五条，详见[Windows测量](COMBO-COMPENSATION-WINDOWS.md)。以下保留首次构建受阻记录，不替代最终结果。

基线542ebab执行用户指定的Spotless、Checkstyle main/test、test --rerun、p0LaunchFiles命令通过，实际90项JUnit，0失败/错误/跳过。冻结baseline-542ebab.jar SHA256：`b22166219f1acdbbe7d3c216065963a210661dbe4eed6397abf2d46dc381c0ef`。

候选81b4b86同一命令在spotlessJavaCheck失败：LatencyCompensation.java的if换行、Scheduler.java的else-if缩进不符仓库格式。因此完整构建门槛未通过，**没有冻结候选jar，没有启动A/B运行**，不使用build/libs中残留基线jar冒充候选。

额外独立compileJava通过，确认当前Forge映射的EntityPlayerMP.ping字段可编译，未改成其他字段。独立Checkstyle main/test、test --rerun、p0ValidationJar通过，实际107项JUnit，0失败/错误/跳过；这些通过不抵消Spotless失败。关键输出：

```text
> Task :spotlessJavaCheck FAILED
src/main/java/com/layue13/clashweave/core/LatencyCompensation.java
src/main/java/com/layue13/clashweave/core/Scheduler.java

> Task :compileJava
BUILD SUCCESSFUL
```

完整日志只在忽略的build/p0/comp-*.log，未入库。补偿量/ping/收包年龄的运行观测、0/100ms各A/B/A/B/A/B、每次20条、一页图均未验证，等待可通过全部检查的候选或用户明确指定如何处理冻结候选的格式问题。不擅自格式化生产源码后仍标成81b4b86。
