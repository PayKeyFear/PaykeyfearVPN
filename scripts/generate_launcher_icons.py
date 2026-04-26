"""Generates launcher icon mipmaps from design/icon-1024.png.

Outputs:
  - mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher.png      (legacy square)
  - mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher_round.png (legacy round mask)
  - mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher_foreground.png (adaptive fg, 108dp safe area)

Adaptive icon background is a solid color resource (not a PNG) — see
res/values/colors.xml `ic_launcher_background`.
"""
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "design" / "icon-1024.png"
RES = ROOT / "app" / "src" / "main" / "res"

# Standard launcher icon sizes (dp → px at 1× = mdpi).
LEGACY_SIZES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}

# Adaptive icon: full canvas is 108dp, safe zone is the inner 66dp circle.
# Source PNG already has its own bg + padding, so we scale it into the safe
# zone (66/108 ≈ 0.611) and center it on a transparent canvas.
ADAPTIVE_SIZES = {dpi: int(px * 108 / 48) for dpi, px in LEGACY_SIZES.items()}
SAFE_RATIO = 0.66  # leave a small visual margin around the shield


def round_mask(size: int) -> Image.Image:
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, size, size), fill=255)
    return mask


def main() -> None:
    if not SOURCE.is_file():
        raise SystemExit(f"missing source icon: {SOURCE}")
    src = Image.open(SOURCE).convert("RGBA")

    for dpi, px in LEGACY_SIZES.items():
        target_dir = RES / f"mipmap-{dpi}"
        target_dir.mkdir(parents=True, exist_ok=True)

        # Legacy square — direct resize, source has its own corners.
        square = src.resize((px, px), Image.LANCZOS)
        square.save(target_dir / "ic_launcher.png", optimize=True)

        # Legacy round — circular alpha mask.
        round_img = Image.new("RGBA", (px, px), (0, 0, 0, 0))
        round_img.paste(square, (0, 0), mask=round_mask(px))
        round_img.save(target_dir / "ic_launcher_round.png", optimize=True)

        # Adaptive foreground — scale source into the inner safe zone of
        # the 108dp canvas and center it. Background is the canvas itself
        # (drawable resource) so the foreground PNG keeps full transparency
        # outside the inset.
        fg_size = ADAPTIVE_SIZES[dpi]
        inset = int(fg_size * SAFE_RATIO)
        scaled = src.resize((inset, inset), Image.LANCZOS)
        canvas = Image.new("RGBA", (fg_size, fg_size), (0, 0, 0, 0))
        offset = (fg_size - inset) // 2
        canvas.paste(scaled, (offset, offset), scaled)
        canvas.save(target_dir / "ic_launcher_foreground.png", optimize=True)

        print(f"  {dpi:7s}: legacy {px}px, adaptive {fg_size}px (fg inset {inset}px)")

    print("done")


if __name__ == "__main__":
    main()
