#!/usr/bin/env python3
"""Draws SuperAwesomeMagical Browser's icon: a white globe on the family's violet, with a small
aurora-green sparkle, in the flat style of SuperAwesomeMagicalMusic's icon (same violet, same
full-bleed rounded square).

    python3 tools/icons.py        (needs Pillow)

Writes the SVG (hicolor/scalable) and PNGs at the sizes the Linux icon theme uses into
desktop/linux/icons, the start page's favicon, and the Android app's adaptive icon (its foreground
and monochrome layers; the violet background is android/.../drawable/ic_launcher_background.xml).
"""
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
APP_ID = "io.github._603madison.SuperAwesomeMagicalBrowser"
OUT = ROOT / "desktop/linux/icons"
SIZES = [16, 24, 32, 48, 64, 128, 256, 512]

VIOLET = "#5B3FD1"   # SuperAwesomeMagicalMusic's icon, the brand's light-mode primary
WHITE = "#FFFFFF"
GREEN = "#6EF2B0"    # the aurora green (dark-mode secondary)

# Geometry on a 512 canvas.
CORNER = 44
GLOBE = (240, 276, 150)          # centre x, centre y, radius
STROKE = 26
MERIDIAN_RX = 66                 # the inner ellipse's half-width
LATITUDES = (-0.5, 0.0, 0.5)     # as fractions of the radius from the centre
SPARKLE = (392, 124, 64, 15)     # centre x, centre y, half-length of the points, half-width of the waist


def sparkle_points(cx, cy, r, w):
    """A four-pointed star: points at r on the axes, waist at w on the diagonals."""
    return [(cx, cy - r), (cx + w, cy - w), (cx + r, cy), (cx + w, cy + w),
            (cx, cy + r), (cx - w, cy + w), (cx - r, cy), (cx - w, cy - w)]


def svg() -> str:
    cx, cy, r = GLOBE
    lats = []
    for f in LATITUDES:
        y = cy + f * r
        half = (r * r - (f * r) ** 2) ** 0.5
        lats.append(f'<line x1="{cx - half:.1f}" y1="{y:.1f}" x2="{cx + half:.1f}" y2="{y:.1f}"/>')
    star = " ".join(f"{x:.1f},{y:.1f}" for x, y in sparkle_points(*SPARKLE))
    return f"""<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="0 0 512 512">
  <rect width="512" height="512" rx="{CORNER}" fill="{VIOLET}"/>
  <clipPath id="globe"><circle cx="{cx}" cy="{cy}" r="{r}"/></clipPath>
  <g fill="none" stroke="{WHITE}" stroke-width="{STROKE}" stroke-linecap="round">
    <g clip-path="url(#globe)">
      {"".join(lats)}
      <ellipse cx="{cx}" cy="{cy}" rx="{MERIDIAN_RX}" ry="{r}"/>
    </g>
    <circle cx="{cx}" cy="{cy}" r="{r - STROKE / 2}"/>
  </g>
  <polygon points="{star}" fill="{GREEN}"/>
</svg>
"""


def png(size: int) -> Image.Image:
    """Draws at 8× and scales down, for smooth edges without an SVG renderer."""
    k = 8 * size / 512
    big = int(512 * k)
    im = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle([0, 0, big - 1, big - 1], radius=CORNER * k, fill=VIOLET)

    cx, cy, r = (v * k for v in GLOBE)
    sw = STROKE * k
    # Inside the globe: latitudes and the meridian ellipse, clipped to the circle.
    inner = Image.new("RGBA", im.size, (0, 0, 0, 0))
    di = ImageDraw.Draw(inner)
    for f in LATITUDES:
        y = cy + f * r
        di.line([(cx - r, y), (cx + r, y)], fill=WHITE, width=round(sw))
    mrx = MERIDIAN_RX * k
    di.ellipse([cx - mrx - sw / 2, cy - r - sw / 2, cx + mrx + sw / 2, cy + r + sw / 2], fill=WHITE)
    di.ellipse([cx - mrx + sw / 2, cy - r + sw / 2, cx + mrx - sw / 2, cy + r - sw / 2], fill=(0, 0, 0, 0))
    mask = Image.new("L", im.size, 0)
    ImageDraw.Draw(mask).ellipse([cx - r, cy - r, cx + r, cy + r], fill=255)
    im.alpha_composite(Image.composite(inner, Image.new("RGBA", im.size), mask))
    # The outline ring.
    d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=WHITE, width=round(sw))
    d.polygon([(x * k, y * k) for x, y in sparkle_points(*SPARKLE)], fill=GREEN)
    return im.resize((size, size), Image.LANCZOS)


