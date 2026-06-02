package cn.iocoder.yudao.module.jijian.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

/**
 * 纪检模块错误码
 * 统一放置于 api 模块，biz / controller / service 均通过 api 依赖引用。
 * 使用 1-050-xxx-xxx 段。
 */
public interface ErrorCodeConstants {

    // ========== 房产情况 1-050-001-xxx ==========
    ErrorCode PROPERTY_NOT_EXISTS = new ErrorCode(1_050_001_000, "房产情况不存在");

    // ========== 解析记录 1-050-002-xxx ==========
    ErrorCode PARSED_DATA_NOT_EXISTS              = new ErrorCode(1_050_002_000, "解析记录不存在");
    ErrorCode PARSED_DATA_ALREADY_CONFIRMED       = new ErrorCode(1_050_002_001, "该解析记录已确认写入，请勿重复操作");
    ErrorCode PARSED_DATA_CANNOT_CONFIRM          = new ErrorCode(1_050_002_002, "解析记录状态不允许确认（仅解析成功的记录可确认）");
    ErrorCode PARSED_DATA_FORM_TYPE_NOT_PROPERTY  = new ErrorCode(1_050_002_003, "该解析记录类型不是房产信息，无法写入房产表");
    ErrorCode PARSED_DATA_PROPERTY_FIELDS_MISSING = new ErrorCode(1_050_002_004, "解析数据中缺少必要的房产字段：房产地址、房产名称、产权信息均不能为空");
    ErrorCode PARSED_DATA_FORM_TYPE_NOT_SUPPORTED = new ErrorCode(1_050_002_005, "当前业务类型暂不支持确认写入，请检查识别类型是否正确");
    ErrorCode PARSED_DATA_ROWS_EMPTY              = new ErrorCode(1_050_002_006, "解析数据中未找到有效数据行，请检查文件内容");
    ErrorCode PARSED_DATA_REQUIRED_FIELD_MISSING  = new ErrorCode(1_050_002_007, "数据行缺少必填字段，请校正后重新提交");
    ErrorCode PARSED_DATA_FORM_TYPE_UNRECOGNIZED  = new ErrorCode(1_050_002_008, "无法识别文件业务类型，请确认文件包含房产/租赁/合同/考勤/疗休养/事假/出差/调休/食堂等关键字");
    ErrorCode PARSED_DATA_FILE_FORMAT_UNSUPPORTED = new ErrorCode(1_050_002_009, "不支持的文件格式，请上传 Excel(.xls/.xlsx)、CSV 或图片(JPG/PNG)/PDF");
    ErrorCode PARSED_DATA_HEADERS_MISSING         = new ErrorCode(1_050_002_010, "文件第一行表头全为空，请检查文件格式");

    // ========== OCR 服务 1-050-003-xxx ==========
    ErrorCode OCR_SERVICE_NOT_ENABLED = new ErrorCode(1_050_003_000, "OCR 识别服务未启用，请改用 Excel 或 CSV 上传，或联系管理员配置本地 OCR 服务");
    ErrorCode OCR_SERVICE_NOT_STARTED = new ErrorCode(1_050_003_001, "PaddleOCR 本地服务未启动，请先执行：cd tools/paddleocr-service && .venv\\Scripts\\activate && uvicorn app:app --host 127.0.0.1 --port 8868");
    ErrorCode OCR_SERVICE_TIMEOUT     = new ErrorCode(1_050_003_002, "PaddleOCR 服务请求超时，请检查服务是否正常运行或适当增大 jijian.ocr.timeout-seconds");
    ErrorCode OCR_SERVICE_ERROR       = new ErrorCode(1_050_003_003, "PaddleOCR 服务返回异常，请查看服务日志");
    ErrorCode OCR_RECOGNITION_FAILED  = new ErrorCode(1_050_003_004, "OCR 识别失败，未识别到有效文字，请检查图片清晰度");
    ErrorCode OCR_NO_TABLE_DETECTED   = new ErrorCode(1_050_003_005, "已识别到文字，但未匹配到支持的业务表头，请检查图片中的表头是否清晰，或改用 Excel 上传");
    ErrorCode OCR_TEXT_NO_HEADERS     = new ErrorCode(1_050_003_006, "已识别到表头，但未识别到有效数据行，请检查图片清晰度或手工补录");

    // ========== 确认写入 1-050-004-xxx ==========
    ErrorCode CONFIRM_WRITE_DB_ERROR   = new ErrorCode(1_050_004_000, "数据库写入失败，请稍后重试或联系管理员");
    ErrorCode CONFIRM_WRITE_CONCURRENT = new ErrorCode(1_050_004_001, "该记录正在被其他操作处理，请稍后重试");

}
