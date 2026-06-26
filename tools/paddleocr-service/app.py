#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
PaddleOCR 本地 HTTP 服务
供 Java 后端通过 HTTP 调用，完成图片/PDF 文字识别。

启动命令：
  cd tools/paddleocr-service
  .venv\\Scripts\\activate          # Windows
  uvicorn app:app --host 127.0.0.1 --port 8868

接口：POST /ocr   (multipart/form-data, field name = "file")
     GET  /health
"""

import io
import os
import sys
import traceback
from typing import List, Dict, Any

import numpy as np
from fastapi import FastAPI, File, Form, UploadFile
from fastapi.responses import JSONResponse
from PIL import Image

# ── FastAPI 应用 ─────────────────────────────────────────────────────────────
app = FastAPI(title="PaddleOCR Local Service", version="1.1.0")

MAX_PDF_PAGES = 5          # PDF 最多处理页数
ROW_Y_TOLERANCE = 18       # 同行 y 坐标容差（像素），可根据字号调整
HEADER_SCAN_LIMIT = 15

HEADER_ALIASES = [
    {"序号", "编号", "no"},
    {"备注", "说明"},
    {"部门", "所在部门", "单位", "科室"},
    {"申请人", "姓名", "人员", "员工"},
    {"休假地点", "地点", "目的地"},
    {"疗养假开始时间", "休假开始时间", "开始时间", "开始日期"},
    {"疗养假结束时间", "休假结束时间", "结束时间", "结束日期"},
    {"事由", "原因", "请假事由"},
    {"天数", "请假天数", "休假天数"},
    {"商品名称", "品名", "项目名称", "物品名称", "名称"},
    {"规格", "规格型号", "型号", "等级", "规格等级", "规格、等级", "规格/等级"},
    {"单位"},
    {"数量", "重量"},
    {"单价", "价格"},
    {"小计", "金额", "合计金额"},
    {"采价点", "采样点", "采购点", "价格采集点"},
    {"房产地址", "地址"},
    {"房产名称"},
    {"产权信息", "产权"},
    {"面积"},
    {"租赁情况"},
]

HEADER_REJECT_WORDS = {"配送单", "登记表", "信息表", "日期：", "日期:", "发布时间", "信息来源", "访问次数", "公告", "标题"}
SUMMARY_WORDS = {"小计", "合计", "总计"}

# ── 模型路径（显式指定，避免中文用户名路径导致 C++ 读取失败） ──────────────────
_THIS_DIR = os.path.dirname(os.path.abspath(__file__))
_MODELS_DIR = os.path.join(_THIS_DIR, "models")

# 优先使用本地 models/ 目录；若不存在则让 PaddleOCR 自动下载到默认位置
_DET_MODEL = os.path.join(_MODELS_DIR, "det", "ch", "ch_PP-OCRv4_det_infer")
_REC_MODEL = os.path.join(_MODELS_DIR, "rec", "ch", "ch_PP-OCRv4_rec_infer")
_CLS_MODEL = os.path.join(_MODELS_DIR, "cls", "ch_ppocr_mobile_v2.0_cls_infer")

_HAS_LOCAL_MODELS = (os.path.isdir(_DET_MODEL) and
                     os.path.isdir(_REC_MODEL) and
                     os.path.isdir(_CLS_MODEL))

# ── PaddleOCR 懒加载（避免启动时下载/初始化耗时影响 health check） ────────────
_ocr_engine = None


def get_ocr():
    global _ocr_engine
    if _ocr_engine is None:
        try:
            from paddleocr import PaddleOCR
            if _HAS_LOCAL_MODELS:
                # 使用复制到无中文路径的本地模型
                _ocr_engine = PaddleOCR(
                    use_angle_cls=True,
                    lang="ch",
                    show_log=False,
                    use_gpu=False,
                    det_model_dir=_DET_MODEL,
                    rec_model_dir=_REC_MODEL,
                    cls_model_dir=_CLS_MODEL,
                )
            else:
                # 让 PaddleOCR 自动管理模型（可能有中文路径问题）
                _ocr_engine = PaddleOCR(
                    use_angle_cls=True,
                    lang="ch",
                    show_log=False,
                    use_gpu=False,
                )
        except Exception as e:
            raise RuntimeError(f"PaddleOCR 初始化失败：{e}\n"
                               "请确认已安装 paddleocr 和 paddlepaddle，"
                               "参考 README.md 中的安装说明。")
    return _ocr_engine


# ── PyMuPDF（PDF 支持，可选） ─────────────────────────────────────────────────
try:
    import fitz  # PyMuPDF
    HAS_FITZ = True
except ImportError:
    HAS_FITZ = False


# ── 结果构造辅助 ──────────────────────────────────────────────────────────────

def ok(text: str, lines: List[Dict], tables: List[Dict], message: str = "") -> dict:
    return {"success": True, "text": text, "lines": lines, "tables": tables, "message": message}


def err(message: str) -> dict:
    return {"success": False, "text": "", "lines": [], "tables": [], "message": message}


# ── 核心 OCR 函数 ─────────────────────────────────────────────────────────────

def ocr_np_image(img_np: np.ndarray) -> dict:
    """
    对 numpy 图片数组执行 OCR，返回文本行和表格结构。
    """
    ocr = get_ocr()
    raw = ocr.ocr(img_np, cls=True)

    # raw 结构: [[bbox, (text, score)], ...]  或  [None]（空图）
    if not raw or raw[0] is None:
        return err("未识别到有效文字，请检查图片清晰度")

    page = raw[0]

    # 提取带坐标的文字项
    items = []
    for line in page:
        if len(line) < 2:
            continue
        bbox = line[0]                                      # [[x0,y0],[x1,y1],[x2,y2],[x3,y3]]
        txt_info = line[1]                                  # (text, score)
        text  = txt_info[0] if isinstance(txt_info, (list, tuple)) else str(txt_info)
        score = float(txt_info[1]) if isinstance(txt_info, (list, tuple)) and len(txt_info) > 1 else 1.0
        cx = sum(p[0] for p in bbox) / 4
        cy = sum(p[1] for p in bbox) / 4
        items.append({"text": text, "cx": cx, "cy": cy, "score": score})

    if not items:
        return err("OCR 识别结果为空，请检查图片清晰度")

    # 按 y 再按 x 排序
    items.sort(key=lambda x: (round(x["cy"] / ROW_Y_TOLERANCE), x["cx"]))

    lines_out = [{"text": it["text"], "score": round(it["score"], 4)} for it in items]
    full_text = " ".join(it["text"] for it in items)

    tables = parse_table(items)
    return ok(full_text, lines_out, tables)


def ocr_image_bytes(img_bytes: bytes) -> dict:
    try:
        img = Image.open(io.BytesIO(img_bytes)).convert("RGB")
        img_np = np.array(img)
        return ocr_np_image(img_np)
    except Exception as e:
        return err(f"图片解码失败：{e}")


def ocr_pdf_bytes(pdf_bytes: bytes, max_pdf_pages: int = MAX_PDF_PAGES) -> dict:
    if not HAS_FITZ:
        return err("PDF 识别需要 PyMuPDF，请执行：pip install PyMuPDF")

    try:
        doc = fitz.open(stream=pdf_bytes, filetype="pdf")
    except Exception as e:
        return err(f"PDF 打开失败：{e}")

    total = len(doc)
    page_limit = int(max_pdf_pages or 0)
    to_process = total if page_limit <= 0 else min(total, page_limit)
    all_lines, all_tables, all_texts = [], [], []

    for i in range(to_process):
        page = doc[i]
        mat  = fitz.Matrix(2.0, 2.0)   # 2× 缩放提升识别精度
        pix  = page.get_pixmap(matrix=mat)
        img_bytes = pix.tobytes("png")
        result = ocr_image_bytes(img_bytes)
        if result["success"]:
            all_lines.extend(result["lines"])
            all_texts.append(result["text"])
            all_tables.extend(result["tables"])

    doc.close()

    if not all_lines:
        return err("PDF 中未识别到有效文字，请确认文件为扫描件且清晰度足够")

    notice = (f"PDF total {total} pages, recognized all {to_process} pages"
              if to_process >= total else
              f"PDF total {total} pages, recognized first {to_process} pages")
    return ok("\n".join(all_texts), all_lines, all_tables, notice)


# ── 表格解析 ──────────────────────────────────────────────────────────────────

def parse_table(items: List[Dict]) -> List[Dict]:
    """
    将 OCR 行按 y 坐标分组，扫描前 N 行并按字段别名白名单定位可信表头。
    返回 [{"headers": [...], "rows": [[...]]}]
    """
    if not items:
        return []

    # 按行分组
    rows: List[List[Dict]] = []
    cur_row = [items[0]]
    for item in items[1:]:
        mean_y = sum(i["cy"] for i in cur_row) / len(cur_row)
        if abs(item["cy"] - mean_y) <= ROW_Y_TOLERANCE:
            cur_row.append(item)
        else:
            cur_row.sort(key=lambda x: x["cx"])
            rows.append(cur_row)
            cur_row = [item]
    if cur_row:
        cur_row.sort(key=lambda x: x["cx"])
        rows.append(cur_row)

    if not rows:
        return []

    text_rows = [[clean_cell(it["text"]) for it in row] for row in rows]
    header_idx = find_header_index(text_rows)
    if header_idx is None:
        return []

    header_items = rows[header_idx]
    headers = make_unique_headers([clean_cell(it["text"]) for it in header_items])
    header_x = [it["cx"] for it in header_items]
    data_rows = []
    fill_down = {}
    for row in rows[header_idx + 1:]:
        cells = [clean_cell(it["text"]) for it in row]
        if should_skip_data_row(cells):
            continue
        aligned = [""] * len(headers)
        for item in row:
            column = min(range(len(header_x)), key=lambda idx: abs(item["cx"] - header_x[idx]))
            text = clean_cell(item["text"])
            aligned[column] = (aligned[column] + " " + text).strip()
        apply_fill_down(headers, aligned, fill_down)
        if sum(1 for cell in aligned if cell) >= 2:
            data_rows.append(aligned)

    if not data_rows:
        return []
    return [{"headers": headers, "rows": data_rows, "rawRows": text_rows}]


def resolve_table_rows(rows: List[List[str]]) -> List[Dict]:
    """从二维文本行中定位可信表头，并映射有效数据行。"""
    header_idx = find_header_index(rows)
    if header_idx is None:
        return []

    headers = make_unique_headers([clean_cell(cell) for cell in rows[header_idx]])
    col_n   = len(headers)

    data_rows = []
    fill_down = {}
    for row in rows[header_idx + 1:]:
        cells = [clean_cell(cell) for cell in row]
        if should_skip_data_row(cells):
            continue
        # 列数不足时补空，过多时截断
        cells = cells[:col_n] + [""] * max(0, col_n - len(cells))
        apply_fill_down(headers, cells, fill_down)
        if sum(1 for cell in cells if cell) < 2:
            continue
        data_rows.append(cells)

    if not data_rows:
        return []

    return [{"headers": headers, "rows": data_rows}]


def find_header_index(rows: List[List[str]]):
    best_idx, best_score, best_hits = None, -1, 0
    for idx, row in enumerate(rows[:HEADER_SCAN_LIMIT]):
        cells = [clean_cell(cell) for cell in row if clean_cell(cell)]
        if len(cells) < 2:
            continue
        score, hits = score_header(cells)
        if hits >= 2 and (score > best_score or (score == best_score and hits > best_hits)):
            best_idx, best_score, best_hits = idx, score, hits
    return best_idx


def score_header(cells: List[str]):
    normalized = [normalize_cell(cell) for cell in cells]
    matched_groups = set()
    for cell in normalized:
        for idx, aliases in enumerate(HEADER_ALIASES):
            if any(normalize_cell(alias) == cell or normalize_cell(alias) in cell for alias in aliases):
                matched_groups.add(idx)
                break

    score = len(matched_groups) * 10
    joined = " ".join(cells)
    if any(word in joined for word in HEADER_REJECT_WORDS):
        score -= 20
    score -= sum(2 for cell in cells if looks_like_data(cell))
    return score, len(matched_groups)


def should_skip_data_row(cells: List[str]) -> bool:
    non_empty = [cell for cell in cells if cell]
    if not non_empty:
        return True
    joined = "".join(non_empty)
    if len(non_empty) <= 3 and any(word in joined for word in SUMMARY_WORDS):
        return True
    if len(non_empty) == 1 and (
            any(word in joined for word in HEADER_REJECT_WORDS)
            or joined.startswith("日期")):
        return True
    return False


def clean_cell(value: str) -> str:
    return " ".join(str(value or "").strip().split())


def normalize_cell(value: str) -> str:
    return (clean_cell(value).lower().replace(" ", "").replace("，", "").replace(",", "")
            .replace("、", "").replace("/", "").replace("：", "").replace(":", ""))


def looks_like_data(value: str) -> bool:
    import re
    text = clean_cell(value)
    return bool(re.search(r"\d{4}[-年/.]\d{1,2}|\d+(?:\.\d+)?(?:元|号|日|月)?$", text))


def make_unique_headers(headers: List[str]) -> List[str]:
    result, seen = [], {}
    for idx, header in enumerate(headers):
        name = header or f"列{idx + 1}"
        seen[name] = seen.get(name, 0) + 1
        result.append(name if seen[name] == 1 else f"{name}_{seen[name]}")
    return result


def apply_fill_down(headers: List[str], cells: List[str], fill_down: Dict[int, str]):
    for idx, header in enumerate(headers):
        normalized = normalize_cell(header)
        if not any(alias in normalized for alias in ["采价点", "采样点", "采购点", "价格采集点", "部门", "所在部门", "科室"]):
            continue
        if idx < len(cells) and cells[idx]:
            fill_down[idx] = cells[idx]
        elif idx < len(cells) and idx in fill_down:
            cells[idx] = fill_down[idx]


# ── HTTP 端点 ─────────────────────────────────────────────────────────────────

@app.get("/health")
async def health():
    return {"status": "ok", "service": "paddleocr-local", "max_pdf_pages": MAX_PDF_PAGES}


@app.post("/ocr")
async def do_ocr(file: UploadFile = File(...), max_pdf_pages: int = Form(MAX_PDF_PAGES)):
    if file is None:
        return JSONResponse(err("未收到文件"))

    fname = (file.filename or "").lower().strip()
    content = await file.read()

    if not content:
        return JSONResponse(err("文件内容为空"))

    try:
        if fname.endswith(".pdf"):
            result = ocr_pdf_bytes(content, max_pdf_pages)
        elif any(fname.endswith(ext) for ext in [".jpg", ".jpeg", ".png", ".bmp", ".gif", ".webp"]):
            result = ocr_image_bytes(content)
        else:
            return JSONResponse(err(f"不支持的文件类型：{fname}，请上传 JPG/PNG/PDF"))
    except RuntimeError as e:
        # PaddleOCR 初始化失败（依赖未安装）
        return JSONResponse(err(str(e)))
    except Exception as e:
        traceback.print_exc()
        return JSONResponse(err(f"OCR 识别异常：{e}"))

    return JSONResponse(result)


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="127.0.0.1", port=8868, log_level="info")
