"""Generates per-action sword motion tracks for the katana from the action timings in katana.json.

Run:  python3 motion.py <katana.json> <out_tracks.json>

Why tracks instead of the placeholder 'rigid_arc': every action used the same one-angle spin around the grip.
Here each action gets its own plane, direction and edge side, and the keyframes are placed on the action's
own phases so retiming katana.json retimes the animation:

    t=0        ready (chudan)
    t=s-1      windup peak (anticipation, ease-out into it)
    t=s        sweep start      <- hit sweep begins when the action becomes active
    t=s+a      sweep end        <- contact window [s, s+a] is the middle of the arc
    t=s+a+1    follow-through (small overshoot)
    t=T        back to ready (ease-in-out)

Body space: x right, y forward, z up, blocks, origin on the ground below the body centre.
Sword local frame: blade +Y, leading (cutting) edge +X, flat normal +Z = X x Y  (matches katana.obj with EDGE_SIGN=1).
The arm is a rigid segment from the shoulder; the grip sits GRIP_DISTANCE along it (KatanaRendering uses 0.55).
"""

import json
import math
import sys

SHOULDER = (0.3125, 0.0, 1.375)
GRIP_DISTANCE = 0.55
HAND_CENTER = (SHOULDER[0] - 0.22, SHOULDER[1] + 0.40, SHOULDER[2] - 0.10)
SAMPLE_STEP = 0.5  # ticks


def add(a, b): return (a[0] + b[0], a[1] + b[1], a[2] + b[2])
def sub(a, b): return (a[0] - b[0], a[1] - b[1], a[2] - b[2])
def mul(a, k): return (a[0] * k, a[1] * k, a[2] * k)
def dot(a, b): return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
def cross(a, b): return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])
def norm(a):
    n = math.sqrt(dot(a, a)) or 1.0
    return (a[0] / n, a[1] / n, a[2] / n)


def frame(edge, blade):
    d = norm(blade)
    e = norm(sub(edge, mul(d, dot(edge, d))))
    return (e, d, cross(e, d))


def quat(f):
    e, d, n = f
    m00, m10, m20 = e
    m01, m11, m21 = d
    m02, m12, m22 = n
    tr = m00 + m11 + m22
    if tr > 0:
        s = math.sqrt(tr + 1.0) * 2
        q = ((m21 - m12) / s, (m02 - m20) / s, (m10 - m01) / s, 0.25 * s)
    elif m00 > m11 and m00 > m22:
        s = math.sqrt(1.0 + m00 - m11 - m22) * 2
        q = (0.25 * s, (m01 + m10) / s, (m02 + m20) / s, (m21 - m12) / s)
    elif m11 > m22:
        s = math.sqrt(1.0 + m11 - m00 - m22) * 2
        q = ((m01 + m10) / s, 0.25 * s, (m12 + m21) / s, (m02 - m20) / s)
    else:
        s = math.sqrt(1.0 + m22 - m00 - m11) * 2
        q = ((m02 + m20) / s, (m12 + m21) / s, 0.25 * s, (m10 - m01) / s)
    n = math.sqrt(sum(c * c for c in q))
    return tuple(c / n for c in q)


def slerp(q0, q1, t):
    d = sum(a * b for a, b in zip(q0, q1))
    if d < 0:
        q1 = tuple(-c for c in q1)
        d = -d
    if d > 0.9995:
        q = tuple(a + (b - a) * t for a, b in zip(q0, q1))
    else:
        th = math.acos(d)
        s0 = math.sin((1 - t) * th) / math.sin(th)
        s1 = math.sin(t * th) / math.sin(th)
        q = tuple(a * s0 + b * s1 for a, b in zip(q0, q1))
    n = math.sqrt(sum(c * c for c in q))
    return tuple(c / n for c in q)


def nlerp(a, b, t):
    return norm(add(mul(a, 1 - t), mul(b, t)))


def lerp(a, b, t): return a + (b - a) * t
def ease_out(t): return 1 - (1 - t) ** 2
def ease_in(t): return t * t
def ease_io(t): return t * t * (3 - 2 * t)


# Slash planes: (forward-in-plane f, lateral-in-plane l); blade D(phi) = cos(phi) f + sin(phi) l.
S2 = math.sqrt(0.5)
HORIZ_LEFT = ((0, 1, 0), (-1, 0, 0))
RISE_RIGHT = ((0, 1, 0), (S2, 0, S2))   # lateral points up and to the right
FALL_LEFT = ((0, 1, 0), (-S2, 0, -S2))  # lateral points down and to the left

