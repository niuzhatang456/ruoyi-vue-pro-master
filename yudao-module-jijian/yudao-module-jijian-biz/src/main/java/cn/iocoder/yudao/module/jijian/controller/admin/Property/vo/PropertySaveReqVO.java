package cn.iocoder.yudao.module.jijian.controller.admin.Property.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import java.util.*;
import javax.validation.constraints.*;
import java.math.BigDecimal;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - 房产情况新增/修改 Request VO")
@Data
public class PropertySaveReqVO {

    @Schema(description = "ä¸»é”®ç¼–å·", requiredMode = Schema.RequiredMode.REQUIRED, example = "21686")
    private Long id;

    @Schema(description = "æˆ¿äº§åœ°å€", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "æˆ¿äº§åœ°å€不能为空")
    private String propertyAddress;

    @Schema(description = "æˆ¿äº§åç§°", requiredMode = Schema.RequiredMode.REQUIRED, example = "芋艿")
    @NotEmpty(message = "æˆ¿äº§åç§°不能为空")
    private String propertyName;

    @Schema(description = "äº§æƒä¿¡æ¯", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "äº§æƒä¿¡æ¯不能为空")
    private String ownershipInfo;

    @Schema(description = "å»ºç­‘æ—¶é—´")
    private LocalDateTime buildingTime;

    @Schema(description = "å»ºç­‘é¢ç§¯ï¼ˆå¹³æ–¹ç±³ï¼‰")
    private BigDecimal area;

    @Schema(description = "ç§Ÿèµæƒ…å†µ", example = "1")
    private String leaseStatus;

    @Schema(description = "å¤‡æ³¨", example = "你猜")
    private String remark;

}