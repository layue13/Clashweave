"""Builds the low-poly katana (drawn) and sheath-with-hilt (sheathed) models, direction A.

Run headless:  blender -b --python build_models.py -- <outdir>

Model contract (matches the P0 placeholder models, see KatanaRendering / PresentationAssets):
  katana.obj : units = blocks, grip centre = origin, blade along +Y, kashira at y=-0.18, tip at y=1.17.
  sheath.obj : mouth at y=0, tip at y=1.25 (+Y is the body's downward axis when mounted), the exposed hilt
               extends to -Y from the mouth.
Both models share one 256x256 atlas (art/katana/paint_atlas.py), regions are listed in REGION below.

Model space (x, y, z) is mapped to Blender (x, -z, y) so Blender's default OBJ export (Y up, -Z forward)
writes the same coordinates back out.
"""

import math
import os
import sys

import bmesh
import bpy
from mathutils import Vector

A = 256  # atlas size in px
OUT = sys.argv[sys.argv.index("--") + 1] if "--" in sys.argv else "."

# Atlas regions, (x0, y0, x1, y1) in px, origin top-left.
REGION = {
    "blade": (0, 0, 256, 56),
    "tsuka": (0, 56, 128, 120),
    "saya": (128, 56, 256, 120),
    "brass": (0, 120, 128, 184),
    "tsuba": (128, 120, 192, 184),
    "cord": (192, 120, 256, 184),
}

# ---- proportions: every dimension is derived from the player's visible height with the golden ratio ----
PHI = (1 + 5 ** 0.5) / 2
PLAYER_H = 1.875  # visible model height of the biped (2.0 model units * the 0.9375 player scale)
L = PLAYER_H / PHI  # total sword length, kashira end to kissaki tip: player : sword = phi : 1
T_THICK = 0.03  # tsuba thickness (aesthetic, not derived)
HILT = (L - T_THICK) / (1 + PHI ** 2)  # kashira .. tsuba: blade : hilt = phi^2 : 1 (real katana is about 2.8 : 1)
BLADE = L - T_THICK - HILT  # habaki + blade to the tip
TSUBA_Y0 = HILT / PHI ** 2  # the grip centre (origin) sits at 0.382 of the hilt, measured from the tsuba
TSUBA_Y1 = TSUBA_Y0 + T_THICK
KASHIRA_END = TSUBA_Y0 - HILT
HABAKI_LEN = BLADE * 0.075
TIP_Y = TSUBA_Y1 + BLADE
KISSAKI = BLADE * 0.09  # 1 / phi^5 of the blade
GRIP_W = 0.088  # tsuka width
TSUBA_D = GRIP_W * PHI  # guard diameter : grip width = phi
BLADE_W = HILT / PHI ** 3  # blade width at the habaki
BLADE_TH = BLADE_W / PHI ** 2  # blade thickness = width / phi^2
SHEATH_LEN = BLADE - HABAKI_LEN + 0.05  # mouth (y=0) to the end cap
SORI = 0.049 * BLADE  # exaggerated a little so the curve reads at game scale
BLADE_BASE = TSUBA_Y1
BLADE_TIP = TIP_Y
EDGE_SIGN = 1.0  # +1: cutting edge toward +X. The motion tracks supersede this; kept for static previews.


def B(p):
    return Vector((p[0], -p[2], p[1]))


def uvn(px, py):
    return (px / A, 1.0 - py / A)


class Part:
    def __init__(self, name):
        self.name = name
        self.bm = bmesh.new()
        self.uvl = self.bm.loops.layers.uv.new("UVMap")

    def vert(self, p):
        return self.bm.verts.new(B(p))

    def face(self, verts, uvs):
        f = self.bm.faces.new(verts)
        for loop, (u, v) in zip(f.loops, uvs):
            loop[self.uvl].uv = uvn(u, v)
        f.smooth = False
        return f

    def build(self):
        bmesh.ops.recalc_face_normals(self.bm, faces=self.bm.faces)
        me = bpy.data.meshes.new(self.name)
        self.bm.to_mesh(me)
        self.bm.free()
        obj = bpy.data.objects.new(self.name, me)
        bpy.context.collection.objects.link(obj)
        return obj


