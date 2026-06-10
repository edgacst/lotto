from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
SRC = Path(
    r"C:\Users\USER\.cursor\projects\c-Users-USER-Desktop\assets"
    r"\c__Users_USER_AppData_Roaming_Cursor_User_workspaceStorage_empty-window_images_image_2d7c4f99-98e9fd9d-ed52-4db5-953d-b2278c8f5103.png"
)
RES = ROOT / "app" / "src" / "main" / "res"
PLAY = ROOT / "play-store"

MIPMAP_SIZES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}

FOREGROUND_SIZES = {
    "drawable-mdpi": 108,
    "drawable-hdpi": 162,
    "drawable-xhdpi": 216,
    "drawable-xxhdpi": 324,
    "drawable-xxxhdpi": 432,
}


def square_crop(image: Image.Image) -> Image.Image:
    width, height = image.size
    side = min(width, height)
    left = (width - side) // 2
    top = (height - side) // 2
    return image.crop((left, top, left + side, top + side))


def resize(image: Image.Image, size: int) -> Image.Image:
    return image.resize((size, size), Image.Resampling.LANCZOS)


def average_corner_color(image: Image.Image) -> tuple[int, int, int]:
    pixels = image.load()
    side = image.size[0]
    points = [
        pixels[2, 2][:3],
        pixels[side - 3, 2][:3],
        pixels[2, side - 3][:3],
        pixels[side - 3, side - 3][:3],
    ]
    return tuple(sum(channel) // len(points) for channel in zip(*points))


def foreground_layer(image: Image.Image, canvas_size: int, scale: float = 0.82) -> Image.Image:
    layer = Image.new("RGBA", (canvas_size, canvas_size), (0, 0, 0, 0))
    target = int(canvas_size * scale)
    icon = resize(image, target)
    offset = (canvas_size - target) // 2
    layer.paste(icon, (offset, offset), icon if icon.mode == "RGBA" else None)
    return layer


def main() -> None:
    source = Image.open(SRC).convert("RGBA")
    square = square_crop(source)
    bg_color = average_corner_color(square)

    for folder, size in MIPMAP_SIZES.items():
        out_dir = RES / folder
        out_dir.mkdir(parents=True, exist_ok=True)
        icon = resize(square, size)
        icon.save(out_dir / "ic_launcher.png", optimize=True)
        icon.save(out_dir / "ic_launcher_round.png", optimize=True)

    for folder, size in FOREGROUND_SIZES.items():
        out_dir = RES / folder
        out_dir.mkdir(parents=True, exist_ok=True)
        foreground_layer(square, size).save(out_dir / "ic_launcher_foreground.png", optimize=True)

    PLAY.mkdir(parents=True, exist_ok=True)
    play_icon = resize(square, 512)
    play_icon.save(PLAY / "icon-512.png", optimize=True)

    # Save sampled background color for XML update.
    color_hex = "#%02X%02X%02X" % bg_color
    (PLAY / "background-color.txt").write_text(color_hex, encoding="utf-8")
    print(f"Generated launcher icons. Background color: {color_hex}")


if __name__ == "__main__":
    main()
