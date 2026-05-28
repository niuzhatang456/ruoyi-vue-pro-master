from pathlib import Path
from zipfile import ZipFile
import xml.etree.ElementTree as ET

PPTX = Path(r"D:\VScode\data\ruoyi-vue-pro-master\outputs\ppt_text_edit\上海音乐学院_working.pptx")
EMU = 914400
NS = {
    "p": "http://schemas.openxmlformats.org/presentationml/2006/main",
    "a": "http://schemas.openxmlformats.org/drawingml/2006/main",
    "r": "http://schemas.openxmlformats.org/officeDocument/2006/relationships",
    "rel": "http://schemas.openxmlformats.org/package/2006/relationships",
}


def ordered_slide_paths(zf):
    pres = ET.fromstring(zf.read("ppt/presentation.xml"))
    rels = ET.fromstring(zf.read("ppt/_rels/presentation.xml.rels"))
    relmap = {rel.attrib["Id"]: rel.attrib["Target"] for rel in rels}
    out = []
    for slide_id in pres.findall(".//p:sldIdLst/p:sldId", NS):
        rid = slide_id.attrib[f"{{{NS['r']}}}id"]
        target = relmap[rid]
        out.append("ppt/" + target if not target.startswith("ppt/") else target)
    return out


def get_text(sp):
    paras = []
    for p in sp.findall(".//p:txBody/a:p", NS):
        paras.append("".join(t.text or "" for t in p.findall(".//a:t", NS)))
    return "\n".join(p for p in paras if p.strip()).strip()


def geom(sp):
    off = sp.find(".//p:spPr/a:xfrm/a:off", NS)
    ext = sp.find(".//p:spPr/a:xfrm/a:ext", NS)
    if off is None or ext is None:
        return None
    return tuple(round(int(v) / EMU, 2) for v in (off.attrib["x"], off.attrib["y"], ext.attrib["cx"], ext.attrib["cy"]))


with ZipFile(PPTX) as zf:
    for i, path in enumerate(ordered_slide_paths(zf)[:28], start=1):
        root = ET.fromstring(zf.read(path))
        rows = []
        for idx, sp in enumerate(root.findall(".//p:sp", NS), start=1):
            text = get_text(sp)
            if not text:
                continue
            name_el = sp.find("./p:nvSpPr/p:cNvPr", NS)
            name = name_el.attrib.get("name", "") if name_el is not None else ""
            g = geom(sp)
            rows.append((idx, name, g, text.replace("\n", " ")[:80]))
        print(f"\nSLIDE {i}")
        for row in rows:
            print(row)
