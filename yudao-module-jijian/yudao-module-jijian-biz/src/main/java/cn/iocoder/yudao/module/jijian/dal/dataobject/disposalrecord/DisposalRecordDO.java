package cn.iocoder.yudao.module.jijian.dal.dataobject.disposalrecord;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("jijian_disposal_record")
@KeySequence("jijian_disposal_record_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class DisposalRecordDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Long queryHistoryId;
    private String queryQuestion;
    private String queryAnswer;
    private String queryResultJson;
    private String disposalOpinion;
    private Long disposalUserId;
    private String disposalUserName;
    private LocalDateTime disposalTime;
    private String sourceModule;
    private String remark;
}
