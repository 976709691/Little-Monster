# -*- coding: utf-8 -*-
"""生成应用图标：深色渐变圆角底 + 白色字幕条 x2 + 播放三角。输出到各 mipmap 密度。"""
import os
from PIL import Image, ImageDraw

SIZE = 512
res_dir = r"C:\Users\18985\Doubao\chats\2026-10-13\new-chat-1\同声字幕翻译器\AndroidApp\app\src\main\res"

img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
d = ImageDraw.Draw(img)

# 圆角矩形底（深蓝紫渐变近似：分块绘制）
def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))

top, bottom = (26, 20, 46), (13, 26, 40)
radius = 112
mask = Image.new("L", (SIZE, SIZE), 0)
md = ImageDraw.Draw(mask)
md.rounded_rectangle([8, 8, SIZE - 8, SIZE - 8], radius=radius, fill=255)
base = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
bd = ImageDraw.Draw(base)
bd.rounded_rectangle([8, 8, SIZE - 8, SIZE - 8], radius=radius, fill=top)
img.paste(base, (0, 0), mask)
# 竖向渐变叠加
grad = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
gd = ImageDraw.Draw(grad)
for y in range(SIZE):
    t = y / SIZE
    c = lerp(top, bottom, t) + (255,)
    gd.line([(0, y), (SIZE, y)], fill=c)
img = Image.composite(grad, img, mask)

# 字幕条（两条白色圆角矩形）
bar_w = 336
bar_h = 52
bar_y1 = 138
bar_y2 = 218
bar_x = (SIZE - bar_w) // 2
for y, h, alpha in ((bar_y1, bar_h, 255), (bar_y2, bar_h - 14, 220)):
    d.rounded_rectangle([bar_x, y, bar_x + bar_w, y + h], radius=26, fill=(255, 255, 255, alpha))

# 播放三角（青色）
tri = [(368, 300), (368, 368), (424, 334)]
d.polygon(tri, fill=(64, 224, 208, 255))

# 输出各密度
densities = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
for name, px in densities.items():
    out_dir = os.path.join(res_dir, f"mipmap-{name}")
    os.makedirs(out_dir, exist_ok=True)
    img.resize((px, px), Image.LANCZOS).save(os.path.join(out_dir, "ic_launcher.png"))
print("done:", densities)
