package cn.iocoder.yudao.module.jijian.service.query.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * REAL_ESTATE aggregate summary over property, lessee, and lease contract data.
 *
 * <p>Sensitive lessee fields are represented only as completeness counts. Raw
 * phone, id card, and business license values must never be exposed here.
 */
@Data
public class PropertySummaryDTO {

    private long propertyTotalCount;
    private long totalCount;
    private BigDecimal totalArea = BigDecimal.ZERO;
    private List<LeaseStatusCountDTO> byLeaseStatus = new ArrayList<>();
    private List<NameCountDTO> byOwnershipInfo = new ArrayList<>();
    private List<NameCountDTO> byBuildingYear = new ArrayList<>();

    private long tenantTotalCount;
    private List<NameCountDTO> byTenantType = new ArrayList<>();
    private long internalTenantCount;
    private long externalTenantCount;
    private long contactNamePresentCount;
    private long phonePresentCount;
    private long idCardPresentCount;
    private long businessLicensePresentCount;

    private long contractTotalCount;
    private BigDecimal totalContractAmount = BigDecimal.ZERO;
    private List<NameCountDTO> byPaymentStatus = new ArrayList<>();
    private List<NameCountDTO> byUtilityManagement = new ArrayList<>();
    private List<NameCountDTO> byContractStartYear = new ArrayList<>();
    private List<NameCountDTO> byContractEndYear = new ArrayList<>();
    private long expiredContractCount;
    private long expiringSoonContractCount;
    private ContractAmountDTO maxAmountContract;
    private ContractAmountDTO minAmountContract;
    private BigDecimal avgContractAmount = BigDecimal.ZERO;
    private List<NameCountDTO> byPropertyRef = new ArrayList<>();
    private List<NameCountDTO> byTenantRef = new ArrayList<>();

    @Data
    public static class LeaseStatusCountDTO {
        private String leaseStatus;
        private long count;
    }

    @Data
    public static class NameCountDTO {
        private String name;
        private long count;
    }

    @Data
    public static class ContractAmountDTO {
        private Long propertyId;
        private Long lesseeId;
        private BigDecimal amount = BigDecimal.ZERO;
        private String paymentStatus;
        private String waterElectricityMgmt;
    }
}
