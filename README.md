# Clashweave

Forge 模组开发骨架，参考 [GTNH 官方 starter](https://github.com/GTNewHorizons/ExampleMod1.7.10)。

设计资料见 [docs/design](docs/design/README.md)，包含完整 V1.0 方案、设计状态、交接说明及后续讨论议题。
本轮审核从 [设计审核入口](docs/design/review.md) 开始，原型任务与验收见 [原型说明](docs/design/proposals/prototype-spec-v1.md)。

| 组件 | 版本 |
| --- | --- |
| Minecraft | 1.7.10 |
| Forge | 10.13.4.1614 |
| Gradle Wrapper | 9.7.1 |
| GTNH 构建插件 | 2.0.33 |
| Gradle 启动与守护进程 | Java 25 |
| 项目编译与开发运行工具链 | Java 8 |
| Minecraft 反编译工具 | Java 17 |

`enableModernJavaSyntax=false` 与 `forceToolchainVersion=8` 使用真正的 JDK 8 编译器。
`gradle/gradle-daemon-jvm.properties` 要求 Gradle 守护进程运行在 Java 25。
Wrapper JAR 已按 Gradle 官方 SHA-256 校验，发行包也固定了官方 SHA-256。

## 云环境

已安装 Temurin 25.0.2+10、8u482-b08 和 17.0.18+8（仅供反编译工具使用）。每个新 shell 先执行：

```bash
source /workspace/clashweave-env.sh
cd /workspace/Clashweave
./gradlew --version
./gradlew setupDecompWorkspace
./gradlew build
```

安装/刷新脚本位于 `/workspace/clashweave-install.sh`，并保存在云环境的安装配置中。
脚本配置本地工具链、平台 HTTPS 代理和系统 Java 信任库；保留 TLS 与校验和验证。
Gradle 缓存位于 `/workspace/.gradle`，JDK 位于 `/workspace/.toolchains`。
环境脚本仅在没有 Git 标签时默认设置 `VERSION=0.1.0-dev`。
也可显式指定 `VERSION`；创建版本标签后可使用 GTNH 的 Git 版本推导。

## 其他开发机器

安装 JDK 25、JDK 8 和反编译工具需要的 JDK 17，将 `JAVA_HOME` 指向 JDK 25。
在个人 Gradle 配置中用 `org.gradle.java.installations.paths` 指定三套 JDK 路径。
无 Git 标签时先设置 `VERSION=0.1.0-dev`，再执行上面的 Gradle 命令。
模组依赖写入 `dependencies.gradle`，额外仓库写入 `repositories.gradle`。

本地有图形界面的机器可用 `./gradlew runClient`；开发服务端可用 `./gradlew runServer`。
这些运行操作尚未验证；服务端 EULA 需由使用者自行阅读并接受。

## 当前验证状态

- 已验证：Gradle 9.7.1 使用 Java 25 启动。
- 已验证：实际项目的 `compileJava` 和生成 Tags 的任务均使用 JDK 8 编译器。
- 已通过：`setupDecompWorkspace build`，包括 Spotless 与 Checkstyle 检查。
- 已验证：`build/libs/clashweave-0.1.0-dev.jar` 中两个类的 major version 均为 52，模组元数据正确。
- 已通过：安装脚本重复执行，复用了已下载依赖和准备好的 Forge 工作区。
- 项目尚未包含测试；Gradle 的 `test` 任务为 `NO-SOURCE`，未执行单元测试。
- 客户端与服务端运行尚未验证。

`LICENSE-GTNH-template` 保留了上游构建模板的 MIT 许可与版权声明；项目自身的许可尚未指定。
