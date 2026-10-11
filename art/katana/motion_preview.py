"""Renders the sword motion tracks on a Minecraft-proportioned figure.

Run:  blender -b --python motion_preview.py -- <tracks.json> <katana.obj> <katana.png> <out_dir> [action ...]

For every action it writes frames f_<action>_<tick>.png (one per tick, 20 TPS) with:
  - the figure, the arm segment and the katana posed from the track,
  - the sword-tip path of the whole action (orange),
  - the server hit sweep (reach x arc from katana.json) as a translucent red fan at chest height,
  - the active ticks marked by a red tint on the figure's belt marker.
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
TIP = 1.17
os.makedirs(OUT, exist_ok=True)
data = json.load(open(TRACKS))
SHOULDER = Vector(data["shoulder"])
GRIP = data["gripDistance"]


def reset():
    bpy.ops.wm.read_factory_settings(use_empty=True)


def box(name, size, loc, color):
    bpy.ops.mesh.primitive_cube_add(size=1, location=loc)
    o = bpy.context.active_object
    o.name = name
    o.scale = size
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    m.node_tree.nodes["Principled BSDF"].inputs["Base Color"].default_value = color
    m.node_tree.nodes["Principled BSDF"].inputs["Roughness"].default_value = 0.9
    o.data.materials.append(m)
    return o


def build_figure():
    skin = (0.80, 0.62, 0.48, 1)
    shirt = (0.16, 0.55, 0.62, 1)
    pants = (0.25, 0.22, 0.45, 1)
    box("head", (0.5, 0.5, 0.5), (0, 0, 1.75), skin)
    box("torso", (0.5, 0.25, 0.75), (0, 0, 1.125), shirt)
    box("legR", (0.25, 0.25, 0.75), (0.125, 0, 0.375), pants)
    box("legL", (0.25, 0.25, 0.75), (-0.125, 0, 0.375), pants)
    box("armL", (0.25, 0.25, 0.75), (-0.375, 0, 1.125), skin)
    arm = box("armR", (0.25, 0.25, 0.75), (0.375, 0, 1.125), skin)
    return arm


def material():
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
    # imported mesh: blade +Z, edge +X, normal -Y  ->  rotate so local X=edge, Y=blade, Z=normal
    fix = bpy.data.objects.new("fix", None)
    bpy.context.collection.objects.link(fix)
    fix.rotation_euler = (math.radians(-90), 0, 0)
    fix.parent = pivot
    for o in objs:
        o.data.materials.clear()
        o.data.materials.append(mat)
        o.parent = fix
    return pivot


def qmat(q):
    x, y, z, w = q
    return Quaternion((w, x, y, z)).to_matrix().to_4x4()


def lights_camera():
    sun = bpy.data.lights.new("sun", "SUN")
    sun.energy = 3.2
    so = bpy.data.objects.new("sun", sun)
    so.rotation_euler = (math.radians(50), math.radians(10), math.radians(-35))
    bpy.context.collection.objects.link(so)
    world = bpy.data.worlds.new("w")
    bpy.context.scene.world = world
    world.use_nodes = True
    bg = world.node_tree.nodes["Background"]
    bg.inputs["Color"].default_value = (0.62, 0.64, 0.68, 1)
    cam = bpy.data.cameras.new("cam")
    cam.type = "ORTHO"
    cam.ortho_scale = 3.7
    co = bpy.data.objects.new("cam", cam)
    bpy.context.collection.objects.link(co)
    co.location = (3.0, 3.4, 3.8)
    co.rotation_euler = (Vector((0.0, 0.6, 1.1)) - co.location).to_track_quat("-Z", "Y").to_euler()
    bpy.context.scene.camera = co
    bpy.ops.mesh.primitive_plane_add(size=12, location=(0, 0, 0))
    g = bpy.context.active_object
    gm = bpy.data.materials.new("ground")
    gm.use_nodes = True
    gm.node_tree.nodes["Principled BSDF"].inputs["Base Color"].default_value = (0.20, 0.27, 0.20, 1)
    g.data.materials.append(gm)


def fan(reach, arc_deg, height=1.0):
    """Server hit sweep: sector in front (+Y) of the player, symmetric about the facing direction."""
    verts = [Vector((0, 0, height))]
    n = 24
    for i in range(n + 1):
        a = math.radians(-arc_deg / 2 + arc_deg * i / n)
        verts.append(Vector((math.sin(a) * reach, math.cos(a) * reach, height)))
    faces = [(0, i, i + 1) for i in range(1, n + 1)]
    me = bpy.data.meshes.new("fan")
    me.from_pydata([tuple(v) for v in verts], [], faces)
    o = bpy.data.objects.new("fan", me)
    bpy.context.collection.objects.link(o)
    m = bpy.data.materials.new("fanmat")
    m.use_nodes = True
    bsdf = m.node_tree.nodes["Principled BSDF"]
    bsdf.inputs["Base Color"].default_value = (0.9, 0.1, 0.1, 1)
    bsdf.inputs["Alpha"].default_value = 0.45
    o.data.materials.append(m)
    return o


def tip_path(samples):
    pts = []
    for s in samples:
        arm = Vector(s["arm"])
        g = SHOULDER + arm * GRIP
        m = qmat(s["q"])
        d = m @ Vector((0, 1, 0))
        yaw = math.radians(s["bodyYaw"])
        p = g + d * TIP
        rot = Matrix.Rotation(yaw, 3, "Z")
        pts.append(rot @ p)
    cu = bpy.data.curves.new("tip", "CURVE")
    cu.dimensions = "3D"
    sp = cu.splines.new("POLY")
    sp.points.add(len(pts) - 1)
    for p, v in zip(sp.points, pts):
        p.co = (v.x, v.y, v.z, 1)
    cu.bevel_depth = 0.012
    o = bpy.data.objects.new("tip", cu)
    bpy.context.collection.objects.link(o)
    m = bpy.data.materials.new("tipmat")
    m.use_nodes = True
    nt = m.node_tree
    for n in list(nt.nodes):
        nt.nodes.remove(n)
    em = nt.nodes.new("ShaderNodeEmission")
    em.inputs["Color"].default_value = (1.0, 0.55, 0.05, 1)
    em.inputs["Strength"].default_value = 3
    out = nt.nodes.new("ShaderNodeOutputMaterial")
    nt.links.new(em.outputs["Emission"], out.inputs["Surface"])
    o.data.materials.append(m)
    return o


def render_action(aid, track):
    reset()
    lights_camera()
    armR = build_figure()
    mat = material()
    sword = load_sword(mat)
    fan(track["reach"], track["arc"])
    tip_path(track["samples"])
    sc = bpy.context.scene
    sc.render.engine = "CYCLES"
    sc.cycles.device = "CPU"
    sc.cycles.samples = 6
    sc.cycles.use_denoising = True
    sc.render.resolution_x, sc.render.resolution_y = 640, 480
    root = bpy.data.objects.new("upper", None)
    bpy.context.collection.objects.link(root)
    for name in ("head", "torso", "armL", "armR"):
        bpy.data.objects[name].parent = root
    sword.parent = root
    bpy.ops.mesh.primitive_uv_sphere_add(radius=0.05, location=(0, 0, 0), segments=12, ring_count=8)
    tipm = bpy.context.active_object
    tm = bpy.data.materials.new("tipm")
    tm.use_nodes = True
    ntt = tm.node_tree
    for n in list(ntt.nodes):
        ntt.nodes.remove(n)
    em2 = ntt.nodes.new("ShaderNodeEmission")
    em2.inputs["Color"].default_value = (1, 0.1, 0.1, 1)
    em2.inputs["Strength"].default_value = 6
    o2 = ntt.nodes.new("ShaderNodeOutputMaterial")
    ntt.links.new(em2.outputs["Emission"], o2.inputs["Surface"])
    tipm.data.materials.append(tm)
    tipm.parent = root
    s_t, a_t = track["startup"], track["active"]
    by_t = {round(s["t"], 3): s for s in track["samples"]}
    for tick in range(0, track["ticks"] + 1):
        s = by_t[float(tick)]
        arm = Vector(s["arm"])
        grip = SHOULDER + arm * GRIP
        root.rotation_euler = (0, 0, math.radians(s["bodyYaw"]))
        # right arm: box centred halfway down the arm, long axis along the arm direction
        centre = SHOULDER + arm * 0.375
        z = Vector((0, 0, 1))
        q = arm.to_track_quat("Z", "Y")
        armR.location = centre
        armR.rotation_mode = "QUATERNION"
        armR.rotation_quaternion = q
        sword.location = grip
        sword.rotation_mode = "QUATERNION"
        qq = s["q"]
        sword.rotation_quaternion = Quaternion((qq[3], qq[0], qq[1], qq[2]))
        mq = Quaternion((qq[3], qq[0], qq[1], qq[2])).to_matrix()
        tipm.location = grip + (mq @ Vector((0, 1, 0))) * TIP
        active = s_t <= tick <= s_t + a_t
        tipm.scale = (1.8, 1.8, 1.8) if active else (1, 1, 1)
        sc.render.filepath = os.path.join(OUT, f"f_{aid}_{tick:02d}.png")
        bpy.ops.render.render(write_still=True)
    print("DONE", aid, track["ticks"] + 1, "frames")


for aid, track in data["actions"].items():
    if ONLY and aid not in ONLY:
        continue
    render_action(aid, track)
print("MOTION_OK")
