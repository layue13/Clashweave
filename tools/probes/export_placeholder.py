"""Blender background source generator and explicit Z-up -> Y-up OBJ exporter."""
import json
from pathlib import Path

import bpy

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "src/main/resources/assets/clashweave/probe"
SOURCE = ROOT / "assets/probes"
OUT.mkdir(parents=True, exist_ok=True)
SOURCE.mkdir(parents=True, exist_ok=True)
bpy.ops.object.select_all(action="SELECT")
bpy.ops.object.delete(use_global=False)


def box(name, center, size, color):
    bpy.ops.mesh.primitive_cube_add(size=1, location=center)
    obj = bpy.context.object
    obj.name = name
    obj.scale = size
    bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
    obj["atlas_band"] = color
    return obj


blade = [box("handle", (0, 0, -0.1), (0.045, 0.035, 0.2), 2),
         box("guard", (0, 0, 0.04), (0.14, 0.08, 0.025), 1),
         box("blade", (0, 0, 0.52), (0.055, 0.012, 0.93), 0)]
sheath = [box("saya", (0, 0, -0.5), (0.075, 0.045, 1.02), 3),
          box("mouth", (0, 0, 0), (0.085, 0.055, 0.035), 1)]


def export(objects, filename):
    lines = ["# Clashweave probe v1; 1 unit=block; grip/mouth origin; Y-up"]
    index = 1
    for obj in objects:
        mesh = obj.data
        mesh.calc_loop_triangles()
        lines.append("g " + obj.name)
        for v in mesh.vertices:
            p = obj.matrix_world @ v.co
            lines.append(f"v {p.x:.6f} {p.z:.6f} {-p.y:.6f}")
        u = (obj["atlas_band"] + 0.5) / 4
        for _ in mesh.vertices:
            lines.append(f"vt {u:.6f} 0.500000")
        for tri in mesh.loop_triangles:
            lines.append("f " + " ".join(f"{index+i}/{index+i}" for i in tri.vertices))
        index += len(mesh.vertices)
    (OUT / filename).write_text("\n".join(lines) + "\n", encoding="ascii")


export(blade, "blade.obj")
export(sheath, "sheath.obj")
image = bpy.data.images.new("placeholder", width=16, height=16)
colors = [(0.78, 0.84, 0.9, 1), (0.86, 0.61, 0.17, 1), (0.1, 0.12, 0.16, 1), (0.38, 0.055, 0.045, 1)]
image.pixels = [value for y in range(16) for x in range(16) for value in colors[x // 4]]
image.filepath_raw = str(OUT / "placeholder.png")
image.file_format = "PNG"
image.save()
contract = {"format": 1, "units": "blocks", "sourceUp": "Z", "runtimeUp": "Y",
            "conversion": "(x,y,z)->(x,z,-y)", "bladeOrigin": "grip", "sheathOrigin": "mouth",
            "swingStartDegrees": 55, "swingEndDegrees": -85, "timeBase": "20 ticks/second",
            "adapter": "ModelBiped rigid mounts; no segmented torso or IK",
            "blenderVersion": bpy.app.version_string, "exporterVersion": 1}
(OUT / "presentation.json").write_text(json.dumps(contract, indent=2) + "\n", encoding="utf-8")
bpy.ops.wm.save_as_mainfile(filepath=str(SOURCE / "placeholder.blend"))
print("CWPROBE ASSET_EXPORT " + json.dumps(contract))
