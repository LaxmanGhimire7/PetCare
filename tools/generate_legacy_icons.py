"""Render the PetCare paw for launchers older than adaptive icons (API 24-25)."""

from pathlib import Path

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[1] / "app" / "src" / "main" / "res"
SIZES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
GREEN = (46, 107, 94, 255)
WHITE = (255, 255, 255, 255)


def draw_icon(round_icon: bool) -> Image.Image:
    size = 512
    icon = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    canvas = ImageDraw.Draw(icon)
    if round_icon:
        canvas.ellipse((8, 8, size - 8, size - 8), fill=GREEN)
    else:
        canvas.rounded_rectangle((8, 8, size - 8, size - 8), radius=112, fill=GREEN)

    for x, y, rx, ry in (
        (164, 190, 35, 43),
        (226, 145, 33, 42),
        (288, 145, 33, 42),
        (350, 190, 35, 43),
    ):
        canvas.ellipse((x - rx, y - ry, x + rx, y + ry), fill=WHITE)

    # Broad lower pad with a narrow shoulder recalls the existing in-app paw.
    canvas.polygon(
        (
            (256, 220), (218, 223), (193, 246), (171, 279), (144, 314),
            (136, 341), (144, 371), (164, 386), (192, 386), (222, 377),
            (256, 374), (290, 377), (320, 386), (348, 386), (368, 371),
            (376, 341), (368, 314), (341, 279), (319, 246), (294, 223),
        ),
        fill=WHITE,
    )
    return icon


def main() -> None:
    for density, size in SIZES.items():
        directory = ROOT / f"mipmap-{density}"
        for name, rounded in (("ic_launcher", False), ("ic_launcher_round", True)):
            draw_icon(rounded).resize((size, size), Image.Resampling.LANCZOS).save(
                directory / f"{name}.webp", format="WEBP", lossless=True
            )


if __name__ == "__main__":
    main()