def android_vector(monochrome: bool) -> str:
    """The globe and sparkle as an Android vector, fitted to the adaptive icon's 66dp safe zone."""
    # The artwork's box on the 512 canvas (globe and sparkle together), mapped onto 21..87dp.
    cx, cy, r = GLOBE
    sx, sy, sr, sw = SPARKLE
    left, top = cx - r, sy - sr
    size = max(sx + sr - left, cy + r - top)
    k = 66 / size
    X = lambda x: 21 + (x - left) * k  # noqa: E731
    Y = lambda y: 21 + (y - top) * k  # noqa: E731
    gx, gy, gr, stroke = X(cx), Y(cy), r * k, STROKE * k
    ring = gr - stroke / 2
    mrx = MERIDIAN_RX * k
    circle = lambda x, y, rx, ry: (f"M{x - rx:.2f},{y:.2f} a{rx:.2f},{ry:.2f} 0 1,0 {2 * rx:.2f},0 "  # noqa: E731
                                   f"a{rx:.2f},{ry:.2f} 0 1,0 {-2 * rx:.2f},0 z")
    lines = "".join(
        f"M{gx - gr:.2f},{gy + f * gr:.2f} L{gx + gr:.2f},{gy + f * gr:.2f} " for f in LATITUDES
    )
    star = "M" + " L".join(f"{X(x):.2f},{Y(y):.2f}" for x, y in sparkle_points(*SPARKLE)) + " z"
    stroke_attrs = (f'android:strokeColor="#FFFFFFFF" android:strokeWidth="{stroke:.2f}" '
                    'android:strokeLineCap="round" android:fillColor="#00000000"')
    green = "#FFFFFFFF" if monochrome else "#FF" + GREEN[1:]
    return f"""<?xml version="1.0" encoding="utf-8"?>
<!-- Drawn by tools/icons.py (the same globe and sparkle as the desktop icon); don't edit by hand. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
    <group>
        <clip-path android:pathData="{circle(gx, gy, gr, gr)}" />
        <path {stroke_attrs} android:pathData="{lines.strip()}" />
        <path {stroke_attrs} android:pathData="{circle(gx, gy, mrx, gr)}" />
    </group>
    <path {stroke_attrs} android:pathData="{circle(gx, gy, ring, ring)}" />
    <path android:fillColor="{green}" android:pathData="{star}" />
</vector>
"""


def main():
    (OUT / "scalable/apps").mkdir(parents=True, exist_ok=True)
    (OUT / f"scalable/apps/{APP_ID}.svg").write_text(svg())
    for s in SIZES:
        d = OUT / f"{s}x{s}/apps"
        d.mkdir(parents=True, exist_ok=True)
        png(s).save(d / f"{APP_ID}.png", optimize=True)
    start = ROOT / "desktop/layer/superawesomemagical/start"
    (start / "icon.svg").write_text(svg())
    res = ROOT / "android/app/src/main/res/drawable"
    if res.is_dir():
        (res / "ic_launcher_foreground.xml").write_text(android_vector(monochrome=False))
        (res / "ic_launcher_monochrome.xml").write_text(android_vector(monochrome=True))
    print("icons written to", OUT)


if __name__ == "__main__":
    main()