def octagon(cx, y, rx, rz, start=22.5):
    pts = []
    for k in range(8):
        a = math.radians(start + 45 * k)
        pts.append((cx + rx * math.cos(a), y, rz * math.sin(a)))
    return pts


def tube(part, rings, ux, spans, closed=True):
    """Quads between consecutive rings. ux[i]: atlas x of ring i. spans[j]: (v_a, v_b) atlas y for segment j."""
    n = len(rings[0])
    verts = [[part.vert(p) for p in ring] for ring in rings]
    for i in range(len(rings) - 1):
        for j in range(n):
            j2 = (j + 1) % n
            if not closed and j2 == 0:
                continue
            va, vb = spans[j]
            part.face(
                [verts[i][j], verts[i][j2], verts[i + 1][j2], verts[i + 1][j]],
                [(ux[i], va), (ux[i], vb), (ux[i + 1], vb), (ux[i + 1], va)],
            )
    return verts


def cap(part, verts, ring, centre_px, half_px, flip_u=False):
    """Planar cap over the ring, mapping model x,z to a square of the atlas."""
    cx, cy = centre_px
    cxm = sum(p[0] for p in ring) / len(ring)
    ext = max(max(abs(p[0] - cxm), abs(p[2])) for p in ring) or 1.0
    uvs = []
    for p in ring:
        u = cx + (p[0] - cxm) / ext * half_px * (-1 if flip_u else 1)
        v = cy + p[2] / ext * half_px
        uvs.append((u, v))
    part.face(verts, uvs)


def face_spans(region, n=8):
    x0, y0, x1, y1 = REGION[region]
    h = (y1 - y0) / n
    return [(y0 + h * k + 0.5, y0 + h * (k + 1) - 0.5) for k in range(n)]


def ux_along(region, ts):
    x0, _, x1, _ = REGION[region]
    return [x0 + 1 + (x1 - x0 - 2) * t for t in ts]


def stack(part, specs, region, ys, closed_caps=("brass", None)):
    """specs: list of (y, rx, rz, cx). Builds an octagonal stack with caps."""
    rings = [octagon(cx, y, rx, rz) for (y, rx, rz, cx) in specs]
    t0, t1 = ys
    ts = [(s[0] - t0) / (t1 - t0) for s in specs]
    verts = tube(part, rings, ux_along(region, ts), face_spans(region))
    for ring, vs, which in ((rings[0], verts[0], 0), (rings[-1], verts[-1], 1)):
        c = closed_caps[0]
        x0, y0, x1, y1 = REGION[c]
        cap(part, list(reversed(vs)) if which == 0 else vs, list(reversed(ring)) if which == 0 else ring,
            ((x0 + x1) / 2, (y0 + y1) / 2), 24)
    return verts


def blade_center(y):
    s = (y - BLADE_BASE) / (BLADE_TIP - BLADE_BASE)
    return -SORI * s * s * EDGE_SIGN


def build_blade(part):
    yk = BLADE_TIP - KISSAKI  # yokote: the start of the kissaki
    n = 8
    ys = [BLADE_BASE + (yk - BLADE_BASE) * i / (n - 1) for i in range(n)] + [yk + KISSAKI * 0.55]
    rings = []
    for y in ys:
        s = (y - BLADE_BASE) / (yk - BLADE_BASE)
        if y <= yk + 1e-9:
            hw = BLADE_W / 2 * (1 - (1 - 1 / PHI) * s)  # tapers to 0.618 at the yokote
            th = BLADE_TH / 2 * (1 - 0.2 * s)
        else:  # kissaki
            k = (y - yk) / KISSAKI
            hw = BLADE_W / 2 / PHI * (1 - 0.7 * k)
            th = BLADE_TH / 2 * 0.8 * (1 - 0.6 * k)
        cx = blade_center(y)
        e = EDGE_SIGN
        rings.append(
            [
                (cx + e * hw, y, 0),  # ha (edge)
                (cx + e * 0.15 * hw, y, th),  # shinogi, upper
                (cx - e * hw, y, 0.6 * th),  # mune, upper
                (cx - e * hw, y, -0.6 * th),  # mune, lower
                (cx + e * 0.15 * hw, y, -th),  # shinogi, lower
            ]
        )
    x0, _, x1, _ = REGION["blade"]
    ts = [(y - BLADE_BASE) / (BLADE_TIP - BLADE_BASE) for y in ys]
    ux = [x0 + 1 + (x1 - x0 - 2) * t for t in ts]
    spans = [(0.5, 31.5), (32.5, 47.5), (48.5, 55.5), (47.5, 32.5), (31.5, 0.5)]
    verts = tube(part, rings, ux, spans)
    base = verts[0]
    part.face(list(reversed(base)), [(1, 28)] * 5)
    tip = part.vert((blade_center(BLADE_TIP) - EDGE_SIGN * 0.012, BLADE_TIP, 0))
    last = verts[-1]
    ut = x1 - 1
    for j in range(5):
        j2 = (j + 1) % 5
        va, vb = spans[j]
        part.face([last[j], last[j2], tip], [(ut - 6, va), (ut - 6, vb), (ut, (va + vb) / 2)])


