package cn.iocoder.yudao.module.jijian.service.ocr.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * PaddleOCR 本地服务的 HTTP 响应 DTO
 * 对应 tools/paddleocr-service/app.py 的返回格式
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaddleOcrResponse {

    /** 识别是否成功 */
    private boolean success;

    /** 识别出的完整文本（所有行拼接） */
    private String text;

    /** 逐行识别结果 */
    private List<LineItem> lines;

    /** 结构化表格列表（PaddleOCR 自动解析或行列分组） */
    private List<TableItem> tables;

    /** 错误信息（success=false 时有值）或附加说明 */
    private String message;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LineItem {
        private String text;
        private Double score;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TableItem {
        private List<String> headers;
        private List<List<String>> rows;
        private List<List<String>> rawRows;
    }
}
