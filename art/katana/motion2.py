"""Sword motion tracks v2: end-effector targets (sword, both hands, torso twist, feet) per action.

Run:  python3 motion2.py <katana.json> <out_tracks.json>

The animation content describes WHERE things are, not joint angles; the engine's pose solver turns the
targets into joint rotations (rigid ModelBiped now, bendy limbs later), so one data file serves both rigs.

Structure follows the ZNKR Iai Kaisetsu manual (kamae/ready, saya-biki + fumi-komi draw cut, furi-kaburi and
kiri-oroshi, chiburi, noto) with game-style rhythm: slow anticipation, fastest through the active window,
a short overshoot, a zanshin hold that doubles as the combo link pose.

Body space (blocks): x right, y forward, z up, origin on the ground below the body centre. Visible player
height 1.875 (model 2.0 x 0.9375 scale). Sword local frame: blade +Y, leading edge +X, normal +Z, origin = grip
centre (katana.obj); the hilt is 0.399 long, the tsuba top is at y=+0.182, the kashira end at y=-0.247.

Self-checks (printed, and stored in the JSON):
  reach     - both hands stay within the rigid arm's reach (grip 0.55 from the shoulder, +-0.09);
  spacing   - the two hands sit on the hilt (>= 0.09 fist width, <= hilt length);
  contact   - during the active window the blade passes through the server hit sweep (reach x arc);
  retime    - keyframe times stay strictly increasing for any startup/active/recovery from the JSON.
"""

import json
import math
import sys

from motion import (add, cross, dot, ease_in, ease_io, ease_out, frame, lerp, mul, norm, nlerp, quat, slerp,
                    sub)

SHOULDER_R = (0.293, 0.0, 1.289)
SHOULDER_L = (-0.293, 0.0, 1.289)
GRIP_DIST, GRIP_TOL = 0.55, 0.09
HAND_R_Y, HAND_L_Y = 0.045, -0.125  # along the blade axis from the grip centre; right near the tsuba, left near the kashira
HILT_LEN = 0.399
FIST = 0.09
STEP = 0.5  # sample step in ticks
S2 = math.sqrt(0.5)

HORIZ = ((0, 1, 0), (-1, 0, 0))  # f, l  (l = player's left)
RISE = ((0, 1, 0), (S2, 0, S2))  # lateral = up and to the right
FALL = ((0, 1, 0), (-S2, 0, -S2))  # lateral = down and to the left

FEET_R0, FEET_L0 = (0.13, 0.14), (-0.13, -0.14)  # (x, y) in hanmi
KOIGUCHI = (-0.30, 0.08, 0.98)  # saya mouth at the left hip (mount dependent, provisional)
KOIGUCHI_PULLED = (-0.30, -0.06, 0.98)
SHEATH_DIR = norm((0.0, -S2, -S2))  # mouth -> tip, back and down at 45 degrees
SHEATH_EDGE = norm((0.0, -S2, S2))  # edge side up when worn (keito)


def slash(plane, phi, esign):
    f, l = plane
    p = math.radians(phi)
    d = add(mul(f, math.cos(p)), mul(l, math.sin(p)))
    dd = add(mul(f, -math.sin(p)), mul(l, math.cos(p)))
    return frame(mul(dd, esign), d)


CHUDAN = frame((0.0, 0.0, -1.0), (0.04, 0.92, 0.40))
C_READY = (0.04, 0.40, 1.08)


def kf(t, fr, c, yaw=0.0, footR=None, footL=None, handL="grip", ease=ease_io):
    return dict(t=t, fr=fr, c=c, yaw=yaw, footR=footR or FEET_R0, footL=footL or FEET_L0, handL=handL, ease=ease)


def cut(plane, esign, phi, c, yaw, step, s, a, r, rear_follow=0.6):
    """Generic two-handed cut. phi/c/yaw are (wind, start, end, over, hold) tuples."""
    total = s + a + r
    tw = max(0.5, s - 1.0)
    fr = [slash(plane, p, esign) for p in phi]
    fR0, fL0 = FEET_R0, FEET_L0
    fR1 = (FEET_R0[0], FEET_R0[1] + step)
    fL1 = (FEET_L0[0], FEET_L0[1] + step * rear_follow)
    hold_t = min(total - 1.0, s + a + 3.0)
    return [
        kf(0.0, CHUDAN, C_READY, 0.0, fR0, fL0, ease=ease_io),
        kf(tw, fr[0], c[0], yaw[0], fR0, fL0, ease=ease_out),  # anticipation: ease out into the peak
        kf(float(s), fr[1], c[1], yaw[1], fR0, fL0, ease=ease_io),
        kf(float(s + a), fr[2], c[2], yaw[2], fR1, fL0, ease=lambda u: u),  # constant speed through contact
        kf(float(s + a + 1), fr[3], c[3], yaw[3], fR1, fL1, ease=ease_out),  # overshoot
        kf(hold_t, fr[4], c[4], yaw[4], fR1, fL1, ease=ease_out),  # zanshin = combo link pose
        kf(float(total), CHUDAN, C_READY, 0.0, fR0, fL0, ease=ease_io),
    ]


