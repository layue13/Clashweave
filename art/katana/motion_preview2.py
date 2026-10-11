"""Renders v2 end-effector tracks on a full figure (both arms, both legs, torso twist, feet).

Run:  blender -b --python motion_preview2.py -- <tracks2.json> <katana.obj> <katana.png> <out_dir> [action ...]

Arms are rigid boxes pointing from the (twisted) shoulder to the hand, like ModelBiped; legs are rigid boxes
from the hip towards the foot target. The server hit sweep (reach x arc from katana.json) is the red fan at
chest height; the orange line is the sword-tip path; the big red dot marks the active window.
"""

import json
import math
import os
import sys

import bpy
from mathutils import Matrix, Quaternion, Vector

args = sys.argv[sys.argv.index("--") + 1:]
TRACKS, OBJ, TEX, OUT = args[:4]
ONLY = args[4:]
os.makedirs(OUT, exist_ok=True)
data = json.load(open(TRACKS))
SR, SL = [Vector(s) for s in data["shoulders"]]
K = 0.9375
HIP_Z = 0.75 * K


def reset():
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
        em.inputs["Strength"].default_value = 4
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


def atlas_material():
    mat = bpy.data.materials.new("atlas")
    mat.use_nodes = True
    nt = mat.node_tree
    for n in list(nt.nodes):
        nt.nodes.remove(n)
    out = nt.nodes.new("ShaderNodeOutputMaterial")
    bsdf = nt.nodes.new("ShaderNodeBsdfPrincipled")
    tex = nt.nodes.new("ShaderNodeTexImage")
    tex.image = bpy.data.images.load(TEX)
    tex.interpolation = "Closest"
    bsdf.inputs["Roughness"].default_value = 0.8
    nt.links.new(tex.outputs["Color"], bsdf.inputs["Base Color"])
    nt.links.new(bsdf.outputs["BSDF"], out.inputs["Surface"])
    return mat


def load_sword(mat):
    before = set(bpy.data.objects)
    bpy.ops.wm.obj_import(filepath=OBJ, forward_axis="NEGATIVE_Z", up_axis="Y")
    objs = [o for o in bpy.data.objects if o not in before]
    pivot = bpy.data.objects.new("grip", None)
    bpy.context.collection.objects.link(pivot)
    fix = bpy.data.objects.new("fix", None)
    bpy.context.collection.objects.link(fix)
    fix.rotation_euler = (math.radians(-90), 0, 0)
    fix.parent = pivot
    for o in objs:
        o.data.materials.clear()
        o.data.materials.append(mat)
        o.parent = fix
    return pivot


def setup_scene():
    sun = bpy.data.lights.new("sun", "SUN")
    sun.energy = 3.2
    sun.use_shadow = False
    so = bpy.data.objects.new("sun", sun)
    so.rotation_euler = (math.radians(50), math.radians(10), math.radians(-35))
    bpy.context.collection.objects.link(so)
    world = bpy.data.worlds.new("w")
    bpy.context.scene.world = world
    world.use_nodes = True
    world.node_tree.nodes["Background"].inputs["Color"].default_value = (0.62, 0.64, 0.68, 1)
    cam = bpy.data.cameras.new("cam")
    cam.type = "ORTHO"
    cam.ortho_scale = 4.6
    co = bpy.data.objects.new("cam", cam)
    bpy.context.collection.objects.link(co)
    co.location = (3.4, 3.8, 3.4)
    co.rotation_euler = (Vector((0.0, 0.4, 1.0)) - co.location).to_track_quat("-Z", "Y").to_euler()
    bpy.context.scene.camera = co
    box("ground", (9, 9, 0.02), (0, 0, -0.01), mat_color("ground", (0.20, 0.27, 0.20, 1)))


def fan(reach, arc_deg, height=1.0):
    verts = [Vector((0, 0, height))]
    n = 28
    for i in range(n + 1):
        a = math.radians(-arc_deg / 2 + arc_deg * i / n)
        verts.append(Vector((math.sin(a) * reach, math.cos(a) * reach, height)))
    me = bpy.data.meshes.new("fan")
    me.from_pydata([tuple(v) for v in verts], [], [(0, i, i + 1) for i in range(1, n + 1)])
    o = bpy.data.objects.new("fan", me)
    bpy.context.collection.objects.link(o)
    m = bpy.data.materials.new("fanmat")
    m.use_nodes = True
    b = m.node_tree.nodes["Principled BSDF"]
    b.inputs["Base Color"].default_value = (0.9, 0.1, 0.1, 1)
    b.inputs["Alpha"].default_value = 0.35
    o.data.materials.append(m)


