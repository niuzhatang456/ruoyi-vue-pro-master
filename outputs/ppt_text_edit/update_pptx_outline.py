from copy import deepcopy
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
import shutil
import xml.etree.ElementTree as ET

SRC = Path(r"D:\VScode\data\ruoyi-vue-pro-master\outputs\ppt_text_edit\上海音乐学院_working.pptx")
OUT = Path(r"D:\VScode\data\ruoyi-vue-pro-master\outputs\ppt_text_edit\上海音乐学院_大纲文字已替换.pptx")

NS = {
    "p": "http://schemas.openxmlformats.org/presentationml/2006/main",
    "a": "http://schemas.openxmlformats.org/drawingml/2006/main",
    "r": "http://schemas.openxmlformats.org/officeDocument/2006/relationships",
    "rel": "http://schemas.openxmlformats.org/package/2006/relationships",
}
for prefix, uri in NS.items():
    if prefix != "rel":
        ET.register_namespace(prefix, uri)

EMU = 914400

OUTLINE = [
    ("视错觉在舞台多媒体设计中的运用", ["以多媒体舞台作品《浮生之境》为例", "姓名：", "专业：", "指导老师：", "学校：", "答辩时间："]),
    ("汇报目录", ["研究背景与意义", "视错觉原理概述", "舞台多媒体设计特征", "视错觉在舞台中的应用方式", "《浮生之境》实践分析", "研究结论与不足"]),
    ("研究背景", ["数字影像、LED屏幕、投影等技术推动舞台形式变化", "舞台由实体布景转向虚实结合的综合空间", "观众对沉浸式、动态化、视觉冲击力的要求提高", "视错觉成为舞台空间塑造的重要方法", "讲述重点：现代舞台不只是“搭景”，而是通过影像、灯光和空间共同创造视觉体验。"]),
    ("研究意义", ["从视觉感知角度理解舞台多媒体设计", "探索视错觉在空间重构中的作用", "分析视错觉对舞台叙事和沉浸体验的提升", "为舞台视觉设计提供新的创作思路"]),
    ("研究方法", ["文献研究法：梳理视错觉理论、舞台多媒体相关研究", "案例分析法：分析经典舞台作品中的视错觉表现方式", "实践研究法：结合原创作品《浮生之境》进行设计验证"]),
    ("什么是视错觉？", ["视觉系统受到生理、心理和环境因素影响", "人眼看到的内容与客观事实产生差异", "本质是视觉感知和大脑认知之间的偏差", "在舞台中可转化为空间、运动和形态的艺术表达"]),
    ("视错觉的形成机制", ["视觉暂留：连续画面形成动态感", "空间感知偏差：平面影像形成纵深感", "经验判断影响：观众根据已有经验理解空间", "图形结构冲突：线条、比例、方向造成误判"]),
    ("三类主要视错觉", ["空间视错觉：拓展舞台纵深与层次", "运动视错觉：制造流动、旋转、漂浮感", "几何视错觉：增强图形张力与结构变化"]),
    ("空间视错觉：重构舞台空间", ["利用透视、投影、光影制造空间纵深", "模糊真实空间与虚拟空间边界", "在有限舞台中创造更大的空间感", "常用于建筑、通道、深景、悬浮空间表现"]),
    ("运动视错觉：制造动态感", ["利用连续图像、灯光变化和节奏切换", "让静态舞台产生运动效果", "可表现流动、旋转、坠落、漂浮等视觉状态", "常与音乐节奏和演员动作配合"]),
    ("几何视错觉：强化视觉张力", ["通过线条、比例、方向和角度造成视觉误判", "打破观众对稳定空间的认知", "增强舞台画面的结构感和冲击力", "适合表现错位、压迫、扭曲等情绪"]),
    ("舞台多媒体的四个特征", ["动态性：影像和灯光随时间变化", "叙事性：影像参与剧情推进", "虚实性：真实布景与虚拟影像融合", "沉浸性：观众进入被影像包围的空间"]),
    ("动态性：让舞台“动起来”", ["影像变化带来空间扩展与收缩", "灯光变化强化节奏与情绪", "动态视觉引导观众注意力", "形成有生命感的舞台空间"]),
    ("叙事性：影像参与讲故事", ["多媒体影像可以完成场景转换", "可表现时间流动、心理变化和情绪氛围", "减少传统换景时间", "增强舞台叙事的连贯性"]),
    ("虚实性：真实与影像的融合", ["实体布景与虚拟影像共同构成空间", "观众难以区分真实结构和虚拟画面", "虚实叠加增强空间层次", "为视错觉产生提供基础"]),
    ("沉浸性：从观看到进入", ["大面积投影和灯光形成包围感", "影像延伸至墙面、地面或观众区域", "观众从“看舞台”变成“进入舞台”", "增强身体在场感和情绪代入"]),
    ("视错觉在舞台中的应用路径", ["感官认知层面：引导观众视觉注意", "艺术表达层面：强化空间、情绪和叙事", "技术实现层面：通过投影、LED、灯光、纱幕实现"]),
    ("实践作品：《浮生之境》", ["主题：无重力空间", "核心概念：打破传统三维空间限制", "表达重点：人与空间的相互塑造关系", "视觉特征：漂浮、旋转、错位、折叠、失重"]),
    ("从现实空间到无重力空间", ["重心不再垂直于地面", "舞步摆脱前后左右的常规动线", "舞台从二维平面转化为多维空间", "观众重新感知身体与空间的关系"]),
    ("为什么使用视错觉？", ["视错觉能够打破常规空间逻辑", "倒置、错位、漂浮等手法契合“失重”主题", "动态错觉表现空间的不稳定状态", "虚实结合强化超现实舞台体验"]),
    ("空间重构：倒置与漂浮", ["倒置房间制造方向感错乱", "漂浮建筑打破现实重力逻辑", "错位墙体形成空间穿插效果", "多层投影增强纵深与立体感"]),
    ("动态变化：旋转与失控", ["通过影像旋转表现无重力环境", "画面运动与演员动作形成呼应", "高潮段落加大空间旋转角度", "视觉节奏推动情绪变化"]),
    ("章节节奏与视觉变化", ["第一幕：博物馆场景，现实铺垫", "第二幕：进入无重力环境", "第三、四幕：空间失控，视觉高潮", "第五幕：逐渐适应，运动变缓", "第六幕：回到现实，空间恢复稳定"]),
    ("技术实现方式", ["使用 UE 构建重力失效的空间效果", "参考 Cinta Vidal 的非重力建筑结构", "通过天幕与纱幕分层制造立体感", "演员动作与投影影像衔接", "打破实体舞台与虚拟影像边界"]),
    ("《浮生之境》的实践效果", ["强化舞台空间层次", "提升观众对场景变化的感知", "增强失重与漂浮的视觉体验", "推动作品叙事和情绪表达", "验证视错觉在舞台多媒体中的可行性"]),
    ("研究结论", ["视错觉可以突破舞台物理空间限制", "视错觉能够增强舞台叙事表达", "视错觉提升观众沉浸体验", "多媒体技术为视错觉应用提供实现条件", "视错觉可作为舞台视觉设计的重要方法"]),
    ("不足与未来展望", ["不足：研究时间有限；对交互式舞台研究不够深入；对实时影像技术探讨不足", "展望：未来可结合实时互动、VR、AR等技术", "拓展视错觉在沉浸式演出中的应用", "推动舞台视觉设计向跨媒介方向发展"]),
    ("感谢聆听", ["恳请各位老师批评指正。"]),
]