def build_hilt(part, dy=0.0, with_blade_fittings=True):
    """Kashira, tsuka, fuchi, tsuba (and habaki). dy shifts everything along y."""
    y = lambda v: v + dy
    kc = HILT * 0.12  # kashira cap length
    fc = HILT * 0.13  # fuchi collar length
    k0, k1 = KASHIRA_END, KASHIRA_END + kc
    f0, f1 = TSUBA_Y0 - fc, TSUBA_Y0
    rx, rz = GRIP_W / 2, GRIP_W / 2 * 0.80
    ts = (f0 - k1)
    # tsuka (cloth wrap) k1 .. f0, slight belly
    stack(
        part,
        [(y(k1), rx * 0.91, rz * 0.91, 0), (y(k1 + ts * 0.3), rx * 1.02, rz * 1.02, 0),
         (y(k1 + ts * 0.62), rx * 1.05, rz * 1.05, 0), (y(f0), rx * 0.95, rz * 0.95, 0)],
        "tsuka", (y(k1), y(f0)), closed_caps=("brass", None),
    )
    # kashira (pommel cap)
    stack(
        part,
        [(y(k0), rx * 0.78, rz * 0.78, 0), (y(k0 + kc * 0.4), rx * 1.05, rz * 1.05, 0), (y(k1), rx * 0.95, rz * 0.95, 0)],
        "brass", (y(k0), y(k1)), closed_caps=("brass", None),
    )
    # fuchi (collar)
    stack(
        part,
        [(y(f0), rx * 1.12, rz * 1.12, 0), (y(f0 + fc * 0.4), rx * 1.18, rz * 1.18, 0), (y(f1), rx * 1.14, rz * 1.14, 0)],
        "brass", (y(f0), y(f1)), closed_caps=("brass", None),
    )
    # tsuba (guard): octagon disc, patterned faces
    td = TSUBA_D / 2
    rings = [octagon(0, y(TSUBA_Y0), td, td * 0.91), octagon(0, y(TSUBA_Y1), td, td * 0.91)]
    bx0, by0, bx1, _ = REGION["brass"]
    verts = tube(part, rings, [bx0 + 4, bx0 + 20], face_spans("brass"))
    tx0, ty0, tx1, ty1 = REGION["tsuba"]
    c = ((tx0 + tx1) / 2, (ty0 + ty1) / 2)
    cap(part, list(reversed(verts[0])), list(reversed(rings[0])), c, 31)
    cap(part, verts[1], rings[1], c, 31, flip_u=True)
    if with_blade_fittings:
        hb = HABAKI_LEN
        hw, hh = BLADE_W / 2 * 1.2, BLADE_TH / 2 * 1.5
        stack(
            part,
            [(y(TSUBA_Y1), hw, hh, 0), (y(TSUBA_Y1 + hb * 0.2), hw * 1.05, hh * 1.05, 0),
             (y(TSUBA_Y1 + hb * 0.85), hw * 1.02, hh * 1.02, 0), (y(TSUBA_Y1 + hb), hw * 0.9, hh * 0.9, 0)],
            "brass", (y(TSUBA_Y1), y(TSUBA_Y1 + hb)), closed_caps=("brass", None),
        )


