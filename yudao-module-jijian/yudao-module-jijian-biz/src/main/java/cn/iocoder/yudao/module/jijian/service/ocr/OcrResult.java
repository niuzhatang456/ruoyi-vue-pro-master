package cn.iocoder.yudao.module.jijian.service.ocr;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * OCR 识别结果（内部 DTO，已完成文本整合）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OcrResult {

    /** 识别是否成功 */
    private boolean success;

    /** OCR 识别出的完整文本（所有行拼接） */
    private String fullText;

    /** 每行文字列表（按从上到下、从左到右排序） */
    private List<String> lines;

    /**
     * 解析出的表格结构列表。
     * 每个 Map 包含：
     *   "headers" → List&lt;String&gt;
     *   "rows"    → List&lt;List&lt;String&gt;&gt;
     */
    private List<Map<String, Object>> tables;

    /** 识别失败时的错误信息（可直接展示给用户） */
    private String errorMessage;

    /** 附加说明（如"PDF 共 5 页仅识别前 3 页"） */
    private String notice;
}
