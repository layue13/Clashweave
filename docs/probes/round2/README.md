# 第二轮复现入口

结论与阶段范围见 [REPORT.md](REPORT.md)，实际命令见 [S1b](S1b-damage.md)。S1b 失败后按用户要求停止，其余项目与 P0 未执行。

实验代码在 `tools/probes2/java`，仅显式 Gradle init script 创建独立源集/实验 jar，生产源码及构建配置没有接入。服务器 EULA 已由用户阅读并接受；复现者须自行接受后才能使用 `--accept-eula`。脚本每次新建 `run/probes2/<label>` 世界与证据，重复 label 会被拒绝。

逐条 [measurements.log](evidence/s1b-reentry/measurements.log)、保留警告的 [server.log](evidence/s1b-reentry/server.log)、启动命令和 SHA-256 [summary.json](evidence/s1b-reentry/summary.json)、[构建日志](evidence/build.log)及[生产包隔离](evidence/packaging.json)均随仓库提交。完整 JVM class-load trace 在本地 `run/probes2/s1b-reentry/server.log`，服务器完整日志不含在生产 mod jar 内。