def box(part, c, h, region, scale=1.0):
    """Axis-aligned box centred at c with half sizes h; every face maps to the whole region."""
    x0, y0, x1, y1 = REGION[region]
    cx, cy, cz = c
    hx, hy, hz = h
    p = {}
    for sx in (-1, 1):
        for sy in (-1, 1):
            for sz in (-1, 1):
                p[(sx, sy, sz)] = part.vert((cx + sx * hx, cy + sy * hy, cz + sz * hz))
    uvs = [(x0 + 1, y0 + 1), (x1 - 1, y0 + 1), (x1 - 1, y1 - 1), (x0 + 1, y1 - 1)]
    quads = [
        [(-1, -1, 1), (1, -1, 1), (1, 1, 1), (-1, 1, 1)],
        [(1, -1, -1), (-1, -1, -1), (-1, 1, -1), (1, 1, -1)],
        [(-1, -1, -1), (-1, -1, 1), (-1, 1, 1), (-1, 1, -1)],
        [(1, -1, 1), (1, -1, -1), (1, 1, -1), (1, 1, 1)],
        [(-1, 1, 1), (1, 1, 1), (1, 1, -1), (-1, 1, -1)],
        [(-1, -1, -1), (1, -1, -1), (1, -1, 1), (-1, -1, 1)],
    ]
    for q in quads:
        part.face([p[k] for k in q], uvs)


def build_sheath(part):
    Ls = SHEATH_LEN

    def sc(y):
        return -SORI * (y / Ls) ** 2 * EDGE_SIGN

    def prof(t):
        return 0.052 - 0.014 * t, 0.038 - 0.010 * t  # rx, rz from the mouth to the tip

    # body, ten rings, tapering
    ys = [0.05 + (Ls - 0.1) * i / 10 for i in range(11)]
    specs = []
    for yy in ys:
        rx, rz = prof(yy / Ls)
        specs.append((yy, rx, rz, sc(yy)))
    stack(part, specs, "saya", (ys[0], ys[-1]), closed_caps=("brass", None))
    # kuchigane (mouth collar)
    rx0, rz0 = prof(0)
    stack(
        part,
        [(0.0, rx0 * 1.06, rz0 * 1.06, sc(0)), (0.018, rx0 * 1.10, rz0 * 1.10, sc(0.018)),
         (0.05, rx0 * 1.04, rz0 * 1.04, sc(0.05))],
        "brass", (0.0, 0.05), closed_caps=("brass", None),
    )
    # kojiri (end cap)
    rx1, rz1 = prof(1)
    stack(
        part,
        [(Ls - 0.075, rx1 * 1.04, rz1 * 1.04, sc(Ls - 0.075)), (Ls - 0.045, rx1 * 1.12, rz1 * 1.12, sc(Ls - 0.045)),
         (Ls, rx1 * 0.74, rz1 * 0.74, sc(Ls))],
        "brass", (Ls - 0.075, Ls), closed_caps=("brass", None),
    )
    # sageo (cord): a wrap near the mouth, a knot on the +Z face and two ends that splay apart
    w0, w1 = 0.09, 0.17
    rxw, rzw = prof(w0 / Ls)
    stack(
        part,
        [(w0, rxw * 1.10, rzw * 1.12, sc(w0)), (w0 + 0.02, rxw * 1.16, rzw * 1.18, sc(w0 + 0.02)),
         (w1 - 0.02, rxw * 1.16, rzw * 1.18, sc(w1 - 0.02)), (w1, rxw * 1.10, rzw * 1.12, sc(w1))],
        "cord", (w0, w1), closed_caps=("cord", None),
    )
    zc = rzw * 1.18 + 0.010
    ym = (w0 + w1) / 2
    box(part, (sc(ym), ym, zc), (0.020, 0.028, 0.012), "cord")
    strand(part, (sc(w1) - 0.006, w1, zc), (sc(0.42) - 0.024, 0.42, zc - 0.004), 0.009)
    strand(part, (sc(w1) + 0.006, w1, zc), (sc(0.37) + 0.028, 0.37, zc - 0.004), 0.009)
    box(part, (sc(0.42) - 0.024, 0.446, zc - 0.004), (0.012, 0.028, 0.012), "brass")
    box(part, (sc(0.37) + 0.028, 0.396, zc - 0.004), (0.012, 0.028, 0.012), "brass")