NAV = ["研究背景与意义", "视错觉原理概述", "舞台多媒体设计特征", "视错觉应用方式", "《浮生之境》实践分析", "研究结论与不足"]


def q(ns, tag):
    return f"{{{NS[ns]}}}{tag}"


def ordered_slide_paths(zf):
    pres = ET.fromstring(zf.read("ppt/presentation.xml"))
    rels = ET.fromstring(zf.read("ppt/_rels/presentation.xml.rels"))
    relmap = {rel.attrib["Id"]: rel.attrib["Target"] for rel in rels}
    paths = []
    for slide_id in pres.findall(".//p:sldIdLst/p:sldId", NS):
        rid = slide_id.attrib[q("r", "id")]
        target = relmap[rid]
        paths.append("ppt/" + target if not target.startswith("ppt/") else target)
    return paths


def shape_text(sp):
    return "\n".join(
        "".join(t.text or "" for t in p.findall(".//a:t", NS))
        for p in sp.findall(".//p:txBody/a:p", NS)
    ).strip()


def geom(sp):
    off = sp.find(".//p:spPr/a:xfrm/a:off", NS)
    ext = sp.find(".//p:spPr/a:xfrm/a:ext", NS)
    if off is None or ext is None:
        return (0, 0, 0, 0)
    return tuple(int(v) / EMU for v in (off.attrib["x"], off.attrib["y"], ext.attrib["cx"], ext.attrib["cy"]))


def tx_body(sp):
    return sp.find("./p:txBody", NS)


def set_text(sp, paragraphs):
    body = tx_body(sp)
    if body is None:
        return
    old_paras = body.findall("./a:p", NS)
    if old_paras:
        template_p = deepcopy(old_paras[0])
    else:
        template_p = ET.Element(q("a", "p"))
    ppr = template_p.find("./a:pPr", NS)
    r = template_p.find(".//a:r", NS)
    rpr = deepcopy(r.find("./a:rPr", NS)) if r is not None and r.find("./a:rPr", NS) is not None else None
    endpr = deepcopy(template_p.find("./a:endParaRPr", NS)) if template_p.find("./a:endParaRPr", NS) is not None else None
    for child in list(body):
        if child.tag == q("a", "p"):
            body.remove(child)
    if isinstance(paragraphs, str):
        paragraphs = [paragraphs]
    if not paragraphs:
        paragraphs = [""]
    for text in paragraphs:
        p = ET.Element(q("a", "p"))
        if ppr is not None:
            p.append(deepcopy(ppr))
        run = ET.SubElement(p, q("a", "r"))
        if rpr is not None:
            run.append(deepcopy(rpr))
        t = ET.SubElement(run, q("a", "t"))
        t.text = text
        if endpr is not None:
            p.append(deepcopy(endpr))
        body.append(p)


