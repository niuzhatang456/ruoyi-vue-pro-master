package cn.iocoder.yudao.module.jijian.service.ocr;

/**
 * OCR 识别服务接口
 * <p>
 * 当前实现：{@link PaddleOcrServiceImpl}（调用本地 PaddleOCR HTTP 服务）
 * 接入本地服务地址：http://127.0.0.1:8868/ocr
 */
public interface OcrService {

    /**
     * 对文件字节进行 OCR 识别。
     *
     * @param fileBytes 文件字节数组（JPG / PNG / PDF）
     * @param fileName  原始文件名，用于判断文件类型
     * @return OCR 识别结果；success=false 时 errorMessage 包含可读错误
     */
    OcrResult recognize(byte[] fileBytes, String fileName);
}