def strand(part, p0, p1, w):
    """Thin square-section rope from p0 to p1 (both model space)."""
    def ring(p):
        return [(p[0] - w, p[1], p[2] - w), (p[0] + w, p[1], p[2] - w), (p[0] + w, p[1], p[2] + w),
                (p[0] - w, p[1], p[2] + w)]

    x0, y0, x1, y1 = REGION["cord"]
    spans = [(y0 + 1.0 + 15 * k, y0 + 15 * (k + 1)) for k in range(4)]
    verts = tube(part, [ring(p0), ring(p1)], [x0 + 1, x1 - 1], spans)
    part.face(list(reversed(verts[0])), [(x0 + 2, y0 + 2)] * 4)
    part.face(verts[1], [(x0 + 2, y0 + 2)] * 4)


def new_model(name, builders):
    for obj in list(bpy.data.objects):
        bpy.data.objects.remove(obj, do_unlink=True)
    for builder, pname in builders:
        part = Part(pname)
        builder(part)
        part.build()
    objs = [o for o in bpy.data.objects]
    tris = 0
    for o in objs:
        me = o.data
        me.calc_loop_triangles()
        tris += len(me.loop_triangles)
    return objs, tris


def export(objs, path):
    bpy.ops.object.select_all(action="DESELECT")
    for o in objs:
        o.select_set(True)
    bpy.context.view_layer.objects.active = objs[0]
    bpy.ops.wm.obj_export(
        filepath=path,
        export_selected_objects=True,
        forward_axis="NEGATIVE_Z",
        up_axis="Y",
        export_materials=False,
        export_uv=True,
        export_normals=True,
        export_triangulated_mesh=True,
        export_object_groups=False,
        export_material_groups=False,
        apply_modifiers=False,
        global_scale=1.0,
    )


def bounds(path):
    xs, ys, zs = [], [], []
    for line in open(path):
        if line.startswith("v "):
            _, x, y, z = line.split()[:4]
            xs.append(float(x)); ys.append(float(y)); zs.append(float(z))
    return [round(min(a), 3) for a in (xs, ys, zs)], [round(max(a), 3) for a in (xs, ys, zs)]


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    objs, tris = new_model(
        "katana",
        [(build_blade, "blade"), (lambda p: build_hilt(p, 0.0, True), "hilt")],
    )
    bpy.ops.wm.save_as_mainfile(filepath=os.path.join(OUT, "katana_drawn.blend"))
    export(objs, os.path.join(OUT, "katana.obj"))
    print("KATANA tris", tris, bounds(os.path.join(OUT, "katana.obj")))
    print("PROPORTIONS", dict(L=round(L,3), hilt=round(HILT,3), blade=round(BLADE,3), tsuba_d=round(TSUBA_D,3), blade_w=round(BLADE_W,4), sheath=round(SHEATH_LEN,3), tip=round(TIP_Y,3), kashira=round(KASHIRA_END,3)))

    objs, tris = new_model(
        "sheath",
        [(build_sheath, "saya"), (lambda p: build_hilt(p, -TSUBA_Y1, False), "hilt")],
    )
    bpy.ops.wm.save_as_mainfile(filepath=os.path.join(OUT, "katana_sheathed.blend"))
    export(objs, os.path.join(OUT, "sheath.obj"))
    print("SHEATH tris", tris, bounds(os.path.join(OUT, "sheath.obj")))

    # sheath without the hilt: drawn state (the katana is in the hand, the scabbard is empty)
    objs, tris = new_model("sheath_empty", [(build_sheath, "saya")])
    export(objs, os.path.join(OUT, "sheath_empty.obj"))
    print("SHEATH_EMPTY tris", tris, bounds(os.path.join(OUT, "sheath_empty.obj")))
