from pathlib import Path
from zipfile import ZipFile
import re
import xml.etree.ElementTree as ET

PPTX = Path(r"D:\VScode\data\ruoyi-vue-pro-master\outputs\ppt_text_edit\上海音乐学院_working.pptx")

NS = {
    "p": "http://schemas.openxmlformats.org/presentationml/2006/main",
    "a": "http://schemas.openxmlformats.org/drawingml/2006/main",
    "r": "http://schemas.openxmlformats.org/officeDocument/2006/relationships",
    "rel": "http://schemas.openxmlformats.org/package/2006/relationships",
}


def ordered_slide_paths(zf: ZipFile):
    pres = ET.fromstring(zf.read("ppt/presentation.xml"))
    rels = ET.fromstring(zf.read("ppt/_rels/presentation.xml.rels"))
    relmap = {rel.attrib["Id"]: rel.attrib["Target"] for rel in rels}
    paths = []
    for slide_id in pres.findall(".//p:sldIdLst/p:sldId", NS):
        rid = slide_id.attrib[f"{{{NS['r']}}}id"]
        target = relmap[rid]
        if target.startswith("/"):
            target = target[1:]
        elif not target.startswith("ppt/"):
            target = "ppt/" + target
        paths.append(target)
    return paths


def shape_texts(slide_xml: bytes):
    root = ET.fromstring(slide_xml)
    texts = []
    for idx, sp in enumerate(root.findall(".//p:sp", NS), start=1):
        c_nv_pr = sp.find("./p:nvSpPr/p:cNvPr", NS)
        name = c_nv_pr.attrib.get("name", "") if c_nv_pr is not None else ""
        paras = []
        for p in sp.findall(".//p:txBody/a:p", NS):
            runs = [t.text or "" for t in p.findall(".//a:t", NS)]
            paras.append("".join(runs))
        text = "\n".join([p for p in paras if p.strip()])
        if text.strip():
            texts.append((idx, name, re.sub(r"\s+", " ", text).strip()))
    return texts


with ZipFile(PPTX) as zf:
    slide_paths = ordered_slide_paths(zf)
    print(f"SLIDE_COUNT={len(slide_paths)}")
    for i, path in enumerate(slide_paths, start=1):
        texts = shape_texts(zf.read(path))
        print(f"\n--- Slide {i} ({path}) text_shapes={len(texts)} ---")
        for shape_idx, name, text in texts[:12]:
            print(f"[{shape_idx}] {name}: {text[:180]}")