def tip_path(samples):
    cu = bpy.data.curves.new("tip", "CURVE")
    cu.dimensions = "3D"
    sp = cu.splines.new("POLY")
    sp.points.add(len(samples) - 1)
    for p, s in zip(sp.points, samples):
        p.co = (*s["tip"], 1)
    cu.bevel_depth = 0.012
    o = bpy.data.objects.new("tip", cu)
    bpy.context.collection.objects.link(o)
    o.data.materials.append(mat_color("tipmat", (1.0, 0.55, 0.05, 1), emit=True))


def limb(obj, start, end, length):
    """Rigid box of `length` from `start` pointing towards `end`."""
    d = (Vector(end) - Vector(start))
    if d.length < 1e-6:
        d = Vector((0, 0, -1))
    d.normalize()
    obj.location = Vector(start) + d * (length / 2)
    obj.rotation_mode = "QUATERNION"
    obj.rotation_quaternion = d.to_track_quat("Z", "Y")


def render_action(aid, track, reach, arc):
    reset()
    setup_scene()
    skin, shirt, pants = (0.80, 0.62, 0.48, 1), (0.16, 0.55, 0.62, 1), (0.25, 0.22, 0.45, 1)
    head = box("head", (0.5 * K,) * 3, (0, 0, 1.75 * K), mat_color("skin", skin))
    torso = box("torso", (0.5 * K, 0.25 * K, 0.75 * K), (0, 0, 1.125 * K), mat_color("shirt", shirt))
    armR = box("armR", (0.25 * K, 0.25 * K, 0.75 * K), (0, 0, 1), mat_color("skinR", skin))
    armL = box("armL", (0.25 * K, 0.25 * K, 0.75 * K), (0, 0, 1), mat_color("skinL", (0.74, 0.56, 0.42, 1)))
    legR = box("legR", (0.25 * K, 0.25 * K, 0.75 * K), (0, 0, 1), mat_color("pantsR", pants))
    legL = box("legL", (0.25 * K, 0.25 * K, 0.75 * K), (0, 0, 1), mat_color("pantsL", (0.21, 0.19, 0.38, 1)))
    sword = load_sword(atlas_material())
    fan(reach, arc)
    tip_path(track["samples"])
    bpy.ops.mesh.primitive_uv_sphere_add(radius=0.05, segments=12, ring_count=8)
    dot = bpy.context.active_object
    dot.data.materials.append(mat_color("dot", (1, 0.1, 0.1, 1), emit=True))
    sc = bpy.context.scene
    sc.render.engine = "CYCLES"
    sc.cycles.device = "CPU"
    sc.cycles.samples = 6
    sc.cycles.use_denoising = True
    sc.render.resolution_x, sc.render.resolution_y = 640, 480
    s_t, a_t = track["startup"], track["active"]
    by_t = {round(s["t"], 3): s for s in track["samples"]}
    for tick in range(0, track["ticks"] + 1):
        s = by_t[float(tick)]
        yaw = math.radians(s["torsoYaw"])
        rot = Matrix.Rotation(yaw, 4, "Z")
        torso.rotation_euler = (0, 0, yaw)
        head.rotation_euler = (0, 0, yaw * 0.4)
        pel = Vector(s["pelvis"])
        sr, sl = rot @ Vector(SR) + pel, rot @ Vector(SL) + pel
        torso.location = Vector((0, 0, 1.125 * K)) + pel
        head.location = Vector((0, 0, 1.75 * K)) + pel
        limb(armR, sr, s["handR"], 0.75 * K)
        limb(armL, sl, s["handL"]["p"], 0.75 * K)
        hip_r, hip_l = Vector((0.125 * K, 0, HIP_Z)) + pel, Vector((-0.125 * K, 0, HIP_Z)) + pel
        limb(legR, hip_r, (s["footR"][0], s["footR"][1], 0), HIP_Z)  # rigid leg towards the foot target
        limb(legL, hip_l, (s["footL"][0], s["footL"][1], 0), HIP_Z)
        sword.location = s["sword"]["p"]
        sword.rotation_mode = "QUATERNION"
        q = s["sword"]["q"]
        sword.rotation_quaternion = Quaternion((q[3], q[0], q[1], q[2]))
        dot.location = s["tip"]
        dot.scale = (1.8,) * 3 if s_t <= tick <= s_t + a_t else (1,) * 3
        sc.render.filepath = os.path.join(OUT, f"g_{aid}_{tick:02d}.png")
        bpy.ops.render.render(write_still=True)
    print("DONE", aid, track["ticks"] + 1)


KATANA = json.load(open(os.path.join(os.path.dirname(TRACKS), "katana_actions.json")))
ACT = {a["id"]: a for a in KATANA["actions"]}
for aid, track in data["actions"].items():
    if ONLY and aid not in ONLY:
        continue
    render_action(aid, track, ACT[aid]["reach"] or 2.0, ACT[aid]["arc"] or 90)
print("MOTION2_OK")
