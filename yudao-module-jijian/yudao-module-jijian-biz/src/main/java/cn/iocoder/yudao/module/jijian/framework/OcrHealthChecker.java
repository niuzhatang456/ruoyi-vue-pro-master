package cn.iocoder.yudao.module.jijian.framework;

import cn.iocoder.yudao.module.jijian.framework.JijianProperties.Ocr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 后端启动时检测 PaddleOCR 本地服务可用性，可选自动拉起。
 * <p>
 * 配置示例（application-local.yaml）：
 * <pre>
 * jijian:
 *   ocr:
 *     enabled: true
 *     check-on-startup: true
 *     auto-start: false               # 改为 true 可在本地开发时自动拉起
 *     work-dir: "D:\\...\\tools\\paddleocr-service"
 *     start-command: ".venv\\Scripts\\uvicorn.exe app:app --host 127.0.0.1 --port 8868"
 *     startup-wait-seconds: 15
 * </pre>
 */
@Component
@Order(Integer.MAX_VALUE - 100)   // 在所有 Bean 就绪后执行，不阻塞启动
public class OcrHealthChecker implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(OcrHealthChecker.class);

    @Resource
    private JijianProperties jijianProperties;

    @Override
    public void run(ApplicationArguments args) {
        Ocr cfg = jijianProperties.getOcr();
        if (!cfg.isEnabled()) {
            log.info("[OCR] OCR 服务已禁用（jijian.ocr.enabled=false），跳过健康检测。");
            return;
        }
        if (!cfg.isCheckOnStartup()) {
            return;
        }

        String healthUrl = cfg.getHealthEndpoint();
        log.info("[OCR] 正在检测 PaddleOCR 服务：{}", healthUrl);

        if (isHealthy(healthUrl)) {
            log.info("[OCR] PaddleOCR 服务可用 ✓  endpoint={}", cfg.getEndpoint());
            return;
        }

        log.warn("[OCR] PaddleOCR 服务不可用！图片/PDF 上传将返回错误提示。");

        if (cfg.isAutoStart()) {
            tryAutoStart(cfg);
        } else {
            log.info("[OCR] 手工启动命令：");
            log.info("[OCR]   cd \"{}\"", cfg.getWorkDir());
            log.info("[OCR]   .venv\\Scripts\\Activate.ps1");
            log.info("[OCR]   uvicorn app:app --host 127.0.0.1 --port 8868");
            log.info("[OCR] 或设置 jijian.ocr.auto-start=true 让后端自动拉起。");
        }
    }

    // ── 健康检测 ──────────────────────────────────────────────────────────────

    private boolean isHealthy(String url) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(3))
                    .version(java.net.http.HttpClient.Version.HTTP_1_1)
                    .build();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(3))
                    .GET().build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            return resp.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    // ── 自动拉起 ──────────────────────────────────────────────────────────────

    private void tryAutoStart(Ocr cfg) {
        String workDir = cfg.getWorkDir();
        String cmd    = cfg.getStartCommand();
        File   dir    = new File(workDir);

        if (!dir.exists()) {
            log.warn("[OCR] auto-start 失败：工作目录不存在 {}", workDir);
            return;
        }

        log.info("[OCR] 尝试自动拉起 PaddleOCR 服务…");
        log.info("[OCR]   workDir: {}", workDir);
        log.info("[OCR]   command: {}", cmd);

        try {
            List<String> cmdList;
            String os = System.getProperty("os.name", "").toLowerCase();
            if (os.contains("win")) {
                // Windows: start /b "" <exe> [args...]
                // cmd 整体作为一个字符串传给 start 会导致空格路径解析失败；
                // 必须将命令和参数分开作为独立 ProcessBuilder token。
                List<String> parts = new ArrayList<>(Arrays.asList("cmd", "/c", "start", "/b", ""));
                for (String token : cmd.split("\\s+")) {
                    if (!token.isEmpty()) parts.add(token);
                }
                cmdList = parts;
            } else {
                cmdList = Arrays.asList("/bin/sh", "-c", cmd + " &");
            }

            ProcessBuilder pb = new ProcessBuilder(cmdList);
            pb.directory(dir);
            pb.redirectErrorStream(true);
            pb.start();  // 不 wait，让 OCR 服务在后台启动

            log.info("[OCR] 进程已启动，等待 {} 秒服务就绪…", cfg.getStartupWaitSeconds());
            Thread.sleep((long) cfg.getStartupWaitSeconds() * 1000);

            if (isHealthy(cfg.getHealthEndpoint())) {
                log.info("[OCR] PaddleOCR 服务自动拉起成功 ✓");
            } else {
                log.warn("[OCR] 自动拉起后服务仍不可用，可能需要更长的启动时间或手工检查。");
            }
        } catch (Exception e) {
            log.error("[OCR] 自动拉起 PaddleOCR 服务失败：{}", e.getMessage());
        }
    }
}
