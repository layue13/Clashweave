"""Paints the 256x256 shared atlas for the katana (direction A: low-poly, hand-painted colour blocks).

Run:  python paint_atlas.py <out.png>      (needs Pillow, numpy)

The palette is kept in PALETTE so skins are a colour swap, not a repaint. Regions must match
REGION in build_models.py.
"""

import math
import random
import sys

import numpy as np
from PIL import Image, ImageDraw

A = 256
REGION = {
    "blade": (0, 0, 256, 56),
    "tsuka": (0, 56, 128, 120),
    "saya": (128, 56, 256, 120),
    "brass": (0, 120, 128, 184),
    "tsuba": (128, 120, 192, 184),
    "cord": (192, 120, 256, 184),
}

PALETTE = {
    "steel": (122, 132, 148),
    "steel_dark": (78, 88, 104),
    "steel_mid": (104, 114, 130),
    "steel_light": (222, 230, 238),
    "hamon": (226, 232, 240),
    "mune": (62, 70, 84),
    "wrap": (36, 38, 44),
    "wrap_edge": (22, 23, 28),
    "diamond": (205, 196, 168),
    "diamond_edge": (92, 84, 66),
    "lacquer": (40, 42, 50),
    "brass_hi": (150, 120, 66),
    "brass": (106, 82, 42),
    "brass_lo": (60, 46, 24),
    "cord": (98, 74, 56),
    "cord_dark": (70, 52, 38),
    "tsuba_dark": (50, 48, 54),
    "cut": (22, 18, 10),
    "filler": (60, 60, 66),
}


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def paint(path):
    rnd = random.Random(7)
    img = Image.new("RGB", (A, A), PALETTE["filler"])
    px = img.load()
    d = ImageDraw.Draw(img)

    # ---- blade: rows 0..55, u along the length
    x0, y0, x1, y1 = REGION["blade"]
    for x in range(x0, x1):
        boundary = 12 + 3.0 * math.sin(x / 9.0) + 1.6 * math.sin(x / 3.7 + 1.0)
        for r in range(0, 32):  # hira (edge -> ridge)
            if r < 3:
                c = PALETTE["steel_light"]  # polished cutting edge
            elif r < boundary:
                c = PALETTE["hamon"] if r < boundary - 1 else lerp(PALETTE["hamon"], PALETTE["steel"], 0.5)
            else:
                t = (r - boundary) / max(1.0, 31 - boundary)
                c = lerp(PALETTE["steel"], PALETTE["steel_dark"], t * 0.9)
            if r == 31:
                c = PALETTE["steel_light"]  # shinogi ridge catches light
            px[x, y0 + r] = c
        for r in range(32, 48):  # shinogi-ji
            t = (r - 32) / 15
            px[x, y0 + r] = lerp(PALETTE["steel_mid"], PALETTE["steel_dark"], t)
        for r in range(48, 56):  # mune
            px[x, y0 + r] = PALETTE["mune"] if r > 49 else lerp(PALETTE["mune"], PALETTE["steel_mid"], 0.5)
    # a few worn scratches
    for _ in range(14):
        sx = rnd.randint(x0 + 4, x1 - 40)
        sy = rnd.randint(14, 28)
        ln = rnd.randint(8, 26)
        for i in range(ln):
            xx, yy = sx + i, sy + (i // 9)
            if x0 <= xx < x1 and 0 <= yy < 32:
                px[xx, yy] = lerp(px[xx, yy], PALETTE["steel_light"], 0.5)

    # ---- tsuka: 8 faces x 8px, diamond lattice of samegawa showing through the wrap
    x0, y0, x1, y1 = REGION["tsuka"]
    d.rectangle([x0, y0, x1 - 1, y1 - 1], fill=PALETTE["wrap"])
    for k in range(8):
        top = y0 + 8 * k
        shade = 0.12 * math.cos(math.radians(22.5 + 45 * k - 60))
        for x in range(x0, x1):
            for r in range(8):
                px[x, top + r] = lerp(PALETTE["wrap"], (70, 72, 82), max(0.0, shade) + (0.05 if r == 0 else 0.0))
        for n in range(0, 8):
            cx = x0 + 8 + 16 * n + (8 if k % 2 else 0)
            cy = top + 4
            pts = [(cx - 7, cy), (cx, cy - 3), (cx + 7, cy), (cx, cy + 3)]
            d.polygon(pts, fill=PALETTE["diamond"], outline=PALETTE["diamond_edge"])
        d.line([(x0, top), (x1 - 1, top)], fill=PALETTE["wrap_edge"])

    # ---- saya: lacquer, 8 faces with facet lighting (light from upper left)
    x0, y0, x1, y1 = REGION["saya"]
    for k in range(8):
        top = y0 + 8 * k
        ang = math.radians(22.5 + 45 * k)
        light = 0.5 + 0.5 * math.cos(ang - math.radians(135))
        base = lerp((18, 19, 24), (60, 63, 76), light)
        for x in range(x0, x1):
            for r in range(8):
                n = rnd.randint(-2, 2)
                c = tuple(max(0, min(255, base[i] + n)) for i in range(3))
                px[x, top + r] = c
        d.line([(x0, top), (x1 - 1, top)], fill=lerp(base, (10, 10, 14), 0.6))
    for _ in range(8):  # faint polish streaks
        sx = rnd.randint(x0 + 2, x1 - 30)
        sy = rnd.randint(y0 + 1, y1 - 2)
        for i in range(rnd.randint(6, 22)):
            if sx + i < x1:
                px[sx + i, sy] = lerp(px[sx + i, sy], (110, 116, 132), 0.25)

    # ---- brass: 8 flat facets (light from upper left), 1px seam, tiny noise, no long highlight lines
    x0, y0, x1, y1 = REGION["brass"]
    for k in range(8):
        top = y0 + 8 * k
        ang = math.radians(22.5 + 45 * k)
        light = 0.5 + 0.5 * math.cos(ang - math.radians(135))
        base = lerp(PALETTE["brass_lo"], PALETTE["brass_hi"], light)
        for x in range(x0, x1):
            for r in range(8):
                n = rnd.randint(-3, 3)
                c = lerp(base, PALETTE["brass_lo"], 0.55) if r == 0 else base
                px[x, top + r] = tuple(max(0, min(255, c[i] + n)) for i in range(3))

    # ---- tsuba: dark iron plate, bronze inner ring, four clean cut-outs
    x0, y0, x1, y1 = REGION["tsuba"]
    d.rectangle([x0, y0, x1 - 1, y1 - 1], fill=PALETTE["tsuba_dark"])
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2

    def octa(r):
        return [(cx + r * math.cos(math.radians(22.5 + 45 * k)), cy + r * math.sin(math.radians(22.5 + 45 * k))) for k in range(8)]

    d.polygon(octa(30), fill=(70, 68, 76), outline=PALETTE["brass_lo"])
    d.polygon(octa(24), outline=PALETTE["brass"])
    d.polygon(octa(10), fill=PALETTE["brass_lo"], outline=PALETTE["brass"])
    for k in range(4):
        a = math.radians(45 + 90 * k)
        ox, oy = cx + 17 * math.cos(a), cy + 17 * math.sin(a)
        d.polygon([(ox - 4, oy), (ox, oy - 4), (ox + 4, oy), (ox, oy + 4)], fill=PALETTE["cut"])
    d.rectangle([cx - 2, cy - 5, cx + 2, cy + 5], fill=PALETTE["cut"])  # nakago-ana

    # ---- cord: twisted brown
    x0, y0, x1, y1 = REGION["cord"]
    d.rectangle([x0, y0, x1 - 1, y1 - 1], fill=PALETTE["cord"])
    for i in range(-A, A, 5):
        d.line([(x0 + i, y1 - 1), (x0 + i + 28, y0)], fill=PALETTE["cord_dark"])

    img.save(path)
    return img


if __name__ == "__main__":
    paint(sys.argv[1] if len(sys.argv) > 1 else "katana.png")
    print("painted")
