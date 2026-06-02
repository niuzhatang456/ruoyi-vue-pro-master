# PaddleOCR 本地服务配置说明

## 1. 为什么采用 PaddleOCR 本地服务

- **完全离线**：不调用百度、阿里、腾讯等任何云 OCR API，文件数据不离开本机
- **中文识别质量优**：PaddleOCR 在中文文档识别准确率高于 Tesseract
- **自定义表格解析**：Python 服务可对识别结果做行列分组，返回结构化 headers/rows
- **独立部署**：Python 服务与 Java 后端完全解耦，通过 HTTP 调用，互不影响

---

## 2. Windows Python 环境准备

推荐 Python **3.9** 或 **3.10**（PaddlePaddle CPU 版对 3.11+ 支持有限）

```powershell
# 验证 Python 版本
python --version

# 如需安装，从 https://www.python.org/downloads/windows/ 下载
# 安装时勾选"Add Python to PATH"
```

---

## 3. 创建虚拟环境

```powershell
cd D:\VScode\data\ruoyi-vue-pro-master\tools\paddleocr-service

# 创建虚拟环境
python -m venv .venv

# 激活（PowerShell）
.venv\Scripts\Activate.ps1

# 如果 Activate.ps1 被系统策略阻止，先执行：
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
```

---

## 4. 安装依赖

```powershell
# 确保虚拟环境已激活
pip install -r requirements.txt

# 如果安装慢，使用清华镜像
pip install -r requirements.txt -i https://pypi.tuna.tsinghua.edu.cn/simple
```

**首次安装约需 5–15 分钟**（paddlepaddle + paddleocr 合计约 400MB）

---

## 5. 启动服务

```powershell
cd D:\VScode\data\ruoyi-vue-pro-master\tools\paddleocr-service
.venv\Scripts\activate

uvicorn app:app --host 127.0.0.1 --port 8868
```

**首次启动会自动下载 PaddleOCR 中文识别模型**（约 200MB），需要网络连接，等待约 2–5 分钟。

下载完成后，后续启动无需下载，通常 5–10 秒内就绪。

**验证服务正常**：
```powershell
curl http://127.0.0.1:8868/health
# 返回：{"status":"ok","service":"paddleocr-local","max_pdf_pages":3}
```

---

## 6. 生成测试样例图片

```powershell
cd D:\VScode\data\ruoyi-vue-pro-master\tools\paddleocr-service
python gen_samples.py
```

执行后生成：
- `samples/canteen.png`（食堂供应价格表）
- `samples/property.png`（房产信息表）

---

## 7. curl 测试

```powershell
# 测试食堂供应图片
curl -X POST "http://127.0.0.1:8868/ocr" -F "file=@samples/canteen.png"

# 成功时返回（示意）
{
  "success": true,
  "text": "项目名称 规格、等级 单位 价格 采价点 ...",
  "lines": [...],
  "tables": [{"headers": ["项目名称","规格、等级","单位","价格","采价点"], "rows": [...]}],
  "message": ""
}
```

---

## 8. Java 后端配置（application-local.yaml）

```yaml
jijian:
  ocr:
    enabled: true                         # 改为 true 后图片/PDF 才会调用 OCR
    provider: paddleocr
    endpoint: http://127.0.0.1:8868/ocr  # PaddleOCR 本地服务地址（固定本机）
    timeout-seconds: 60                  # 超时秒数（大图可增大到 120）
    max-pdf-pages: 3                     # PDF 最多处理页数
```

修改后需要重启 Java 后端。

---

## 9. 图片清晰度要求

| 要求 | 说明 |
|------|------|
| 分辨率 | 建议 ≥ 150 DPI，截图优于手机拍照 |
| 文字大小 | 正文字号 ≥ 10pt |
| 背景 | 白底黑字识别最佳，避免复杂背景 |
| 表格线 | 有表格线比无表格线识别效果更好 |
| 角度 | 尽量保持水平，歪斜超过 15° 会降低准确率 |
| 文件大小 | 单张图片建议 < 10MB；PDF ≤ 3 页 |

---

## 10. PDF 限制

- 当前最多处理前 **3 页**（通过 `max-pdf-pages` 配置）
- 仅支持扫描件 PDF（图片型）；纯文字 PDF 可尝试但效果不稳定
- PDF 较大（>5MB/页）时识别速度较慢，可通过 `timeout-seconds` 增大超时

---

## 11. 常见错误排查

### PaddleOCR 服务未启动
**现象**：前端显示"PaddleOCR 本地服务未启动"  
**原因**：tools/paddleocr-service 未运行  
**解决**：
```powershell
cd D:\VScode\data\ruoyi-vue-pro-master\tools\paddleocr-service
.venv\Scripts\activate
uvicorn app:app --host 127.0.0.1 --port 8868
```

---

### paddlepaddle 安装失败
**现象**：`pip install paddlepaddle` 找不到版本  
**原因**：Python 版本不兼容（3.12+ 不支持 PaddlePaddle 2.6）  
**解决**：切换到 Python 3.9 或 3.10

```powershell
# 查找适合版本
pip index versions paddlepaddle

# 使用镜像
pip install paddlepaddle==2.6.2 -i https://pypi.tuna.tsinghua.edu.cn/simple
```

---

### 中文识别效果差
**现象**：OCR 能识别到文字，但字符有误或乱码  
**解决**：
1. 提高图片分辨率（截图 > 手机拍照）
2. 避免字体过小（字号 < 8pt 识别不稳定）
3. 避免图片压缩（使用 PNG 而非高压缩率 JPG）

---

### 返回文字但无法拆表
**现象**：`tables` 为空，`lines` 有内容  
**原因**：列间距不均匀，无法自动分组  
**解决**：上传图片后，在前端"结构化 JSON"标签页手工编辑 headers 和 rows，
点击"保存校正"后再"确认写入"

---

### Java 后端连接超时
**现象**：`PaddleOCR 服务超时（60s）`  
**解决**：
```yaml
jijian:
  ocr:
    timeout-seconds: 120
```

---

### Windows PowerShell 执行策略限制
**现象**：`.venv\Scripts\Activate.ps1` 报"在此系统上禁止运行脚本"  
**解决**：
```powershell
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
```
