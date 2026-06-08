#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
生成用于 PaddleOCR 测试的样例图片。
依赖：Pillow（已在 requirements.txt 中）

用法：
  python gen_samples.py

输出：
  samples/canteen.png   — 食堂供应价格表图片
  samples/property.png  — 房产信息表图片
"""

from PIL import Image, ImageDraw, ImageFont
import os

SAMPLES_DIR = os.path.join(os.path.dirname(__file__), "samples")
os.makedirs(SAMPLES_DIR, exist_ok=True)

# 尝试使用系统中文字体
FONT_PATHS = [
    "C:/Windows/Fonts/msyh.ttc",    # 微软雅黑
    "C:/Windows/Fonts/simsun.ttc",  # 宋体
    "C:/Windows/Fonts/simhei.ttf",  # 黑体
    "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc",
    "/usr/share/fonts/truetype/arphic/uming.ttc",
]

def get_font(size=22):
    for path in FONT_PATHS:
        try:
            return ImageFont.truetype(path, size=size)
        except Exception:
            continue
    return ImageFont.load_default()

def make_table_image(title: str, headers: list, rows: list, outpath: str):
    """生成简单的白底黑字表格图片"""
    font_h = get_font(24)   # 表头字体
    font_d = get_font(20)   # 数据字体

    COL_PAD = 30
    ROW_H   = 40

    # 估算列宽（每个字约 22px）
    col_widths = []
    for ci, h in enumerate(headers):
        max_w = len(h) * 22 + COL_PAD
        for row in rows:
            if ci < len(row):
                max_w = max(max_w, len(row[ci]) * 20 + COL_PAD)
        col_widths.append(max(max_w, 80))

    total_w = sum(col_widths) + 40
    total_h = ROW_H * (len(rows) + 2) + 60

    img = Image.new("RGB", (total_w, total_h), "white")
    draw = ImageDraw.Draw(img)

    # 标题
    draw.text((20, 10), title, fill="black", font=get_font(26))

    y = 50
    x_start = 20

    def draw_row(y, data, font, is_header=False):
        x = x_start
        for ci, text in enumerate(data):
            if ci >= len(col_widths):
                break
            if is_header:
                draw.rectangle([x, y, x + col_widths[ci], y + ROW_H], fill="#E8E8E8", outline="black")
            else:
                draw.rectangle([x, y, x + col_widths[ci], y + ROW_H], outline="black")
            draw.text((x + 6, y + 8), str(text), fill="black", font=font)
            x += col_widths[ci]

    draw_row(y, headers, font_h, is_header=True)
    y += ROW_H
    for row in rows:
        draw_row(y, row, font_d)
        y += ROW_H

    img.save(outpath, "PNG", dpi=(150, 150))
    print(f"  -> {outpath}")


print("生成测试样例图片...")

# ── 食堂供应 ─────────────────────────────────────────────────────────
make_table_image(
    title="食堂主副食品价格信息表",
    headers=["项目名称", "规格、等级", "单位", "价格", "采价点"],
    rows=[
        ["粳米",   "一级",     "元/500克", "2.88",  "美一天生活超市北苑店"],
        ["面粉",   "五星特精", "元/500克", "2.38",  "美一天生活超市北苑店"],
        ["猪肉",   "精五花",   "元/500克", "14.50", "某肉联厂"],
        ["白菜",   "新鲜",     "元/500克", "0.60",  "某蔬菜批发市场"],
        ["食用油", "5L桶装",   "桶",       "88.00", "某大型超市"],
    ],
    outpath=os.path.join(SAMPLES_DIR, "canteen.png"),
)

# ── 房产信息 ─────────────────────────────────────────────────────────
make_table_image(
    title="房产信息登记表",
    headers=["房产地址", "房产名称", "产权信息", "面积", "租赁情况", "备注"],
    rows=[
        ["测试路1号101室", "测试宿舍A", "单位自有", "80.5",  "未出租", "测试"],
        ["某市某区某路2号", "办公室B",  "租赁房产", "120.0", "已出租", "测试"],
        ["某区某路3号301室","储藏室C",  "单位自有", "30.0",  "未出租", "测试"],
    ],
    outpath=os.path.join(SAMPLES_DIR, "property.png"),
)

print("\n全部样例图片已生成到 samples/ 目录。")
print("测试命令（需先启动服务）：")
print("  curl -X POST http://127.0.0.1:8868/ocr -F file=@samples/canteen.png")
