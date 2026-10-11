"""Renders preview images of the katana models with the shared atlas (nearest-neighbour, like 1.7.10).

Run:  blender -b --python render_preview.py -- <dir with katana.obj, sheath.obj, katana.png> <out_prefix>
"""

import math
import sys

import bpy
from mathutils import Vector

args = sys.argv[sys.argv.index("--") + 1:]
SRC, PREFIX = args[0], args[1]


def reset():
    bpy.ops.wm.read_factory_settings(use_empty=True)


def material():
    mat = bpy.data.materials.new("atlas")
    mat.use_nodes = True
    nt = mat.node_tree
    for n in list(nt.nodes):
        nt.nodes.remove(n)
    out = nt.nodes.new("ShaderNodeOutputMaterial")
    bsdf = nt.nodes.new("ShaderNodeBsdfPrincipled")
    tex = nt.nodes.new("ShaderNodeTexImage")
    tex.image = bpy.data.images.load(f"{SRC}/katana.png")
    tex.interpolation = "Closest"
    bsdf.inputs["Roughness"].default_value = 0.85
    nt.links.new(tex.outputs["Color"], bsdf.inputs["Base Color"])
    nt.links.new(bsdf.outputs["BSDF"], out.inputs["Surface"])
    return mat


def load(name, mat):
    before = set(bpy.data.objects)
    bpy.ops.wm.obj_import(filepath=f"{SRC}/{name}.obj", forward_axis="NEGATIVE_Z", up_axis="Y")
    objs = [o for o in bpy.data.objects if o not in before]
    for o in objs:
        o.data.materials.clear()
        o.data.materials.append(mat)
    return objs


def parent(objs):
    root = bpy.data.objects.new("root", None)
    bpy.context.collection.objects.link(root)
    for o in objs:
        o.parent = root
    return root


def scene(w, h):
    sc = bpy.context.scene
    sc.render.engine = "CYCLES"
    sc.cycles.device = "CPU"
    sc.cycles.samples = 48
    sc.cycles.use_denoising = True
    sc.render.resolution_x, sc.render.resolution_y = w, h
    sc.render.image_settings.file_format = "PNG"
    world = bpy.data.worlds.new("w")
    sc.world = world
    world.use_nodes = True
    bg = world.node_tree.nodes["Background"]
    bg.inputs["Color"].default_value = (0.66, 0.67, 0.69, 1)
    bg.inputs["Strength"].default_value = 1.0
    sun = bpy.data.lights.new("sun", "SUN")
    sun.energy = 3.0
    sun.use_shadow = False
    so = bpy.data.objects.new("sun", sun)
    so.rotation_euler = (math.radians(50), math.radians(10), math.radians(-40))
    bpy.context.collection.objects.link(so)
    return sc


def camera(loc, look, ortho):
    cam = bpy.data.cameras.new("cam")
    cam.type = "ORTHO"
    cam.ortho_scale = ortho
    co = bpy.data.objects.new("cam", cam)
    bpy.context.collection.objects.link(co)
    co.location = loc
    direction = Vector(look) - Vector(loc)
    co.rotation_euler = direction.to_track_quat("-Z", "Y").to_euler()
    bpy.context.scene.camera = co


def plane():
    bpy.ops.mesh.primitive_plane_add(size=10, location=(0, 0.5, 0))
    p = bpy.context.active_object
    p.rotation_euler = (math.radians(90), 0, 0)
    pm = bpy.data.materials.new("bg")
    pm.diffuse_color = (0.66, 0.67, 0.69, 1)
    p.data.materials.append(pm)


def overview():
    reset()
    mat = material()
    k = parent(load("katana", mat))
    s = parent(load("sheath", mat))
    # blade (+Z in Blender after import) -> pointing right (+X), slight 3/4 tilt
    for root, z in ((k, 0.42), (s, -0.36)):
        root.rotation_euler = (math.radians(14), math.radians(90), math.radians(-6))
        root.location = (-0.62 if root is k else -0.62, 0, z)
    # sheath: tip is +Y in model (up in Blender); the sheath mouth is at the left, same orientation as the blade
    plane()
    sc = scene(1536, 1024)
    camera((0.1, -4, 0.02), (0.1, 0, 0.02), 2.05)
    sc.render.filepath = f"{PREFIX}_overview.png"
    bpy.ops.render.render(write_still=True)


def hilt_closeup():
    reset()
    mat = material()
    k = parent(load("katana", mat))
    k.rotation_euler = (math.radians(18), math.radians(90), math.radians(-18))
    k.location = (-0.12, 0, 0)
    plane()
    sc = scene(1024, 768)
    camera((0.0, -4, 0.0), (0.0, 0, 0.0), 0.62)
    sc.render.filepath = f"{PREFIX}_hilt.png"
    bpy.ops.render.render(write_still=True)


overview()
hilt_closeup()
print("RENDER_OK")
