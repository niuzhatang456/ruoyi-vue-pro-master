package cn.iocoder.yudao.module.jijian.service.ocr;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.jijian.framework.JijianProperties;
import cn.iocoder.yudao.module.jijian.service.ocr.dto.PaddleOcrResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.io.ByteArrayOutputStream;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.jijian.enums.ErrorCodeConstants.*;

/**
 * PaddleOCR 本地服务实现
 * <p>
 * 使用 Java 11 内置 {@link HttpClient}（无需额外 Maven 依赖）
 * 通过 multipart/form-data 调用本机 FastAPI 服务：http://127.0.0.1:8868/ocr
 * <p>
 * 启动服务：
 * <pre>
 *   cd tools/paddleocr-service
 *   .venv\Scripts\activate
 *   uvicorn app:app --host 127.0.0.1 --port 8868
 * </pre>
 */
@Service
public class PaddleOcrServiceImpl implements OcrService {

    private static final Logger log = LoggerFactory.getLogger(PaddleOcrServiceImpl.class);

    @Resource
    private JijianProperties jijianProperties;

    private HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() {
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .version(java.net.http.HttpClient.Version.HTTP_1_1)  // 强制 HTTP/1.1，uvicorn 不支持 HTTP/2
                .build();
    }

    @Override
    public OcrResult recognize(byte[] fileBytes, String fileName) {
        JijianProperties.Ocr ocrCfg = jijianProperties.getOcr();
        String endpoint     = ocrCfg.getEndpoint();
        int    timeoutSecs  = ocrCfg.getTimeoutSeconds();

        if (!isHealthy(ocrCfg.getHealthEndpoint())) {
            throw exception(OCR_SERVICE_NOT_STARTED);
        }

        try {
            // 构造 multipart/form-data 请求体
            String boundary = "jijianocr" + UUID.randomUUID().toString().replace("-", "");
            byte[] body = buildMultipart(boundary, fileBytes, fileName);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(timeoutSecs))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() != 200) {
                log.warn("[PaddleOCR] HTTP {} from {}", response.statusCode(), endpoint);
                throw exception(OCR_SERVICE_ERROR);
            }

            PaddleOcrResponse resp = objectMapper.readValue(response.body(), PaddleOcrResponse.class);
            return convertToOcrResult(resp);

        } catch (ConnectException e) {
            throw exception(OCR_SERVICE_NOT_STARTED);
        } catch (java.net.http.HttpTimeoutException e) {
            throw exception(OCR_SERVICE_TIMEOUT);
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            if (e.getCause() instanceof ConnectException) {
                throw exception(OCR_SERVICE_NOT_STARTED);
            }
            log.error("[PaddleOCR] request failed to {}", endpoint, e);
            throw exception(OCR_SERVICE_ERROR);
        }
    }

    private boolean isHealthy(String healthEndpoint) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(healthEndpoint))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    // ── multipart 构造 ────────────────────────────────────────────────────────

    private byte[] buildMultipart(String boundary, byte[] fileBytes, String fileName) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String sep = "--" + boundary + "\r\n";
        // file 字段
        out.write(sep.getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" +
                sanitizeFileName(fileName) + "\"\r\n").getBytes(StandardCharsets.UTF_8));
        out.write("Content-Type: application/octet-stream\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        out.write(fileBytes);
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
        // 结束边界
        out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    /** 去除文件名中可能破坏 Content-Disposition 头的字符 */
    private String sanitizeFileName(String name) {
        return (name == null ? "upload" : name).replaceAll("[\"\\\\]", "_");
    }

    // ── 结果转换 ──────────────────────────────────────────────────────────────

    private OcrResult convertToOcrResult(PaddleOcrResponse resp) {
        if (!resp.isSuccess()) {
            throw exception(OCR_RECOGNITION_FAILED);
        }

        String fullText = StrUtil.blankToDefault(resp.getText(), "");
        if (StrUtil.isBlank(fullText)) {
            throw exception(OCR_RECOGNITION_FAILED);
        }

        List<String> lines = new ArrayList<>();
        if (resp.getLines() != null) {
            lines = resp.getLines().stream()
                    .map(PaddleOcrResponse.LineItem::getText)
                    .filter(StrUtil::isNotBlank)
                    .collect(Collectors.toList());
        }

        List<Map<String, Object>> tables = new ArrayList<>();
        if (resp.getTables() != null) {
            for (PaddleOcrResponse.TableItem tbl : resp.getTables()) {
                if (tbl.getHeaders() == null || tbl.getHeaders().isEmpty()) continue;
                Map<String, Object> t = new LinkedHashMap<>();
                t.put("headers", tbl.getHeaders());
                t.put("rows", tbl.getRows() != null ? tbl.getRows() : new ArrayList<>());
                t.put("rawRows", tbl.getRawRows() != null ? tbl.getRawRows() : new ArrayList<>());
                tables.add(t);
            }
        }

        return OcrResult.builder()
                .success(true)
                .fullText(fullText)
                .lines(lines)
                .tables(tables)
                .errorMessage("")
                .notice(StrUtil.blankToDefault(resp.getMessage(), ""))
                .build();
    }

}
