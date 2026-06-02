package cn.iocoder.yudao.module.jijian.dal.dataobject.lessee;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/** 纪检房屋租赁人员 DO */
@TableName("jijian_lessee")
@KeySequence("jijian_lessee_seq")
@Data @EqualsAndHashCode(callSuper = true) @ToString(callSuper = true)
@Builder @NoArgsConstructor @AllArgsConstructor
public class LesseeDO extends TenantBaseDO {
    @TableId private Long id;
    /** 主体类型：个人 / 组织 */ private String entityType;
    /** 联系人姓名 */           private String contactName;
    /** 手机号 */               private String phone;
    /** 身份证号 */             private String idCard;
    /** 营业执照号 */           private String businessLicense;
    /** 是否单位内部人员 */     private Boolean isInternalStaff;
    /** 备注 */                 private String remark;
    /** 来源解析数据ID */       private Long sourceParsedDataId;
}