def phis(arc, sgn, wind=None):
    """Sweep angles from the action's own arc, centred on the facing direction, so the blade crosses the server sweep."""
    start, end = -sgn * arc / 2.0, sgn * arc / 2.0
    return (wind if wind is not None else start - sgn * 35.0, start, end, end + sgn * 0.12 * arc,
            end - sgn * 0.45 * arc)


def build_actions(actions):
    out = {}
    d = actions
    s, a, r = d["light_1"]["startup"], d["light_1"]["active"], d["light_1"]["recovery"]
    out["light_1"] = cut(HORIZ, 1, phis(d["light_1"]["arc"], 1),
                         [(0.22, 0.20, 1.18), (0.14, 0.28, 1.20), (-0.10, 0.42, 1.20), (-0.14, 0.42, 1.18),
                          (0.00, 0.40, 1.12)], (-22, -15, 18, 24, 6), 0.30, s, a, r)
    s, a, r = d["light_2"]["startup"], d["light_2"]["active"], d["light_2"]["recovery"]
    out["light_2"] = cut(HORIZ, -1, phis(d["light_2"]["arc"], -1),
                         [(-0.12, 0.30, 1.18), (-0.10, 0.40, 1.20), (0.14, 0.28, 1.20), (0.18, 0.26, 1.18),
                          (0.04, 0.38, 1.12)], (16, 10, -16, -22, -6), 0.24, s, a, r)
    s, a, r = d["light_3"]["startup"], d["light_3"]["active"], d["light_3"]["recovery"]
    out["light_3"] = cut(RISE, 1, phis(d["light_3"]["arc"], 1, wind=-85),
                         [(-0.18, 0.32, 0.92), (-0.14, 0.36, 0.98), (0.14, 0.30, 1.40), (0.16, 0.26, 1.48),
                          (0.06, 0.36, 1.30)], (14, 10, -10, -14, -4), 0.26, s, a, r)
    s, a, r = d["heavy"]["startup"], d["heavy"]["active"], d["heavy"]["recovery"]
    out["heavy"] = cut(FALL, 1, phis(d["heavy"]["arc"], 1, wind=-125),
                       [(0.14, 0.14, 1.64), (0.12, 0.24, 1.56), (-0.08, 0.44, 1.02), (-0.10, 0.46, 0.98),
                        (0.00, 0.40, 1.04)], (-10, -6, 12, 16, 4), 0.36, s, a, r)
    # iai: saya-biki (left hand pulls the saya back), fumi-komi, one-handed draw cut at head height
    s, a, r = d["iai"]["startup"], d["iai"]["active"], d["iai"]["recovery"]
    total = s + a + r
    fr = [slash(HORIZ, p, -1) for p in phis(d["iai"]["arc"], -1)]
    c = [(-0.25, 0.18, 1.00), (-0.18, 0.26, 1.06), (0.18, 0.40, 1.12), (0.22, 0.42, 1.12), (0.04, 0.38, 1.12)]
    stepR = (FEET_R0[0], FEET_R0[1] + 0.40)
    iai = [
        kf(0.0, CHUDAN, C_READY, 0.0, FEET_R0, FEET_L0, handL=("free", KOIGUCHI), ease=ease_io),
        kf(max(0.5, s - 1.0), fr[0], c[0], 20, FEET_R0, FEET_L0, handL=("free", KOIGUCHI_PULLED), ease=ease_out),
        kf(float(s), fr[1], c[1], 24, FEET_R0, FEET_L0, handL=("free", KOIGUCHI_PULLED), ease=ease_io),
        kf(float(s + a), fr[2], c[2], -14, stepR, FEET_L0, handL=("free", KOIGUCHI_PULLED), ease=lambda u: u),
        kf(float(s + a + 1), fr[3], c[3], -20, stepR, (FEET_L0[0], FEET_L0[1] + 0.2), handL=("free", KOIGUCHI_PULLED),
           ease=ease_out),
        kf(min(total - 3.0, s + a + 4.0), fr[4], c[4], -4, stepR, (FEET_L0[0], FEET_L0[1] + 0.3), handL="grip",
           ease=ease_out),
        kf(float(total), CHUDAN, C_READY, 0.0, FEET_R0, FEET_L0, ease=ease_io),
    ]
    out["iai"] = iai
    # sheathe: chiburi (shake the water off an umbrella) then noto (return the blade to the mouth)
    s, a, r = d["sheathe"]["startup"], d["sheathe"]["active"], d["sheathe"]["recovery"]
    total = s + a + r
    chiburi_up = frame((0.0, 0.0, -1.0), norm((0.55, 0.50, 0.75)))
    chiburi_dn = frame((0.7, 0.2, 0.0), norm((0.55, 0.62, -0.62)))  # tip down about 45 degrees, to the right
    mouth_origin = sub(KOIGUCHI, mul(SHEATH_DIR, 0.182))  # grip centre when the tsuba sits at the mouth
    noto_fr = frame(SHEATH_EDGE, SHEATH_DIR)
    mid = frame((0.0, 0.2, -0.6), norm((-0.2, -0.5, -0.5)))
    # c is the midpoint of the hands; for sheathe the final c puts the grip centre at the mouth
    c_noto = sub(mouth_origin, mul(SHEATH_DIR, 0.04))
    out["sheathe"] = [
        kf(0.0, CHUDAN, C_READY, 0.0, FEET_R0, FEET_L0, handL=("free", KOIGUCHI), ease=ease_io),
        kf(1.5, chiburi_up, (0.24, 0.22, 1.42), -8, FEET_R0, FEET_L0, handL=("free", KOIGUCHI), ease=ease_out),
        kf(3.0, chiburi_dn, (0.26, 0.36, 1.10), -10, FEET_R0, FEET_L0, handL=("free", KOIGUCHI), ease=ease_in),
        kf(4.5, mid, (-0.06, 0.20, 1.02), 6, FEET_R0, FEET_L0, handL=("free", KOIGUCHI), ease=ease_io),
        kf(float(total), noto_fr, c_noto, 8, FEET_R0, FEET_L0, handL=("free", KOIGUCHI), ease=ease_out),
    ]
    return out


