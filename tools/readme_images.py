#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Frames raw window captures (./summa shot NAME) for the README: trims the caption band, rounds the
corners, adds a soft shadow on a tangerine-to-lilac backdrop. Captures are Summa's own window only.
    python3 tools/readme_images.py RAW.png OUT.png [--band 44] [--width 1760]
"""
import sys
from PIL import Image, ImageDraw, ImageFilter


def frame(raw, out, band=44, width=1760):
    im = Image.open(raw).convert("RGB")
    w, h = im.size
    im = im.crop((0, band, w, h))
    w, h = im.size
    r = 22
    mask = Image.new("L", (w, h), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, w - 1, h - 1), r, fill=255)
    pad = 56
    W, H = w + 2 * pad, h + 2 * pad
    bg = Image.new("RGB", (W, H))
    top, bot = (255, 214, 190), (222, 214, 250)
    for y in range(H):
        t = y / H
        ImageDraw.Draw(bg).line([(0, y), (W, y)], fill=tuple(int(top[i] + (bot[i] - top[i]) * t) for i in range(3)))
    shadow = Image.new("L", (W, H), 0)
    ImageDraw.Draw(shadow).rounded_rectangle((pad, pad + 14, pad + w, pad + h + 14), r, fill=120)
    shadow = shadow.filter(ImageFilter.GaussianBlur(22))
    bg.paste((60, 40, 80), (0, 0), shadow)
    bg.paste(im, (pad, pad), mask)
    scale = width / W
    bg = bg.resize((width, int(H * scale)), Image.LANCZOS)
    bg.save(out, optimize=True)
    print(out, bg.size)


if __name__ == "__main__":
    args = sys.argv[1:]
    band = int(args[args.index("--band") + 1]) if "--band" in args else 44
    width = int(args[args.index("--width") + 1]) if "--width" in args else 1760
    frame(args[0], args[1], band, width)