def clear_text(sp):
    set_text(sp, "")


def edit_slide(xml_bytes, idx):
    title, bullets = OUTLINE[idx - 1]
    root = ET.fromstring(xml_bytes)
    shapes = [sp for sp in root.findall(".//p:sp", NS) if tx_body(sp) is not None and shape_text(sp)]
    metas = []
    for sp in shapes:
        x, y, w, h = geom(sp)
        metas.append({"sp": sp, "x": x, "y": y, "w": w, "h": h, "area": w * h, "text": shape_text(sp)})

    assigned = set()
    def mark(sp, text):
        set_text(sp, text)
        assigned.add(id(sp))

    if idx == 1:
        if metas:
            mark(max(metas, key=lambda m: m["area"])["sp"], [title, bullets[0]])
        bottom = sorted([m for m in metas if m["y"] > 4.5], key=lambda m: m["x"])
        for m, text in zip(bottom[:3], ["姓名：\n专业：", "指导老师：\n学校：", "答辩时间："]):
            mark(m["sp"], text.split("\n"))
    elif idx == 2:
        left_title = sorted(metas, key=lambda m: (m["x"], m["y"]))[:2]
        if left_title:
            mark(left_title[0]["sp"], title)
        item_shapes = [m for m in metas if m["text"] in ["选题背景与意义", "研究内容与目的", "研究方法与思路", "难点与创新思路", "论文进度及安排", "论文结论与展望"]]
        for m, text in zip(item_shapes, bullets):
            mark(m["sp"], text)
    else:
        navs = [m for m in metas if 4.5 <= m["x"] <= 12.0 and m["y"] < 1.25 and m["w"] > 0.8]
        for m, text in zip(sorted(navs, key=lambda m: m["x"]), NAV):
            mark(m["sp"], text)
        title_candidates = [m for m in metas if m["x"] < 4.1 and m["y"] < 1.25 and m["w"] > 1.5]
        if not title_candidates:
            title_candidates = [m for m in metas if m["area"] > 2.0]
        title_shape = sorted(title_candidates, key=lambda m: (m["y"], m["x"]))[0]["sp"] if title_candidates else metas[0]["sp"]
        body_candidates = [m for m in metas if id(m["sp"]) != id(title_shape) and m["y"] > 1.2 and m["area"] > 0.25]
        if not body_candidates:
            mark(title_shape, [title, *bullets])
        else:
            mark(title_shape, title)
            # Prefer a broad prose area; otherwise use the largest available object.
            body_shape = max(body_candidates, key=lambda m: (m["area"], m["w"]))
            mark(body_shape["sp"], bullets)

    for m in metas:
        if id(m["sp"]) in assigned:
            continue
        text = m["text"].strip()
        if text.startswith("Page") or text.startswith("页码"):
            mark(m["sp"], f"Page {idx}")
        elif text in NAV:
            # Repeated navigation not consumed above.
            clear_text(m["sp"])
        elif text.isdigit() and len(text) <= 2:
            # Keep small layout numerals such as 01/02 markers.
            continue
        else:
            clear_text(m["sp"])

    return ET.tostring(root, encoding="utf-8", xml_declaration=True)


def trim_presentation(pres_xml, rels_xml, keep_count):
    pres = ET.fromstring(pres_xml)
    rels = ET.fromstring(rels_xml)
    sld_lst = pres.find(".//p:sldIdLst", NS)
    slide_ids = list(sld_lst)
    remove = slide_ids[keep_count:]
    remove_rids = {el.attrib[q("r", "id")] for el in remove}
    for el in remove:
        sld_lst.remove(el)
    for rel in list(rels):
        if rel.attrib.get("Id") in remove_rids:
            rels.remove(rel)
    return (
        ET.tostring(pres, encoding="utf-8", xml_declaration=True),
        ET.tostring(rels, encoding="utf-8", xml_declaration=True),
    )


def main():
    shutil.copyfile(SRC, OUT)
    with ZipFile(SRC, "r") as zin:
        slide_paths = ordered_slide_paths(zin)
        replacements = {path: edit_slide(zin.read(path), i) for i, path in enumerate(slide_paths[:28], start=1)}
        pres_xml, rels_xml = trim_presentation(zin.read("ppt/presentation.xml"), zin.read("ppt/_rels/presentation.xml.rels"), 28)
        with ZipFile(OUT, "w", ZIP_DEFLATED) as zout:
            for item in zin.infolist():
                data = zin.read(item.filename)
                if item.filename in replacements:
                    data = replacements[item.filename]
                elif item.filename == "ppt/presentation.xml":
                    data = pres_xml
                elif item.filename == "ppt/_rels/presentation.xml.rels":
                    data = rels_xml
                zout.writestr(item, data)
    print(OUT)


if __name__ == "__main__":
    main()
