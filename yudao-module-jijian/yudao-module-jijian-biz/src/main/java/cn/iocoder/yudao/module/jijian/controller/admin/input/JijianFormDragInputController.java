package cn.iocoder.yudao.module.jijian.controller.admin.input;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.jijian.controller.admin.importrecord.vo.ImportRecordRespVO;
import cn.iocoder.yudao.module.jijian.dal.dataobject.importrecord.ImportRecordDO;
import cn.iocoder.yudao.module.jijian.enums.FormTypeConstants;
import cn.iocoder.yudao.module.jijian.service.importrecord.ImportRecordService;
import cn.iocoder.yudao.module.jijian.service.parseddata.ParsedDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 纪检 9 类表单独立拖拽录入接口。
 * 每个端点服务端硬绑定 formType，不依赖前端传参或文件内容自动识别。
 * 内部复用 ParsedDataService 解析能力，走相同的 upload → parsedData → confirmWrite 流程。
 */
@Tag(name = "Admin - 纪检表单拖拽录入（9类独立接口）")
@RestController
@RequestMapping("/jijian/input")
@Validated
public class JijianFormDragInputController {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String SOURCE_TYPE = "drag";

    @Resource
    private ImportRecordService importRecordService;
    @Resource
    private ParsedDataService parsedDataService;

    // ───── 房产情况表 PROPERTY_INFO ─────
    @PostMapping("/property-info/drag-upload")
    @Operation(summary = "房产情况表 - 拖拽上传")
    public CommonResult<ImportRecordRespVO> uploadPropertyInfo(@RequestParam("file") MultipartFile file) {
        return success(doUpload(file, FormTypeConstants.PROPERTY));
    }

    // ───── 租赁人员表 LESSEE ─────
    @PostMapping("/lessee/drag-upload")
    @Operation(summary = "租赁人员表 - 拖拽上传")
    public CommonResult<ImportRecordRespVO> uploadLessee(@RequestParam("file") MultipartFile file) {
        return success(doUpload(file, FormTypeConstants.LESSEE));
    }

    // ───── 租赁合同表 LEASE_CONTRACT ─────
    @PostMapping("/lease-contract/drag-upload")
    @Operation(summary = "租赁合同表 - 拖拽上传")
    public CommonResult<ImportRecordRespVO> uploadLeaseContract(@RequestParam("file") MultipartFile file) {
        return success(doUpload(file, FormTypeConstants.LEASE_CONTRACT));
    }

    // ───── 考勤日报表 ATTENDANCE_DAILY ─────
    @PostMapping("/attendance-daily/drag-upload")
    @Operation(summary = "考勤日报表 - 拖拽上传")
    public CommonResult<ImportRecordRespVO> uploadAttendanceDaily(@RequestParam("file") MultipartFile file) {
        return success(doUpload(file, FormTypeConstants.ATTENDANCE));
    }

    // ───── 疗休养请假表 RECUPERATION_LEAVE ─────
    @PostMapping("/recuperation-leave/drag-upload")
    @Operation(summary = "疗休养请假表 - 拖拽上传")
    public CommonResult<ImportRecordRespVO> uploadRecuperationLeave(@RequestParam("file") MultipartFile file) {
        return success(doUpload(file, FormTypeConstants.LEAVE_HEALTH));
    }

    // ───── 事假表 PERSONAL_LEAVE ─────
    @PostMapping("/personal-leave/drag-upload")
    @Operation(summary = "事假表 - 拖拽上传")
    public CommonResult<ImportRecordRespVO> uploadPersonalLeave(@RequestParam("file") MultipartFile file) {
        return success(doUpload(file, FormTypeConstants.LEAVE_PERSONAL));
    }

    // ───── 出差表 BUSINESS_TRIP ─────
    @PostMapping("/business-trip/drag-upload")
    @Operation(summary = "出差表 - 拖拽上传")
    public CommonResult<ImportRecordRespVO> uploadBusinessTrip(@RequestParam("file") MultipartFile file) {
        return success(doUpload(file, FormTypeConstants.BUSINESS_TRIP));
    }

    // ───── 调休表 COMPENSATORY_LEAVE ─────
    @PostMapping("/compensatory-leave/drag-upload")
    @Operation(summary = "调休表 - 拖拽上传")
    public CommonResult<ImportRecordRespVO> uploadCompensatoryLeave(@RequestParam("file") MultipartFile file) {
        return success(doUpload(file, FormTypeConstants.COMPENSATORY));
    }

    // ───── 食堂供应商信息表 CANTEEN_SUPPLIER ─────
    @PostMapping("/canteen-supplier/drag-upload")
    @Operation(summary = "食堂供应商信息表 - 拖拽上传")
    public CommonResult<ImportRecordRespVO> uploadCanteenSupplier(@RequestParam("file") MultipartFile file) {
        return success(doUpload(file, FormTypeConstants.CANTEEN));
    }

    // ───── 公共解析逻辑（服务端硬绑定 formType） ─────
    private ImportRecordRespVO doUpload(MultipartFile file, String forcedFormType) {
        ImportRecordDO record = importRecordService.createImportRecord(
                file.getOriginalFilename(), SOURCE_TYPE);
        parsedDataService.parseAndCreateWithFormType(record, file, forcedFormType);
        return convert(record);
    }

    private ImportRecordRespVO convert(ImportRecordDO r) {
        ImportRecordRespVO vo = new ImportRecordRespVO();
        vo.setId(r.getId());
        vo.setFileName(r.getFileName());
        vo.setSourceType(r.getSourceType());
        vo.setDetectedFormType(r.getDetectedFormType());
        vo.setStatus(r.getStatus());
        LocalDateTime t = r.getCreatedAt() != null ? r.getCreatedAt() : r.getCreateTime();
        vo.setCreatedAt(t == null ? null : FMT.format(t));
        return vo;
    }
}
