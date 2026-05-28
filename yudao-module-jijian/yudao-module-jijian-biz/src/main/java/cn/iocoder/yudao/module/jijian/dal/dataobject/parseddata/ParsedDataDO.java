package cn.iocoder.yudao.module.jijian.dal.dataobject.parseddata;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;

@TableName("jijian_import_parsed_data")
@KeySequence("jijian_import_parsed_data_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParsedDataDO extends TenantBaseDO {

    @TableId
    private Long id;

    private Long importRecordId;

    private String formType;

    private String rawText;

    private String parsedJson;

    private BigDecimal confidence;

    private String status;

    private String errorMsg;

}
