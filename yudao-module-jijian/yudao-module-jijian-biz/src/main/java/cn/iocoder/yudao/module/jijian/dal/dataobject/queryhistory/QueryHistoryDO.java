package cn.iocoder.yudao.module.jijian.dal.dataobject.queryhistory;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("jijian_query_history")
@KeySequence("jijian_query_history_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class QueryHistoryDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Long userId;
    private String userName;
    private String question;
    private String answer;
    private String queryType;
    private String formType;
    private String querySql;
    private String queryResultJson;
    private String databaseContextMetaJson;
    private String modelName;
    private Boolean success;
    private String errorMessage;
    private String remark;
}