# phi angles in degrees: windup peak, sweep start, sweep end; esign +1 when phi increases during the sweep.
SPEC = {
    "iai": dict(plane=HORIZ_LEFT, wind=95, start=70, end=-20, esign=-1, twist=24, over=0.18),
    "light_1": dict(plane=HORIZ_LEFT, wind=-95, start=-70, end=50, esign=1, twist=20, over=0.15),
    "light_2": dict(plane=HORIZ_LEFT, wind=75, start=50, end=-70, esign=-1, twist=20, over=0.15),
    "light_3": dict(plane=RISE_RIGHT, wind=-85, start=-60, end=55, esign=1, twist=16, over=0.15),
    "heavy": dict(plane=FALL_LEFT, wind=-120, start=-75, end=45, esign=1, twist=22, over=0.20),
}

READY_BLADE = norm((0.10, 0.90, 0.42))
READY_EDGE = (0.0, 0.0, -1.0)


def pose_from_phi(spec, phi_deg):
    f, l = spec["plane"]
    p = math.radians(phi_deg)
    d = add(mul(f, math.cos(p)), mul(l, math.sin(p)))
    dd = add(mul(f, -math.sin(p)), mul(l, math.cos(p)))
    return frame(mul(dd, spec["esign"]), d)


def arm_for(f):
    e, d, n = f
    g = add(HAND_CENTER, mul(d, 0.16))
    arm = norm(sub(g, SHOULDER))
    return arm


def body_yaw(spec, f):
    return spec["twist"] * dot(f[1], (-1.0, 0.0, 0.0))  # twist toward the side the blade points to


def keyframes(spec, s, a, r):
    total = s + a + r
    ready = frame(READY_EDGE, READY_BLADE)
    wind = pose_from_phi(spec, spec["wind"])
    start = pose_from_phi(spec, spec["start"])
    end = pose_from_phi(spec, spec["end"])
    over_phi = spec["end"] + spec["over"] * (spec["end"] - spec["start"])
    over = pose_from_phi(spec, over_phi)
    t_wind = max(0.5, s - 1.0)
    return [
        (0.0, ready, None),
        (t_wind, wind, ease_out),
        (float(s), start, ease_io),
        (float(s + a), end, None),  # constant speed through the contact window
        (float(s + a + 1), over, ease_out),
        (float(total), ready, ease_io),
    ]


def sample(kfs, t):
    for (t0, f0, _), (t1, f1, ease) in zip(kfs, kfs[1:]):
        if t0 <= t <= t1:
            u = 0.0 if t1 == t0 else (t - t0) / (t1 - t0)
            if ease is not None:
                u = ease(u)
            else:
                u = u
            q = slerp(quat(f0), quat(f1), u)
            arm0, arm1 = arm_for(f0), arm_for(f1)
            arm = nlerp(arm0, arm1, u)
            yaw = lerp(body_yaw_cache(f0), body_yaw_cache(f1), u)
            return q, arm, yaw
    f = kfs[-1][1]
    return quat(f), arm_for(f), 0.0


_SPEC = {}


def body_yaw_cache(f):
    return body_yaw(_SPEC["spec"], f)


def build(actions):
    out = {}
    for aid, spec in SPEC.items():
        if aid not in actions:
            continue
        d = actions[aid]
        s, a, r = d["startup"], d["active"], d["recovery"]
        _SPEC["spec"] = spec
        kfs = keyframes(spec, s, a, r)
        total = s + a + r
        samples = []
        k = 0
        while k * SAMPLE_STEP <= total + 1e-9:
            t = k * SAMPLE_STEP
            q, arm, yaw = sample(kfs, t)
            samples.append(
                {"t": round(t, 3), "q": [round(c, 5) for c in q], "arm": [round(c, 5) for c in arm],
                 "bodyYaw": round(yaw, 3)}
            )
            k += 1
        out[aid] = {"startup": s, "active": a, "recovery": r, "ticks": total, "reach": d["reach"],
                    "arc": d["arc"], "samples": samples}
    return out


if __name__ == "__main__":
    data = json.load(open(sys.argv[1]))
    actions = {a["id"]: a for a in data["actions"]}
    tracks = {
        "version": 1,
        "space": "body: x right, y forward, z up, blocks",
        "swordLocal": "blade +Y, leading edge +X, normal +Z",
        "shoulder": list(SHOULDER),
        "gripDistance": GRIP_DISTANCE,
        "sampleStepTicks": SAMPLE_STEP,
        "actions": build(actions),
    }
    json.dump(tracks, open(sys.argv[2], "w"), indent=1)
    print("TRACKS", {k: len(v["samples"]) for k, v in tracks["actions"].items()})
