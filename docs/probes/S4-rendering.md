# S4：占位刀渲染 — 通过（限定占位适配器）

## 假设与做法

Blender 5.2.2 LTS 后台生成可编辑 `.blend`，显式导出静态三角 OBJ 和 16×16 PNG。刀的原点为握点、鞘为鞘口；1 单位=1 方块，Blender `(x,y,z)` 转游戏 `(x,z,-y)`。来源和导出器版本保存在 `presentation.json`；OBJ 不承担骨骼动画。

实际 Forge AdvancedModelLoader 加载两份 OBJ。第一人称用 IItemRenderer；第三人称在 RenderPlayerEvent.Specials 的已建立模型矩阵下，刀跟随 bipedRightArm 的手部挂点、鞘跟随 bipedBody 腰部挂点。独立视觉曲线按实例的绝对进度采样，切换视角不改开始时间。曲线验证一段准备/出刀/恢复；不是 P0 战斗动作。

## 命令与输出

```powershell
& 'C:\Program Files\Blender Foundation\Blender 5.2\blender.exe' --background --python tools/probes/export_placeholder.py
.\gradlew.bat -I tools/probes/launch.gradle probeLaunchFiles --no-configuration-cache
python tools/probes/run_matrix.py --accept-eula --label my-run
```

实际输出包含 `S4_IMPORT loader=AdvancedModelLoader format=OBJ texture=PNG units=blocks up=Y origin=grip`。自动脚本在同一段动作第 2/6/9 tick 切第一人称、背后第三人称、正面第三人称，输出的同一 `instance` 对应进度分别为 0.15384616 / 0.46153846 / 0.6923077。截图文件名为 `cw-probe-view-0/1/2.png`，保存在 evidence 的 100ms 场景中；已逐张检查实际握持、腰鞘和进度 HUD。

## 结论与影响

静态导入、两处挂点、一段挥刀与 F5 使用的原版视角变量切换通过；没有重启动作。专用服务器的实际 class-load trace 未加载探针客户端包、Minecraft 客户端包或 OpenGL 类（详见总表）。

资源/视角模块的静态 OBJ 起点成立，需明确此适配器的降级：刚性原版躯干、无胸腰分段、无手臂蒙皮/IK，无盔甲/第三方模型兼容验收；第一人称沿用原版持物变换。脚本切换 `thirdPersonView`，使用与 F5 相同的渲染路径，但人工 F5 按键舒适度未测。本阶段没有命中轨迹，不能由 F5 不重启视觉实例推导“不重复伤害”已验收，也没有验证正式轨迹对齐。

## 审核后的限定

三张截图中抽查的一张为第三人称背面：能看到刀尖一小条和腰后刀鞘，只能证明挂点存在，不能证明握持方向和第一人称握持正确。S4 限定为“挂点与进度显示可行”，握持对齐仍待验证。
