"""Old vs golden-ratio katana, standing upright next to the player figure (front view, orthographic).

Run:  blender -b --python render_proportion.py -- <old.obj> <new.obj> <katana.png> <out.png> <old_kashira_y> <new_kashira_y>

Reference lines: the player's visible height (1.875) and 1/phi of it (1.159), the golden sword length.
"""

import math
import sys

import bpy
from mathutils import Vector

ARGS = sys.argv[sys.argv.index("--") + 1:]
TEX, OUT = ARGS[0], ARGS[1]
ITEMS = [a.split("|") for a in ARGS[2:]]  # path|kashira_y|x|label
PLAYER_H = 1.875
PHI = (1 + 5 ** 0.5) / 2

bpy.ops.wm.read_factory_settings(use_empty=True)


def mat_color(name, color, emit=False):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    nt = m.node_tree
    if emit:
        for n in list(nt.nodes):
            nt.nodes.remove(n)
        em = nt.nodes.new("ShaderNodeEmission")
        em.inputs["Color"].default_value = color
        em.inputs["Strength"].default_value = 2
        out = nt.nodes.new("ShaderNodeOutputMaterial")
        nt.links.new(em.outputs["Emission"], out.inputs["Surface"])
    else:
        nt.nodes["Principled BSDF"].inputs["Base Color"].default_value = color
        nt.nodes["Principled BSDF"].inputs["Roughness"].default_value = 0.9
    return m


def box(name, size, loc, m):
    bpy.ops.mesh.primitive_cube_add(size=1, location=loc)
    o = bpy.context.active_object
    o.name = name
    o.scale = size
    o.data.materials.append(m)
    return o


skin = mat_color("skin", (0.80, 0.62, 0.48, 1))
shirt = mat_color("shirt", (0.16, 0.55, 0.62, 1))
pants = mat_color("pants", (0.25, 0.22, 0.45, 1))
# The visible model is the biped scaled by 0.9375 (2.0 units -> 1.875 blocks)
k = 0.9375
box("head", (0.5 * k,) * 3, (0, 0, 1.75 * k), skin)
box("torso", (0.5 * k, 0.25 * k, 0.75 * k), (0, 0, 1.125 * k), shirt)
box("legR", (0.25 * k, 0.25 * k, 0.75 * k), (0.125 * k, 0, 0.375 * k), pants)
box("legL", (0.25 * k, 0.25 * k, 0.75 * k), (-0.125 * k, 0, 0.375 * k), pants)
box("armR", (0.25 * k, 0.25 * k, 0.75 * k), (0.375 * k, 0, 1.125 * k), skin)
box("armL", (0.25 * k, 0.25 * k, 0.75 * k), (-0.375 * k, 0, 1.125 * k), skin)

img = bpy.data.images.load(TEX)
atlas = bpy.data.materials.new("atlas")
atlas.use_nodes = True
nt = atlas.node_tree
for n in list(nt.nodes):
    nt.nodes.remove(n)
out = nt.nodes.new("ShaderNodeOutputMaterial")
bsdf = nt.nodes.new("ShaderNodeBsdfPrincipled")
tex = nt.nodes.new("ShaderNodeTexImage")
tex.image = img
tex.interpolation = "Closest"
bsdf.inputs["Roughness"].default_value = 0.8
nt.links.new(tex.outputs["Color"], bsdf.inputs["Base Color"])
nt.links.new(bsdf.outputs["BSDF"], out.inputs["Surface"])


def place(path, x, kashira_y):
    before = set(bpy.data.objects)
    bpy.ops.wm.obj_import(filepath=path, forward_axis="NEGATIVE_Z", up_axis="Y")
    objs = [o for o in bpy.data.objects if o not in before]
    root = bpy.data.objects.new("r", None)
    bpy.context.collection.objects.link(root)
    for o in objs:
        o.data.materials.clear()
        o.data.materials.append(atlas)
        o.parent = root
    root.location = (x, 0, -kashira_y)  # kashira end rests on the ground
    # face the front of the blade to the camera: blade is +Z in Blender, edge +X; look from -Y


def label(text, x):
    bpy.ops.object.text_add(location=(x - 0.28, -0.35, -0.16), rotation=(math.radians(90), 0, 0))
    t = bpy.context.active_object
    t.data.body = text
    t.data.size = 0.09
    t.data.materials.append(mat_color("txt" + text, (1, 1, 1, 1), emit=True))


for path, kk, x, text in ITEMS:
    place(path, float(x), float(kk))
    label(text, float(x))

line = mat_color("line", (1.0, 0.55, 0.05, 1), emit=True)
box("h_player", (3.9, 0.01, 0.008), (0, -0.3, PLAYER_H), line)
box("h_phi", (3.9, 0.01, 0.008), (0, -0.3, PLAYER_H / PHI), mat_color("line2", (0.2, 0.9, 0.3, 1), emit=True))
g = box("ground", (8, 4, 0.02), (0, 0, -0.01), mat_color("g", (0.20, 0.27, 0.20, 1)))

sun = bpy.data.lights.new("sun", "SUN")
sun.energy = 3.2
sun.use_shadow = False
so = bpy.data.objects.new("sun", sun)
so.rotation_euler = (math.radians(55), 0, math.radians(-25))
bpy.context.collection.objects.link(so)
world = bpy.data.worlds.new("w")
bpy.context.scene.world = world
world.use_nodes = True
world.node_tree.nodes["Background"].inputs["Color"].default_value = (0.62, 0.64, 0.68, 1)
cam = bpy.data.cameras.new("cam")
cam.type = "ORTHO"
cam.ortho_scale = 3.9
co = bpy.data.objects.new("cam", cam)
bpy.context.collection.objects.link(co)
co.location = (0, -6, 0.9)
co.rotation_euler = (math.radians(90), 0, 0)
bpy.context.scene.camera = co
sc = bpy.context.scene
sc.render.engine = "CYCLES"
sc.cycles.device = "CPU"
sc.cycles.samples = 24
sc.cycles.use_denoising = True
sc.render.resolution_x, sc.render.resolution_y = 1500, 900
sc.render.filepath = OUT
bpy.ops.render.render(write_still=True)
print("PROP_OK")
