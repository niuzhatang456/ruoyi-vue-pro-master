# ============================================================
# 纪检系统本地一键启动脚本（PowerShell）
# 用途：先启动 PaddleOCR 本地服务，再提示启动后端
# 用法：在 PowerShell 中执行  .\start-jijian.ps1
# ============================================================

$PROJECT_ROOT = $PSScriptRoot
$OCR_DIR      = Join-Path $PROJECT_ROOT "tools\paddleocr-service"
$OCR_PORT     = 8868
$OCR_CMD      = ".venv\Scripts\uvicorn.exe"
$OCR_ARGS     = "app:app --host 127.0.0.1 --port $OCR_PORT"

Write-Host "============================================" -ForegroundColor Cyan
Write-Host " 纪检系统本地启动脚本" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan

# ── 1. 检查 PaddleOCR 是否已在运行 ──────────────────────────────────────────
function Test-OcrHealth {
    try {
        $resp = Invoke-WebRequest -Uri "http://127.0.0.1:$OCR_PORT/health" `
                                  -TimeoutSec 3 -UseBasicParsing -ErrorAction Stop
        return $resp.StatusCode -eq 200
    } catch {
        return $false
    }
}

if (Test-OcrHealth) {
    Write-Host "[OCR] PaddleOCR 服务已在运行（端口 $OCR_PORT）✓" -ForegroundColor Green
} else {
    Write-Host "[OCR] 启动 PaddleOCR 本地服务..." -ForegroundColor Yellow

    if (-not (Test-Path $OCR_DIR)) {
        Write-Host "[OCR] 错误：目录不存在 $OCR_DIR" -ForegroundColor Red
        exit 1
    }

    $uvicornPath = Join-Path $OCR_DIR ".venv\Scripts\uvicorn.exe"
    if (-not (Test-Path $uvicornPath)) {
        Write-Host "[OCR] 错误：未找到 $uvicornPath" -ForegroundColor Red
        Write-Host "[OCR] 请先执行：cd $OCR_DIR && python -m venv .venv && .venv\Scripts\pip install -r requirements.txt" -ForegroundColor Yellow
        exit 1
    }

    # 在后台启动 uvicorn
    $proc = Start-Process -FilePath $uvicornPath `
                          -ArgumentList "app:app --host 127.0.0.1 --port $OCR_PORT" `
                          -WorkingDirectory $OCR_DIR `
                          -WindowStyle Minimized `
                          -PassThru
    Write-Host "[OCR] PaddleOCR 进程已启动（PID=$($proc.Id)），等待就绪..." -ForegroundColor Yellow

    $ready = $false
    for ($i = 1; $i -le 20; $i++) {
        Start-Sleep -Seconds 2
        if (Test-OcrHealth) {
            $ready = $true
            break
        }
        Write-Host "[OCR]   等待中... ($($i*2)s)" -ForegroundColor Gray
    }

    if ($ready) {
        Write-Host "[OCR] PaddleOCR 服务就绪 ✓" -ForegroundColor Green
    } else {
        Write-Host "[OCR] 警告：PaddleOCR 服务未在 40s 内就绪，可能仍在下载模型，请稍后检查。" -ForegroundColor Yellow
    }
}

# ── 2. 提示启动 Java 后端 ────────────────────────────────────────────────────
Write-Host ""
Write-Host "[后端] 现在请在 IDEA 中启动 YudaoServerApplication" -ForegroundColor Cyan
Write-Host "[后端] 或执行：cd $PROJECT_ROOT\yudao-server && mvn spring-boot:run" -ForegroundColor Cyan
Write-Host ""
Write-Host "[提示] 前端开发服务：cd $PROJECT_ROOT\yudao-ui\yudao-ui-admin-vue3 && npm run dev" -ForegroundColor Cyan
Write-Host ""
Write-Host "============================================" -ForegroundColor Cyan
Write-Host " 启动完成，请查看各服务日志" -ForegroundColor Green
Write-Host "============================================" -ForegroundColor Cyan