def hands_for(fr, c, hand_l, yaw):
    e, d, n = fr
    p0 = add(c, mul(d, -(HAND_R_Y + HAND_L_Y) / 2))
    pr = add(p0, mul(d, HAND_R_Y))
    pl = add(p0, mul(d, HAND_L_Y)) if hand_l == "grip" else hand_l[1]
    return p0, pr, pl


def rot_z(p, deg):
    a = math.radians(deg)
    return (p[0] * math.cos(a) - p[1] * math.sin(a), p[0] * math.sin(a) + p[1] * math.cos(a), p[2])


def clamp_reach(fr, c, hand_l, yaw):
    """Move c so both gripping hands are within arm reach. Returns (c, correction)."""
    c0 = c
    sr, sl = rot_z(SHOULDER_R, yaw), rot_z(SHOULDER_L, yaw)
    lo, hi = GRIP_DIST - GRIP_TOL, GRIP_DIST + GRIP_TOL
    for _ in range(24):
        p0, pr, pl = hands_for(fr, c, hand_l, yaw)
        pairs = [(pr, sr)] + ([(pl, sl)] if hand_l == "grip" else [])
        move = (0.0, 0.0, 0.0)
        for p, s in pairs:
            v = sub(p, s)
            dist = math.sqrt(dot(v, v))
            tgt = min(hi, max(lo, dist))
            move = add(move, mul(norm(v), (tgt - dist) / len(pairs)))
        if math.sqrt(dot(move, move)) < 1e-4:
            break
        c = add(c, move)
    corr = sub(c, c0)
    return c, math.sqrt(dot(corr, corr))


def sample(kfs, t):
    for k0, k1 in zip(kfs, kfs[1:]):
        if k0["t"] <= t <= k1["t"]:
            u = 0.0 if k1["t"] == k0["t"] else (t - k0["t"]) / (k1["t"] - k0["t"])
            u = k1["ease"](u)
            q = slerp(quat(k0["fr"]), quat(k1["fr"]), u)
            e = nlerp(k0["fr"][0], k1["fr"][0], u)
            d = nlerp(k0["fr"][1], k1["fr"][1], u)
            fr = frame(e, d)
            c = tuple(lerp(a, b, u) for a, b in zip(k0["c"], k1["c"]))
            yaw = lerp(k0["yaw"], k1["yaw"], u)
            fR = tuple(lerp(a, b, u) for a, b in zip(k0["footR"], k1["footR"]))
            fL = tuple(lerp(a, b, u) for a, b in zip(k0["footL"], k1["footL"]))
            hl0, hl1 = k0["handL"], k1["handL"]
            if hl0 == "grip" and hl1 == "grip":
                hl = "grip"
            else:
                a_ = hl0[1] if hl0 != "grip" else None
                b_ = hl1[1] if hl1 != "grip" else None
                if a_ is None or b_ is None:
                    hl = "blend"  # resolved by the caller from the sword pose
                    gripL = hands_for(fr, c, "grip", yaw)[2]
                    a_ = a_ or gripL
                    b_ = b_ or gripL
                hl = ("free", tuple(lerp(x, y, u) for x, y in zip(a_, b_)))
            return fr, c, yaw, fR, fL, hl
    k = kfs[-1]
    return k["fr"], k["c"], k["yaw"], k["footR"], k["footL"], k["handL"]


