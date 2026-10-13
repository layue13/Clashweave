# WF-1b Scheduler 入口规则数据驱动

Scheduler 不再写死 `iai/light_1/heavy/sheathe`:入口由 style 的 `entry` 声明(Gameplay 字段,进入清单哈希)。

## Schema(styles[].entry,必填)

```json
"entry": {
  "sheathed": { "LIGHT": "katana:iai" },
  "ready": { "LIGHT": "...", "HEAVY": "...", "SHEATHE": "..." },
  "entersReady": ["..."],
  "entersSheathed": ["katana:sheathe"]
}
```

- `ready` 必填且非空;`sheathed` 可省略(无收刀概念的风格,如枪,只有 ready 态,初始即 ready)。
- 键是 `Intent` 枚举名;值是风格内的动作 ID(短 ID 按所在 pack 命名空间补全)。
- `entersReady`:开始后进入出鞘态的动作;`entersSheathed`:**完成**后回到收刀态的动作。声明了 `sheathed` 就两者都必须非空。
- 加载期校验:未知 Intent、动作不在风格内、缺 `ready`、拼错字段(如 `reedy`)、缺 `entry`,全部带"文件: JSON 路径: 原因"。

## 兼容与接线影响

- 没有 `entry` 的旧 `combat/katana.json` 走兼容路径(`EntryRules.legacyKatana`,只对实际存在的动作生效),旧测试不改断言全部通过。
- `ActionCatalog` 内部键改为完整 ID,`get/entry*` 接受短 ID 与完整 ID;`Definition.id` 保持声明时的写法,所以旧目录的 Result/wire 字符串不变,内容包目录的是 `katana:xxx`。**core 里的短 ID 补全暂用 `katana:` 前缀(`ActionCatalog.normalize`)作为过渡**,接线阶段应改为目录自带默认命名空间。
- 仍写死短 ID、接线阶段必须一并处理:`client/ClientProxy.java:163-165`(客户端预测入口)、`client/KatanaRendering.java:57`、`client/PresentationAssets.java:33`、`forge/CombatPresentation.java:54`。

## 验证

`bash tools/wf1/run-content-tests.sh`(javac --release 8 + JUnit,仅纯逻辑测试;依赖 Minecraft/Forge/netty/ASM 的测试被跳过):**128 项通过**。新增 `ContentEntryTest`:40 个种子 × 400 tick 的随机输入下,旧硬编码目录与内容包目录的 Scheduler 状态与结果序列逐 tick 相同(并断言随机游走确实触达 iai/light_1/light_2/heavy/sheathe);全部 (状态, Intent) 组合表;枪风格数据入口;非法 entry 报错;entry 改动会改变 Gameplay 哈希。katana Gameplay 哈希因加入 entry 而变为 518dafda…。

未验证:Gradle/Spotless/Forge 完整构建;网络/客户端相关测试(被跳过);真实游戏内行为。
