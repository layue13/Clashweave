# P0 前的技术探针

本分支只包含 S1–S5 的可关闭实验接入，不包含动作图、资源经济、连段、SA 或 P0 运行时。正常启动不注册探针物品、频道、命令或事件处理器；必须显式提供 `-Dclashweave.probes=true`，两端都启用。所有探针结论见各页；审核前不进入 P0。

## 复现

Windows PowerShell，项目根目录；Java 25 启动 Gradle，项目/游戏用 JDK 8。反编译工具链由构建插件自动下载到用户 Gradle 缓存，本次下载了 JDK 17 和 21；没有修改用户的全局 Gradle 配置。

```powershell
$env:JAVA_HOME='C:\Program Files\Zulu\zulu-25'
$env:VERSION='0.1.0-dev'
.\gradlew.bat setupDecompWorkspace
& 'C:\Program Files\Blender Foundation\Blender 5.2\blender.exe' --background --python tools/probes/export_placeholder.py
.\gradlew.bat -I tools/probes/launch.gradle probeLaunchFiles --no-configuration-cache
# 使用者已阅读并接受 Minecraft EULA 后才运行下面的命令。
python tools/probes/run_matrix.py --accept-eula --label my-run
python tools/probes/collect_evidence.py my-run
python tools/probes/run_matrix.py --accept-eula --label my-boundary --rtts 100 --trials 6 --negative-cases
python tools/probes/collect_evidence.py my-boundary
python tools/probes/check_disabled.py --accept-eula
.\gradlew.bat spotlessApply
.\gradlew.bat build
```

`probeLaunchFiles` 导出构建插件实际使用的 JVM、classpath 和启动器到 `build/probes/`；Python 用这些配置直接启动实际 Forge 进程，避免并发 Gradle 构建共享工作目录。`run_matrix.py` 启动一个专用服务器和两个不同离线名字的客户端（ProbeA、ProbeB），每个场景使用新建的 `run/probes/<label>-…` 目录，拒绝覆盖已有目录。服务器只监听 127.0.0.1，测试端口 25565/25566；退出后关闭本脚本创建的进程。不会访问生存存档。

矩阵默认 0/50/100/200ms RTT 和 100ms RTT＋20ms 抖动。FIFO TCP 代理只延迟转发，不改变包内容或顺序；抖动为每个方向均匀 ±10ms，实际转发耗时另有记录。TCP read chunk 并非 Minecraft 包，不能将此实验等同于互联网丢包/重传测试。客户端以已知 RTT 校准 S3 测试时钟，不把它当成可发布的时间同步协议。

本机原版 1.7.10 音频初始化/重载竞态曾触发 OpenAL 异常，矩阵的独立 `options.txt` 设置 master/music=0；未测声音。自动模式清除本游戏进程的键位状态并释放鼠标抓取，避免桌面输入污染回放。S4 截图直接来自 Minecraft framebuffer，未后期修改。

手工接入可用 `gradlew -I tools/probes/launch.gradle runServer` / `runClient`。接受 EULA 的文件、离线服务器配置和 OP 权限需自行放在插件默认的 `run/server`。`/cwprobe s1`、`s2 open|wall|invalid`、`s3`、`swing`、`engage`、`peace` 均为 OP 命令；这是探针夹具，不是玩法接口。不要在已有世界启用：S2 会在 x=-2..15、z=-2..7、y=64..68 建测试平台。

## 证据范围

每项报告分别给出假设、方法、输出、结论与设计影响。`evidence/` 保留可审阅的测量日志、汇总、原生截图及反编译源码 SHA-256；完整 stdout、class-load trace 和世界在 `run/probes/`，不打包进 Git。异常启动也保存在本地，用于复核实验修正。

原型说明 8/8.1 的玩法、派生、扣费、控制预算、模式经济及持续 PvP 指标属于后续 P0/P2，本阶段均未实现/未验证。`test` 为 NO-SOURCE；验收证据是实际游戏实验，不能把 `build` 当成探针通过。

配置在测试目录 `config/clashweave.cfg` 的 `probes` 类别。段间隔、精准窗口、回溯上限、位移、视觉时长、接敌半径和迟滞均可调；友好误击保护默认 true、平时方块交互默认 true，均是待用户确认的暂定值。
