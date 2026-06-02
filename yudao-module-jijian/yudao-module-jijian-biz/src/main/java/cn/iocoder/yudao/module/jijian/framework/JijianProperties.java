package cn.iocoder.yudao.module.jijian.framework;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 纪检模块配置属性
 * 通过 application-local.yaml 中 jijian.* 进行覆盖
 */
@Component
@ConfigurationProperties(prefix = "jijian")
@Data
public class JijianProperties {

    /** OCR 识别相关配置 */
    private Ocr ocr = new Ocr();

    @Data
    public static class Ocr {
        /**
         * 是否启用本地 OCR 识别服务。
         * false（默认）：图片/PDF 上传时返回友好提示，不假装成功。
         * true：通过 provider 指定的服务进行识别。
         */
        private boolean enabled = false;

        /**
         * OCR 服务提供方（当前支持 paddleocr）
         */
        private String provider = "paddleocr";

        /**
         * OCR 识别接口地址
         */
        private String endpoint = "http://127.0.0.1:8868/ocr";

        /**
         * OCR 健康检查地址（用于启动时探测服务可用性）
         */
        private String healthEndpoint = "http://127.0.0.1:8868/health";

        /**
         * OCR 请求超时（秒）
         */
        private int timeoutSeconds = 60;

        /**
         * PDF 最多处理页数
         */
        private int maxPdfPages = 3;

        /**
         * 后端启动时是否检测 OCR 服务可用性并输出日志
         */
        private boolean checkOnStartup = true;

        /**
         * 是否在检测到服务不可用时自动尝试拉起本地 PaddleOCR 服务。
         * 仅限本地开发环境使用，生产环境请保持 false。
         */
        private boolean autoStart = false;

        /**
         * 自动拉起时的工作目录（需绝对路径，含 app.py 和 .venv 的目录）
         */
        private String workDir = "D:\\VScode\\data\\ruoyi-vue-pro-master\\tools\\paddleocr-service";

        /**
         * 自动拉起命令（在 workDir 下执行，多段参数用空格分隔）
         * Windows 示例：.venv\\Scripts\\uvicorn.exe app:app --host 127.0.0.1 --port 8868
         */
        private String startCommand = ".venv\\Scripts\\uvicorn.exe app:app --host 127.0.0.1 --port 8868";

        /**
         * 自动拉起后等待服务就绪的秒数
         */
        private int startupWaitSeconds = 15;

        /** OCR 未配置时对用户展示的提示信息 */
        private String disabledMessage =
                "OCR 识别服务未配置，请改用 Excel 或 CSV 上传；或联系管理员部署本地 PaddleOCR 服务后重试。";
    }
}
