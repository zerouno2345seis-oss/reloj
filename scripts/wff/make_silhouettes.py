#!/usr/bin/env python3
"""Three backgrounds drawn here (no photos, nothing to license), in the style of the
faces' own bg_mosques.png: a dark green sky warming toward a gold horizon and a
silhouette: Masjid al-Haram with the clock tower, Masjid an-Nabawi with its green
dome, and the Kaaba. Output: design/assets/bg_haram.png, bg_nabawi.png, bg_kaaba.png."""
import os
from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
S = 900          # drawn at twice the size, then halved for clean edges
SIL = (6, 14, 12)


def sky():
    im = Image.new("RGB", (S, S))
    px = im.load()
    for y in range(S):
        t = y / S
        top, mid, low = (8, 26, 22), (14, 34, 28), (122, 86, 38)
        if t < .62:
            c = [int(top[i] + (mid[i] - top[i]) * t / .62) for i in range(3)]
        else:
            k = (t - .62) / .38
            c = [int(mid[i] + (low[i] - mid[i]) * min(1, k * 1.4)) for i in range(3)]
        for x in range(S):
            px[x, y] = tuple(c)
    return im


def minaret(d, x, base, h, w, fill=SIL, top=True):
    d.rectangle([x - w / 2, base - h, x + w / 2, base], fill=fill)
    for k in (.35, .62, .85):          # balconies
        y = base - h * k
        d.rectangle([x - w * .9, y - 6, x + w * .9, y + 6], fill=fill)
    if top:
        d.polygon([(x - w / 2, base - h), (x + w / 2, base - h), (x, base - h - w * 2.6)], fill=fill)
        d.line([(x, base - h - w * 2.6), (x, base - h - w * 3.4)], fill=fill, width=4)


def dome(d, cx, base, r, fill=SIL):
    d.pieslice([cx - r, base - r * 1.15, cx + r, base + r * 1.15], 180, 360, fill=fill)
    d.line([(cx, base - r * 1.15), (cx, base - r * 1.15 - 30)], fill=fill, width=5)


def haram():
    im = sky(); d = ImageDraw.Draw(im)
    g = 690
    d.rectangle([0, g, S, S], fill=SIL)
    # the clock tower behind, in the middle
    d.rectangle([420, 300, 480, g], fill=SIL); d.rectangle([400, 380, 500, g], fill=SIL)
    d.ellipse([423, 318, 477, 372], outline=(214, 170, 92), width=5)
    d.polygon([(425, 300), (475, 300), (450, 210)], fill=SIL); d.line([(450, 210), (450, 170)], fill=SIL, width=5)
    # the arcades and many minarets
    d.rectangle([60, 600, 840, g], fill=SIL)
    for x in range(90, 830, 60):
        d.pieslice([x - 22, 615, x + 22, 660], 180, 360, fill=(16, 32, 26))
    for x, h in ((120, 230), (230, 260), (330, 250), (570, 250), (670, 260), (780, 230)):
        minaret(d, x, 600, h, 22)
    return im


def nabawi():
    im = sky(); d = ImageDraw.Draw(im)
    g = 690
    d.rectangle([0, g, S, S], fill=SIL)
    d.rectangle([90, 590, 810, g], fill=SIL)
    for x in range(120, 800, 56):
        d.pieslice([x - 20, 604, x + 20, 646], 180, 360, fill=(16, 32, 26))
    # the green dome, a touch of colour
    d.rectangle([380, 520, 520, 600], fill=SIL)
    d.pieslice([370, 400, 530, 640], 180, 360, fill=(30, 92, 58))
    d.line([(450, 400), (450, 352)], fill=(214, 170, 92), width=6)
    d.ellipse([440, 340, 460, 360], outline=(214, 170, 92), width=4)
    dome(d, 270, 590, 60); dome(d, 630, 590, 60)
    for x, h in ((150, 250), (300, 270), (600, 270), (750, 250)):
        minaret(d, x, 590, h, 24)
    return im


def kaaba():
    im = sky(); d = ImageDraw.Draw(im)
    g = 700
    d.rectangle([0, g, S, S], fill=(10, 20, 17))
    # the court, rings of worshippers as soft dots
    for r, n in ((300, 60), (240, 48), (180, 36)):
        for i in range(n):
            import math
            a = i / n * 2 * math.pi
            x, y = 450 + r * math.cos(a), g + 60 + r * .18 * math.sin(a)
            d.ellipse([x - 4, y - 4, x + 4, y + 4], fill=(40, 52, 46))
    # the Kaaba, seen a little from one corner
    front = [(330, 470), (520, 490), (520, 700), (330, 690)]
    side = [(520, 490), (600, 460), (600, 670), (520, 700)]
    top = [(330, 470), (410, 440), (600, 460), (520, 490)]
    d.polygon(front, fill=(8, 10, 10)); d.polygon(side, fill=(4, 6, 6)); d.polygon(top, fill=(18, 20, 20))
    gold = (214, 170, 92)
    d.line([(330, 530), (520, 548)], fill=gold, width=12); d.line([(520, 548), (600, 520)], fill=gold, width=10)
    d.rectangle([450, 600, 490, 690], outline=gold, width=4)          # the door
    return im


if __name__ == "__main__":
    out = os.path.join(ROOT, "design/assets")
    os.makedirs(out, exist_ok=True)
    for name, f in (("haram", haram), ("nabawi", nabawi), ("kaaba", kaaba)):
        f().filter(ImageFilter.GaussianBlur(1)).resize((450, 450), Image.LANCZOS).save(os.path.join(out, f"bg_{name}.png"), optimize=True)
        print("wrote", f"bg_{name}.png")
