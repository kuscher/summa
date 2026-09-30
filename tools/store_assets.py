#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Builds the Google Play graphics in store-submission/graphics/ from Summa's own renders.

    python3 tools/store_assets.py RENDERS_DIR

RENDERS_DIR holds the raw images made on the Googlebook (see store-submission/README.md):
  phone-1.png … phone-6.png     ./summa render phone|list 1080 1920 420 …   (9:16)
  large-1.png … large-3.png     ./summa render large|focus 1920 1080 240 …  (16:9)
  mini-pinned.png               ./summa shot mini-pinned (the real mini window, pinned)
  calc-card.png                 ./summa shot calc-card  (the Calculate card)

Writes: icon-512.png (full square; Play rounds the corners), feature-graphic.png (1024×500),
phone/*.png and large-screen/*.png (24-bit PNG, no alpha), plus the README images for the repo.
"""
import pathlib
import subprocess
import sys

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT = ROOT / "store-submission/graphics"
FONT = ROOT / "app/src/main/res/font/summa_sans.ttf"
TOP, BOTTOM = (255, 214, 190), (222, 214, 250)      # the README backdrop: tangerine to lilac
INK = (47, 46, 51)


def font(size, weight=700, rond=100):
    f = ImageFont.truetype(str(FONT), size)
    f.set_variation_by_axes([min(144, max(8, size * 0.75)), weight, rond])
    return f


def backdrop(w, h):
    bg = Image.new("RGB", (w, h))
    d = ImageDraw.Draw(bg)
    for y in range(h):
        t = y / h
        d.line([(0, y), (w, y)], fill=tuple(int(TOP[i] + (BOTTOM[i] - TOP[i]) * t) for i in range(3)))
    return bg


def window(im, radius, shadow=40):
    """A capture with rounded corners and a soft shadow, as an RGBA layer (image + margin)."""
    w, h = im.size
    pad = shadow
    layer = Image.new("RGBA", (w + 2 * pad, h + 2 * pad), (0, 0, 0, 0))
    sh = Image.new("L", layer.size, 0)
    ImageDraw.Draw(sh).rounded_rectangle((pad, pad + shadow // 3, pad + w, pad + h + shadow // 3), radius, fill=110)
    sh = sh.filter(ImageFilter.GaussianBlur(shadow / 2))
    layer.paste((50, 35, 70, 255), (0, 0), sh)
    mask = Image.new("L", (w, h), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, w - 1, h - 1), radius, fill=255)
    if im.mode == "RGBA":
        mask = Image.composite(im.split()[3], Image.new("L", (w, h), 0), mask)
    layer.paste(im.convert("RGB"), (pad, pad), mask)
    return layer


def icon():
    """The launcher icon as a full square (Play applies its own mask), from tools/logo.py's SVG."""
    svg = (ROOT / "docs/images/icon.svg").read_text()
    square = svg.replace('clip-path="url(#c)"', "")
    tmp = OUT / "icon-square.svg"
    tmp.write_text(square)
    subprocess.run(["rsvg-convert", "-w", "512", "-h", "512", str(tmp), "-o", str(OUT / "icon-512.png")], check=True)
    tmp.unlink()
    # Play wants a 32-bit PNG (with an alpha channel, even though every pixel is opaque).
    Image.open(OUT / "icon-512.png").convert("RGBA").save(OUT / "icon-512.png")


def feature(renders):
    w, h = 1024, 500
    bg = backdrop(w, h)
    # The round icon and the name on the left, a laptop render on the right.
    ic = Image.open(ROOT / "docs/images/icon.png").convert("RGBA").resize((132, 132), Image.LANCZOS)
    bg.paste(ic, (64, 110), ic)
    d = ImageDraw.Draw(bg)
    d.text((64, 262), "Summa", font=font(76, 820), fill=INK)
    d.text((66, 352), "Type maths the way", font=font(30, 560, 60), fill=(93, 90, 96))
    d.text((66, 390), "you'd say it.", font=font(30, 560, 60), fill=(93, 90, 96))
    # Two phone screens (light and dark) on the right, so the answers show in full.
    for name, x, y in (("phone-1.png", 526, 40), ("phone-5.png", 774, 84)):
        shot = Image.open(renders / name).convert("RGB")
        shot = shot.resize((220, int(220 * shot.height / shot.width)), Image.LANCZOS)
        layer = window(shot, 22, 26)
        bg.paste(layer, (x, y), layer)
    bg.save(OUT / "feature-graphic.png", optimize=True)


def flat(src, dst):
    Image.open(src).convert("RGB").save(dst, optimize=True)


def mini_capture(renders):
    """The real mini window: without the empty system title-bar strip, trimmed below its last line."""
    mini = Image.open(renders / "mini-pinned.png").convert("RGBA")
    return mini.crop((0, 44, mini.width, 360))


def mini_on_top(renders):
    """A laptop render with the real (pinned) mini window floating over it, bottom-right."""
    base = Image.open(renders / "large-3.png").convert("RGB")
    mini = mini_capture(renders)
    scale = 1.5 / 1.125                                       # render density / Googlebook density
    mini = mini.resize((int(mini.width * scale), int(mini.height * scale)), Image.LANCZOS)
    layer = window(mini, 20, 40)
    base.paste(layer, (base.width - layer.width - 20, base.height - layer.height - 10), layer)
    return base


def readme(renders):
    """Extra README images: a phone, the pinned mini window, the Calculate card."""
    docs = ROOT / "docs/images"
    for name, src, width in (("phone.png", renders / "phone-1.png", 520),):
        im = Image.open(src).convert("RGB")
        im = im.resize((width, int(width * im.height / im.width)), Image.LANCZOS)
        out = backdrop(im.width + 120, im.height + 120)
        layer = window(im, 36, 30)
        out.paste(layer, (60 - 30, 60 - 30), layer)
        out.save(docs / name, optimize=True)
    mini = mini_capture(renders)
    out = backdrop(mini.width + 140, mini.height + 140)
    layer = window(mini, 20, 36)
    out.paste(layer, (70 - 36, 70 - 36), layer)
    out.save(docs / "mini.png", optimize=True)
    card = Image.open(renders / "calc-card.png").convert("RGBA")
    out = backdrop(card.width + 140, card.height + 140)
    layer = window(card, 26, 36)
    out.paste(layer, (70 - 36, 70 - 36), layer)
    out.save(docs / "calculate.png", optimize=True)


def main():
    renders = pathlib.Path(sys.argv[1])
    for sub in ("phone", "large-screen"):
        (OUT / sub).mkdir(parents=True, exist_ok=True)
    icon()
    feature(renders)
    names = ["munich-weekend", "units-and-time", "buying-a-flat", "your-own-words", "dark", "sheets"]
    for i, n in enumerate(names, 1):
        flat(renders / f"phone-{i}.png", OUT / "phone" / f"{i:02d}-{n}.png")
    for i, n in enumerate(["with-sheet-list", "dark", "units-dates-time-zones"], 1):
        flat(renders / f"large-{i}.png", OUT / "large-screen" / f"{i:02d}-{n}.png")
    mini_on_top(renders).save(OUT / "large-screen" / "04-mini-calculator-on-top.png", optimize=True)
    readme(renders)
    for p in sorted(OUT.rglob("*.png")):
        im = Image.open(p)
        print(p.relative_to(ROOT), im.size, im.mode, f"{p.stat().st_size // 1024} KB")


if __name__ == "__main__":
    main()
