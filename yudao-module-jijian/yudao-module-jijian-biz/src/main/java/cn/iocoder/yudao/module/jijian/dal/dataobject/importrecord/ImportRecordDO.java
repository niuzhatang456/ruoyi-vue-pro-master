package cn.iocoder.yudao.module.jijian.dal.dataobject.importrecord;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Jijian import record.
 */
@TableName("jijian_import_record")
@KeySequence("jijian_import_record_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportRecordDO extends TenantBaseDO {

    @TableId
    private Long id;

    private String fileName;

    private String sourceType;

    private String detectedFormType;

    private String status;

    @TableField("created_at")
    private java.time.LocalDateTime createdAt;

}
