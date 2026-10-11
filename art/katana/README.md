# art/katana

日本刀(方向 A:低多边形 + 手绘色块)的生成脚本。说明、图集分区、验收清单见 `docs/art/katana-a.md`。

- `paint_atlas.py`  — 生成 256×256 共用图集(Pillow + numpy)。换皮肤改 `PALETTE`。
- `build_models.py` — Blender 无头建模并导出 `katana.obj`、`sheath.obj`(`EDGE_SIGN` 控制刃口朝向)。
- `render_preview.py` — Cycles 预览图。
- `blend/` — 两份 `.blend`,可直接在 Blender 里打开修改。
