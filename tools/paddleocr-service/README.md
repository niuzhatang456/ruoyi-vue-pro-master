# PaddleOCR 本地服务

纪检系统录入模块的本地 OCR 服务。  
通过 FastAPI 提供 HTTP 接口，Java 后端调用后完成图片/PDF 文字识别。

## 为什么选 PaddleOCR

- 完全离线，不依赖云服务，不上传任何数据到外部
- 中文识别准确率高，支持表格结构识别
- 轻量 FastAPI 服务，本机部署无需额外环境

---

## 环境准备

### Python 版本
推荐 Python **3.9** 或 **3.10**（PaddlePaddle 对 3.12+ 支持有限）

验证版本：
```powershell
python --version
```

### 创建虚拟环境

```powershell
cd D:\VScode\data\ruoyi-vue-pro-master\tools\paddleocr-service

# 创建虚拟环境
python -m venv .venv

# 激活（PowerShell）
.venv\Scripts\Activate.ps1
# 或 CMD：
.venv\Scripts\activate.bat
```

---

## 安装依赖

```powershell
# 激活虚拟环境后执行
pip install -r requirements.txt
```

### 常见安装问题

#### paddlepaddle 安装失败
如果 `paddlepaddle==2.6.2` 安装失败，请手动查找适合你的版本：
```powershell
# 查看可用版本
pip index versions paddlepaddle

# 安装 CPU 版（选择与 Python 版本匹配的）
pip install paddlepaddle==2.6.2 -i https://pypi.tuna.tsinghua.edu.cn/simple
```

如果遇到 `ERROR: Could not find a version that satisfies the requirement`，说明当前 Python 版本不支持，请切换到 Python 3.9 或 3.10。

#### 安装速度慢
使用清华镜像：
```powershell
pip install -r requirements.txt -i https://pypi.tuna.tsinghua.edu.cn/simple
```

---

## 启动服务

```powershell
cd D:\VScode\data\ruoyi-vue-pro-master\tools\paddleocr-service
.venv\Scripts\activate

uvicorn app:app --host 127.0.0.1 --port 8868
```

**首次启动会自动下载 PaddleOCR 中文模型**（约 200MB），请耐心等待。  
下载完成后后续启动会直接使用缓存，速度很快。

启动成功后会看到：
```
INFO:     Uvicorn running on http://127.0.0.1:8868 (Press CTRL+C to quit)
```

---

## 测试

### 健康检查
```powershell
curl http://127.0.0.1:8868/health
```
返回：`{"status":"ok","service":"paddleocr-local","max_pdf_pages":3}`

### 图片识别测试
```powershell
curl -X POST "http://127.0.0.1:8868/ocr" -F "file=@samples/canteen.png"
```

正常返回（示例）：
```json
{
  "success": true,
  "text": "项目名称 规格、等级 单位 价格 采价点 粳米 一级 元/500克 2.88 美一天生活超市",
  "lines": [
    {"text": "项目名称", "score": 0.9987},
    {"text": "规格、等级", "score": 0.9921}
  ],
  "tables": [
    {
      "headers": ["项目名称", "规格、等级", "单位", "价格", "采价点"],
      "rows": [
        ["粳米", "一级", "元/500克", "2.88", "美一天生活超市北苑店"]
      ]
    }
  ],
  "message": ""
}
```

---

## Java 后端配置

在 `yudao-server/src/main/resources/application-local.yaml` 中：

```yaml
jijian:
  ocr:
    enabled: true
    provider: paddleocr
    endpoint: http://127.0.0.1:8868/ocr
    timeout-seconds: 60
    max-pdf-pages: 3
```

---

## 测试样例说明

`samples/` 目录用于放置测试图片。**项目中未自动生成图片**，请按以下说明准备：

### canteen.png（食堂供应价格表）
截图或拍照一张包含以下格式表格的图片：
```
项目名称  规格、等级  单位      价格   采价点
粳米      一级        元/500克  2.88   美一天生活超市北苑店
面粉      五星特精    元/500克  2.38   美一天生活超市北苑店
猪肉      精五花      元/500克  14.50  某肉联厂
```
要求：
- 图片清晰，文字无严重模糊
- 白底黑字优先
- 分辨率建议 1200×600 以上

### property.png（房产信息）
截图包含以下格式：
```
房产地址           房产名称   产权信息  面积    租赁情况  备注
测试路1号101室     测试宿舍A  自有产权  80.5    未出租    测试
某市某区某路2号    办公室B    租赁房产  120.0   已出租    测试
```

---

## 已知问题与解决方案

### numpy 版本冲突（numpy 2.x 与 PaddleOCR 不兼容）
**现象**：OCR 返回 `numpy.core.multiarray failed to import`  
**解决**：
```powershell
pip install "numpy==1.26.4"
# 重启服务
```

### Windows 中文用户名路径问题
**现象**：`Cannot open file C:\Users\中文名\.paddleocr\...`  
**解决**：运行 `gen_samples.py` 前先执行，将模型复制到无中文路径：
```powershell
python gen_samples.py   # 生成测试图片（顺带验证环境）
# 模型会自动从 models/ 目录加载（app.py 已配置）
```

### Java HTTP 422 错误
**现象**：Java 后端调用 PaddleOCR 返回 422  
**原因**：Java 11 HttpClient 默认尝试 HTTP/2 升级，uvicorn 不支持 → 拒绝  
**解决**：已在 `PaddleOcrServiceImpl` 中强制 `HTTP_1_1`（代码层面已修复）

---

## 常见问题排查

### 1. PaddleOCR 服务未启动
**表现**：Java 后端返回 "PaddleOCR 本地服务未启动"  
**解决**：
```powershell
cd tools/paddleocr-service
.venv\Scripts\activate
uvicorn app:app --host 127.0.0.1 --port 8868
```

### 2. paddlepaddle 安装失败
**表现**：`pip install paddlepaddle` 找不到版本  
**解决**：检查 Python 版本（必须 3.8–3.11），或使用镜像源

### 3. 中文识别效果差
**可能原因**：图片模糊、分辨率过低、表格线干扰  
**解决**：
- 提高图片分辨率（建议 ≥ 150 DPI）
- 去除干扰线后重试
- 使用高清截图而非手机拍照

### 4. 返回文字但无法拆表
**表现**：`tables` 为空，`lines` 有内容  
**原因**：OCR 识别到文字但列间距不均，无法自动分组  
**解决**：上传图片后，在前端"结构化 JSON"Tab 手工编辑 headers 和 rows，保存校正后再确认写入

### 5. Java 后端连接超时
**表现**：`Read timed out`  
**解决**：
- 增大超时配置：`jijian.ocr.timeout-seconds: 120`
- 检查 PaddleOCR 服务是否在处理大图（首次推理较慢）

### 6. Windows PowerShell 执行策略限制
**表现**：`.venv\Scripts\Activate.ps1` 被阻止  
**解决**：
```powershell
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
```
