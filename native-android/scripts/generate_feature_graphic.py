from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

OUT = Path(__file__).resolve().parents[1] / "play-store" / "feature-graphic.png"
W, H = 1024, 500

img = Image.new("RGB", (W, H), "#1B2F8F")
draw = ImageDraw.Draw(img)

draw.rectangle((0, 0, W, H), fill="#1B2F8F")
draw.ellipse((680, -80, 1120, 360), fill="#334CC4")
draw.ellipse((-120, 220, 280, 620), fill="#243CB0")

title = "십이지신이 추천하는"
subtitle = "행운의 로또번호"
caption = "띠 운세 · QR 당첨조회 · 공식 통계"

font_paths = [
    Path("C:/Windows/Fonts/malgun.ttf"),
    Path("C:/Windows/Fonts/malgunbd.ttf"),
]
font_title = font_sub = font_cap = ImageFont.load_default()
for path in font_paths:
    if path.exists():
        font_title = ImageFont.truetype(str(path), 54)
        font_sub = ImageFont.truetype(str(path), 72)
        font_cap = ImageFont.truetype(str(path), 30)
        break

draw.text((56, 120), title, fill="#F4CD82", font=font_title)
draw.text((56, 190), subtitle, fill="#FFFFFF", font=font_sub)
draw.text((56, 310), caption, fill="#D8E2FF", font=font_cap)

balls = [(760, 150, "#F5C84B", "6"), (860, 110, "#FF7886", "12"), (930, 190, "#69A9FF", "18")]
for x, y, color, num in balls:
    draw.ellipse((x, y, x + 72, y + 72), fill=color)
    draw.text((x + 22, y + 18), num, fill="#151514", font=font_cap)

OUT.parent.mkdir(parents=True, exist_ok=True)
img.save(OUT, optimize=True)
print(f"Saved {OUT}")