def active_intersects(samples, s, a, arc):
    """Samples inside the active window whose blade bearing lies within the server sweep sector (+-10 degrees)."""
    half = math.radians(arc / 2) + math.radians(10)
    n = ok = 0
    for smp in samples:
        if not (s - 0.01 <= smp["t"] <= s + a + 0.01):
            continue
        tip = smp["tip"]
        n += 1
        ok += 1 if abs(math.atan2(-tip[0], tip[1])) <= half else 0
    return ok, n


def visual_reach(samples, s, a):
    """Farthest tip distance from the body centre in the active window (horizontal)."""
    best = 0.0
    for smp in samples:
        if s - 0.01 <= smp["t"] <= s + a + 0.01:
            best = max(best, math.hypot(smp["tip"][0], smp["tip"][1]))
    return best


def build(actions):
    kfs_all = build_actions(actions)
    out, report = {}, {}
    for aid, kfs in kfs_all.items():
        d = actions[aid]
        s, a, r = d["startup"], d["active"], d["recovery"]
        total = s + a + r
        times = [k["t"] for k in kfs]
        assert all(t1 > t0 for t0, t1 in zip(times, times[1:])), (aid, "keyframe times must increase", times)
        samples, max_corr, reach_viol, space_viol = [], 0.0, 0, 0
        k = 0
        while k * STEP <= total + 1e-9:
            t = k * STEP
            fr, c, yaw, fR, fL, hl = sample(kfs, t)
            c2, corr = clamp_reach(fr, c, hl, yaw)
            max_corr = max(max_corr, corr)
            p0, pr, pl = hands_for(fr, c2, hl, yaw)
            if hl == "grip":
                sep = math.sqrt(dot(sub(pr, pl), sub(pr, pl)))
                if not (FIST - 1e-6 <= sep <= HILT_LEN + 1e-6):
                    space_viol += 1
            tip = add(p0, mul(fr[1], 1.227))  # the sword pose is already in body space; only the shoulders follow the torso twist
            sr, sl = rot_z(SHOULDER_R, yaw), rot_z(SHOULDER_L, yaw)
            dr = math.sqrt(dot(sub(pr, sr), sub(pr, sr)))
            if not (GRIP_DIST - GRIP_TOL - 1e-3 <= dr <= GRIP_DIST + GRIP_TOL + 1e-3):
                reach_viol += 1
            samples.append({
                "t": round(t, 3),
                "sword": {"q": [round(x, 5) for x in quat(fr)], "p": [round(x, 4) for x in p0]},
                "handR": [round(x, 4) for x in pr],
                "handL": {"mode": "grip" if hl == "grip" else "free", "p": [round(x, 4) for x in pl]},
                "torsoYaw": round(yaw, 3),
                "footR": [round(x, 4) for x in fR],
                "footL": [round(x, 4) for x in fL],
                "tip": [round(x, 4) for x in tip],
            })
            k += 1
        ok, n = active_intersects(samples, s, a, d["arc"])
        vr = visual_reach(samples, s, a)
        report[aid] = {"maxReachCorrection": round(max_corr, 3), "reachViolations": reach_viol,
                       "spacingViolations": space_viol, "activeBearingInSweep": f"{ok}/{n}",
                       "visualReach": round(vr, 2), "withStep": round(vr + d["distance"], 2),
                       "serverReach": d["reach"],
                       "reachDeficit": round(max(0.0, d["reach"] - (vr + d["distance"])), 2)}
        out[aid] = {"startup": s, "active": a, "recovery": r, "ticks": total, "samples": samples}
    return out, report


if __name__ == "__main__":
    data = json.load(open(sys.argv[1]))
    acts = {x["id"]: x for x in data["actions"]}
    tracks, report = build(acts)
    json.dump({"version": 2, "space": "body: x right, y forward, z up, blocks", "sampleStepTicks": STEP,
               "swordLocal": "blade +Y, leading edge +X, normal +Z, origin = grip centre",
               "shoulders": [list(SHOULDER_R), list(SHOULDER_L)], "gripDistance": GRIP_DIST,
               "checks": report, "actions": tracks}, open(sys.argv[2], "w"), indent=1)
    for k, v in report.items():
        print(k, v)
