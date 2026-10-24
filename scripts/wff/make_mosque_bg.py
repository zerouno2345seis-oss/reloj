#!/usr/bin/env python3
"""Builds watchface-row's "photo" background from zaher's photo of the
Buenos Aires mosque (design/assets/mosque-buenos-aires.jpg).

The white around the building is taken out by flood fill from the edges, so
the white lattice inside the windows stays; the cut edge is feathered and
de-whitened so no halo shows on the dark sky. The building is darkened and
set on the same dusk gradient as the other backgrounds, at the bottom of the
face, behind the prayer row.
"""
import os

import numpy as np
from PIL import Image, ImageFilter
from scipy import ndimage

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SRC = os.path.join(ROOT, "design/assets/mosque-buenos-aires.jpg")
OUT = os.path.join(ROOT, "watchface-row/src/main/res/drawable-nodpi/bg_photo.webp")
S = 450


def cutout(img):
    rgb = img.convert("RGB")
    a = np.asarray(rgb).astype(np.int16)
    near_white = (a.min(axis=2) > 228)
    labels, _ = ndimage.label(near_white)
    edge = np.unique(np.concatenate([labels[0], labels[-1], labels[:, 0], labels[:, -1]]))
    bg = np.isin(labels, edge[edge > 0])
    alpha = Image.fromarray(np.where(bg, 0, 255).astype(np.uint8))
    alpha = alpha.filter(ImageFilter.MinFilter(3)).filter(ImageFilter.GaussianBlur(1.2))
    out = rgb.copy()
    out.putalpha(alpha)
    return out


def sky():
    top, mid, low = np.array([5, 26, 21]), np.array([10, 40, 32]), np.array([46, 34, 16])
    t = np.linspace(0, 1, S)[:, None]
    col = np.where(t < 0.6, top + (mid - top) * (t / 0.6), mid + (low - mid) * ((t - 0.6) / 0.4))
    arr = np.repeat(col[:, None, :], S, axis=1).astype(np.uint8)
    return Image.fromarray(arr).convert("RGBA")


def main():
    building = cutout(Image.open(SRC))
    # Smaller and higher, so nearly all of it shows inside the round glass.
    width = 380
    building = building.resize((width, round(building.height * width / building.width)), Image.LANCZOS)
    # Darkened and a little desaturated, so the time and the prayer row read over it.
    rgb = np.asarray(building.convert("RGB")).astype(np.float32)
    grey = rgb.mean(axis=2, keepdims=True)
    rgb = (rgb * 0.7 + grey * 0.3) * 0.42
    toned = Image.fromarray(rgb.clip(0, 255).astype(np.uint8))
    # See-through, so the dusk shows through the stone.
    toned.putalpha(building.getchannel("A").point(lambda v: int(v * 0.75)))
    face = sky()
    face.alpha_composite(toned, ((S - width) // 2, S - toned.height - 38))
    face.convert("RGB").save(OUT, "WEBP", quality=82, method=6)
    print(f"wrote {OUT} ({os.path.getsize(OUT) // 1024} KB)")


if __name__ == "__main__":
    main()
