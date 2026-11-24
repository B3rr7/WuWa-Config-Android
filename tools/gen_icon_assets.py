#!/usr/bin/env python3
"""Generate every WuWaConfig icon raster from the composed master artwork.

`tools/art/icon_master.png` is the full-bleed 1024px square: the neon squircle
with its gear, `</>`, bolt, stacked cards and `>_` prompt. The render's baked-in
transparency checkerboard is removed and each corner continues the rim colour
found at the same angle, so the container's rounded silhouette survives and no
square shows through the launcher mask. It is the single source for both the
Android adaptive-icon foreground and the web/PWA icons.

Android layout: an adaptive icon is a 108dp canvas whose central 72dp square is
what the launcher mask shows. The master is placed exactly on that square, and
the 18dp bleed is a flat edge-colour fill so a launcher that over-zooms for
parallax still sees field rather than transparency.

Outputs
    app/src/main/res/drawable-{m,h,x,xx,xxx}hdpi/ic_launcher_foreground.png
    site/app_icon.png        1024   README / social embeds
    site/icon-512.png         512   PWA `any` + `maskable`
    site/icon-192.png         192   PWA `any`
    site/icon-96.webp          96   navbar logo mark, manifest shortcuts
    site/apple-touch-icon.png 180   iOS home screen
    site/favicon.png           48   browser tab (prompt close-up)

Run:  python3 tools/gen_icon_assets.py
Requires Pillow + numpy.
"""
from __future__ import annotations

import argparse
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
MASTER = Path(__file__).resolve().parent / "art" / "icon_master.png"

DENSITIES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}
VIEWPORT = 108.0
SAFE = 72.0

# Close-up of the prompt, used for the tab icon where the full composition is
# illegible at 16px. Fractions of the master.
FAVICON_CROP = (0.28, 0.34, 0.84, 0.90)


BG = (0x39, 0x0F, 0x86)  # edge colour; mirrors ic_launcher_background.xml


def adaptive_foreground(master: Image.Image, size: int) -> Image.Image:
    """Master on the 72dp safe square over a flat field.

    The 18dp bleed is never shown by a launcher, so it is a flat fill rather than
    an extension of the artwork: extending it streaked badly at the diagonals and
    bought nothing.
    """
    art_px = round(size * SAFE / VIEWPORT)
    off = (size - art_px) // 2
    canvas = Image.new("RGB", (size, size), BG)
    canvas.paste(master.resize((art_px, art_px), Image.LANCZOS), (off, off))
    return canvas


def main() -> None:
    ap = argparse.ArgumentParser(description="Regenerate icon rasters from the master.")
    ap.add_argument("--master", default=str(MASTER))
    args = ap.parse_args()

    master = Image.open(args.master).convert("RGB")
    if master.size != (1024, 1024):
        master = master.resize((1024, 1024), Image.LANCZOS)

    for bucket, scale in DENSITIES.items():
        size = round(VIEWPORT * scale)
        out = ROOT / f"app/src/main/res/drawable-{bucket}/ic_launcher_foreground.png"
        out.parent.mkdir(parents=True, exist_ok=True)
        adaptive_foreground(master, size).save(out, "PNG", optimize=True)
        print(f"{out.relative_to(ROOT)}  {size}x{size}")

    fav = master.crop(tuple(round(v * master.width) for v in FAVICON_CROP))
    assets = [
        ("site/app_icon.png", 1024, False),
        ("site/icon-512.png", 512, False),
        ("site/icon-192.png", 192, False),
        ("site/icon-96.webp", 96, False),
        ("site/apple-touch-icon.png", 180, False),
        ("site/favicon.png", 48, True),
    ]
    for rel, size, is_fav in assets:
        out = ROOT / rel
        img = (fav if is_fav else master).resize((size, size), Image.LANCZOS)
        if out.suffix == ".webp":
            img.save(out, "WEBP", quality=95, method=6)
        else:
            img.save(out, "PNG", optimize=True)
        print(f"{rel}  {size}x{size}")


if __name__ == "__main__":
    main()
