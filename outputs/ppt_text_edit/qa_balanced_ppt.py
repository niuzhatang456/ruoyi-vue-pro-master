from pathlib import Path
from zipfile import ZipFile
import os
import re
import xml.etree.ElementTree as ET

from PIL import Image, ImageDraw

PPTX = Path(r"C:\Users\牛轧糖\Desktop\小楼本科毕设\完稿文件\上海音乐学院.pptx")
PREVIEW = Path(r"D:\VScode\data\ruoyi-vue-pro-master\outputs\ppt_text_edit\preview_balanced")
CONTACT = Path(r"D:\VScode\data\ruoyi-vue-pro-master\outputs\ppt_text_edit\contact_sheet_balanced.jpg")

NS = {
    "p": "http://schemas.openxmlformats.org/presentationml/2006/main",
    "a": "http://schemas.openxmlformats.org/drawingml/2006/main",
    "r": "http://schemas.openxmlformats.org/officeDocument/2006/relationships",
}


def read_texts():
    with ZipFile(PPTX) as zf:
        pres = ET.fromstring(zf.read("ppt/presentation.xml"))
        rels = ET.fromstring(zf.read("ppt/_rels/presentation.xml.rels"))
        relmap = {r.attrib["Id"]: r.attrib["Target"] for r in rels}
        texts = []
        for sid in pres.findall(".//p:sldIdLst/p:sldId", NS):
            rid = sid.attrib[f"{{{NS['r']}}}id"]
            target = relmap[rid]
            path = "ppt/" + target if not target.startswith("ppt/") else target
            root = ET.fromstring(zf.read(path))
            texts.append(" ".join(t.text or "" for t in root.findall(".//a:t", NS)))
        return texts


def make_contact_sheet():
    files = sorted(PREVIEW.glob("*.PNG"), key=lambda p: int(re.search(r"(\d+)", p.stem).group(1)))
    thumbs = []
    for f in files:
        im = Image.open(f).convert("RGB")
        im.thumbnail((256, 144))
        thumbs.append((f, im.copy()))
    cols = 4
    rows = (len(thumbs) + cols - 1) // cols
    sheet = Image.new("RGB", (cols * 280, rows * 182), "white")
    draw = ImageDraw.Draw(sheet)
    for idx, (f, im) in enumerate(thumbs, start=1):
        x = ((idx - 1) % cols) * 280 + 12
        y = ((idx - 1) // cols) * 182 + 24
        sheet.paste(im, (x, y))
        draw.text((x, y - 18), f"{idx:02d}", fill=(0, 0, 0))
    sheet.save(CONTACT, quality=92)
    return files


texts = read_texts()
alltext = "".join(texts).replace(" ", "")
keys = ["视错觉在舞台多媒体设计中的运用", "汇报目录", "实践作品：《浮生之境》", "感谢聆听", "Page28"]
old = ["单击此处", "请输入标题", "点击输入", "答辩演讲通用PPT模板", "Page$i", "研究数据", "欢迎提问", "Mercury"]
files = make_contact_sheet()

print("SLIDE_COUNT=", len(texts))
print("FILE_SIZE=", os.path.getsize(PPTX))
print("PREVIEW_COUNT=", len(files))
print("KEY_PRESENT=", {k: k.replace(" ", "") in alltext for k in keys})
print("OLD_PRESENT=", {k: k.replace(" ", "") in alltext for k in old})
print("SLIDE_3=", texts[2][:220])
print("SLIDE_28=", texts[27][:180])
print("CONTACT=", CONTACT)
