package cn.iocoder.yudao.module.jijian.service.query;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.jijian.controller.admin.query.vo.JijianQueryPageReqVO;
import cn.iocoder.yudao.module.jijian.controller.admin.query.vo.JijianQueryPageRespVO;
import cn.iocoder.yudao.module.jijian.dal.dataobject.Property.PropertyDO;
import cn.iocoder.yudao.module.jijian.dal.dataobject.leasecontract.LeaseContractDO;
import cn.iocoder.yudao.module.jijian.dal.dataobject.lessee.LesseeDO;
import cn.iocoder.yudao.module.jijian.dal.mysql.Property.PropertyMapper;
import cn.iocoder.yudao.module.jijian.dal.mysql.leasecontract.LeaseContractMapper;
import cn.iocoder.yudao.module.jijian.dal.mysql.lessee.LesseeMapper;
import cn.iocoder.yudao.module.jijian.enums.query.JijianQueryTimeRangeEnum;
import cn.iocoder.yudao.module.jijian.service.query.dto.JijianAiQueryIntent;
import cn.iocoder.yudao.module.jijian.service.query.dto.PropertySummaryDTO;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * REAL_ESTATE query service.
 *
 * <p>All data access uses fixed Mapper methods. AI output is never used as SQL,
 * table names, column names, or dynamic conditions.
 */
@Service
@Validated
public class JijianPropertyQueryServiceImpl implements JijianPropertyQueryService {

    private static final String UNKNOWN = "未填写";
    private static final int EXPIRING_SOON_DAYS = 30;

    @Resource
    private PropertyMapper propertyMapper;
    @Resource
    private LesseeMapper lesseeMapper;
    @Resource
    private LeaseContractMapper leaseContractMapper;

    @Override
    public JijianQueryPageRespVO queryPage(JijianQueryPageReqVO req) {
        LocalDateTime startTime = resolveStartTime(req.getTimeRange());
        PageResult<PropertyDO> doPage = propertyMapper.selectPageForQuery(req, startTime);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (PropertyDO d : doPage.getList()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("propertyName", d.getPropertyName());
            row.put("propertyAddress", d.getPropertyAddress());
            row.put("ownershipInfo", d.getOwnershipInfo());
            row.put("area", d.getArea());
            row.put("leaseStatus", d.getLeaseStatus());
            row.put("buildingTime", d.getBuildingTime());
            rows.add(row);
        }

        List<JijianQueryPageRespVO.ColumnDef> columns = Arrays.asList(
                JijianQueryPageRespVO.ColumnDef.of("propertyName", "房产名称", "text"),
                JijianQueryPageRespVO.ColumnDef.of("propertyAddress", "房产地址", "text"),
                JijianQueryPageRespVO.ColumnDef.of("ownershipInfo", "产权信息", "text"),
                JijianQueryPageRespVO.ColumnDef.of("area", "建筑面积㎡", "number"),
                JijianQueryPageRespVO.ColumnDef.of("leaseStatus", "租赁情况", "text"),
                JijianQueryPageRespVO.ColumnDef.of("buildingTime", "建筑时间", "date")
        );

        JijianQueryPageRespVO resp = new JijianQueryPageRespVO();
        resp.setPageResult(new PageResult<>(rows, doPage.getTotal()));
        resp.setSummary(buildSummary(startTime));
        resp.setColumns(columns);
        return resp;
    }

    @Override
    public PropertySummaryDTO querySummary(JijianAiQueryIntent intent) {
        return buildSummary(resolveStartTime(intent.getTimeRange()));
    }

    @Override
    public Map<String, Object> queryMatrix(JijianAiQueryIntent intent) {
        PropertySummaryDTO summary = querySummary(intent);
        Map<String, Object> matrix = new LinkedHashMap<>();
        matrix.put("formType", "REAL_ESTATE");
        matrix.put("timeRange", intent.getTimeRange());
        matrix.put("propertyTotalCount", summary.getPropertyTotalCount());
        matrix.put("totalCount", summary.getTotalCount());
        matrix.put("totalArea", summary.getTotalArea());
        matrix.put("byLeaseStatus", summary.getByLeaseStatus());
        matrix.put("byOwnershipInfo", summary.getByOwnershipInfo());
        matrix.put("byBuildingYear", summary.getByBuildingYear());
        matrix.put("tenantTotalCount", summary.getTenantTotalCount());
        matrix.put("byTenantType", summary.getByTenantType());
        matrix.put("internalTenantCount", summary.getInternalTenantCount());
        matrix.put("externalTenantCount", summary.getExternalTenantCount());
        matrix.put("contactNamePresentCount", summary.getContactNamePresentCount());
        matrix.put("phonePresentCount", summary.getPhonePresentCount());
        matrix.put("idCardPresentCount", summary.getIdCardPresentCount());
        matrix.put("businessLicensePresentCount", summary.getBusinessLicensePresentCount());
        matrix.put("contractTotalCount", summary.getContractTotalCount());
        matrix.put("totalContractAmount", summary.getTotalContractAmount());
        matrix.put("byPaymentStatus", summary.getByPaymentStatus());
        matrix.put("byUtilityManagement", summary.getByUtilityManagement());
        matrix.put("byContractStartYear", summary.getByContractStartYear());
        matrix.put("byContractEndYear", summary.getByContractEndYear());
        matrix.put("expiredContractCount", summary.getExpiredContractCount());
        matrix.put("expiringSoonContractCount", summary.getExpiringSoonContractCount());
        matrix.put("maxAmountContract", summary.getMaxAmountContract());
        matrix.put("minAmountContract", summary.getMinAmountContract());
        matrix.put("avgContractAmount", summary.getAvgContractAmount());
        matrix.put("byPropertyRef", summary.getByPropertyRef());
        matrix.put("byTenantRef", summary.getByTenantRef());
        return matrix;
    }

    private PropertySummaryDTO buildSummary(LocalDateTime startTime) {
        List<PropertyDO> properties = propertyMapper.selectListForSummary(startTime);
        List<LesseeDO> tenants = lesseeMapper.selectListForSummary(startTime);
        List<LeaseContractDO> contracts = leaseContractMapper.selectListForSummary(startTime);
        return buildSummary(properties, tenants, contracts);
    }

    private PropertySummaryDTO buildSummary(List<PropertyDO> properties,
                                            List<LesseeDO> tenants,
                                            List<LeaseContractDO> contracts) {
        PropertySummaryDTO dto = new PropertySummaryDTO();
        dto.setPropertyTotalCount(properties.size());
        dto.setTotalCount(properties.size());
        dto.setTotalArea(properties.stream()
                .map(PropertyDO::getArea)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        dto.setByLeaseStatus(groupLeaseStatus(properties));
        dto.setByOwnershipInfo(groupCount(properties, item -> normalize(item.getOwnershipInfo())));
        dto.setByBuildingYear(groupCount(properties, item -> yearOf(item.getBuildingTime())));

        dto.setTenantTotalCount(tenants.size());
        dto.setByTenantType(groupCount(tenants, item -> normalize(item.getEntityType())));
        dto.setInternalTenantCount(tenants.stream().filter(item -> Boolean.TRUE.equals(item.getIsInternalStaff())).count());
        dto.setExternalTenantCount(tenants.stream().filter(item -> !Boolean.TRUE.equals(item.getIsInternalStaff())).count());
        dto.setContactNamePresentCount(tenants.stream().filter(item -> present(item.getContactName())).count());
        dto.setPhonePresentCount(tenants.stream().filter(item -> present(item.getPhone())).count());
        dto.setIdCardPresentCount(tenants.stream().filter(item -> present(item.getIdCard())).count());
        dto.setBusinessLicensePresentCount(tenants.stream().filter(item -> present(item.getBusinessLicense())).count());

        dto.setContractTotalCount(contracts.size());
        dto.setTotalContractAmount(contracts.stream()
                .map(LeaseContractDO::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        dto.setByPaymentStatus(groupCount(contracts, item -> normalize(item.getPaymentStatus())));
        dto.setByUtilityManagement(groupCount(contracts, item -> normalize(item.getWaterElectricityMgmt())));
        dto.setByContractStartYear(groupCount(contracts, item -> yearOf(item.getContractStartTime())));
        dto.setByContractEndYear(groupCount(contracts, item -> yearOf(item.getContractEndTime())));
        dto.setExpiredContractCount(countExpired(contracts));
        dto.setExpiringSoonContractCount(countExpiringSoon(contracts));
        dto.setMaxAmountContract(extremeContract(contracts, true));
        dto.setMinAmountContract(extremeContract(contracts, false));
        dto.setAvgContractAmount(avgAmount(contracts));
        dto.setByPropertyRef(groupCount(contracts, item -> idRef("property:", item.getPropertyId())));
        dto.setByTenantRef(groupCount(contracts, item -> idRef("lessee:", item.getLesseeId())));
        return dto;
    }

    private List<PropertySummaryDTO.LeaseStatusCountDTO> groupLeaseStatus(List<PropertyDO> properties) {
        Map<String, Long> grouped = properties.stream().collect(Collectors.groupingBy(
                item -> normalize(item.getLeaseStatus()),
                LinkedHashMap::new,
                Collectors.counting()));
        List<PropertySummaryDTO.LeaseStatusCountDTO> result = new ArrayList<>();
        grouped.forEach((key, count) -> {
            PropertySummaryDTO.LeaseStatusCountDTO dto = new PropertySummaryDTO.LeaseStatusCountDTO();
            dto.setLeaseStatus(key);
            dto.setCount(count);
            result.add(dto);
        });
        return result;
    }

    private <T> List<PropertySummaryDTO.NameCountDTO> groupCount(List<T> source, Function<T, String> classifier) {
        Map<String, Long> grouped = source.stream().collect(Collectors.groupingBy(
                classifier,
                LinkedHashMap::new,
                Collectors.counting()));
        List<PropertySummaryDTO.NameCountDTO> result = new ArrayList<>();
        grouped.forEach((key, count) -> {
            PropertySummaryDTO.NameCountDTO dto = new PropertySummaryDTO.NameCountDTO();
            dto.setName(key);
            dto.setCount(count);
            result.add(dto);
        });
        return result;
    }

    private long countExpired(List<LeaseContractDO> contracts) {
        LocalDateTime now = LocalDateTime.now();
        return contracts.stream()
                .filter(item -> item.getContractEndTime() != null && item.getContractEndTime().isBefore(now))
                .count();
    }

    private long countExpiringSoon(List<LeaseContractDO> contracts) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime deadline = now.plusDays(EXPIRING_SOON_DAYS);
        return contracts.stream()
                .filter(item -> item.getContractEndTime() != null)
                .filter(item -> !item.getContractEndTime().isBefore(now) && !item.getContractEndTime().isAfter(deadline))
                .count();
    }

    private PropertySummaryDTO.ContractAmountDTO extremeContract(List<LeaseContractDO> contracts, boolean max) {
        return contracts.stream()
                .filter(item -> item.getAmount() != null)
                .sorted(max
                        ? Comparator.comparing(LeaseContractDO::getAmount).reversed()
                        : Comparator.comparing(LeaseContractDO::getAmount))
                .findFirst()
                .map(this::toContractAmount)
                .orElse(null);
    }

    private PropertySummaryDTO.ContractAmountDTO toContractAmount(LeaseContractDO item) {
        PropertySummaryDTO.ContractAmountDTO dto = new PropertySummaryDTO.ContractAmountDTO();
        dto.setPropertyId(item.getPropertyId());
        dto.setLesseeId(item.getLesseeId());
        dto.setAmount(item.getAmount());
        dto.setPaymentStatus(item.getPaymentStatus());
        dto.setWaterElectricityMgmt(item.getWaterElectricityMgmt());
        return dto;
    }

    private BigDecimal avgAmount(List<LeaseContractDO> contracts) {
        List<BigDecimal> amounts = contracts.stream()
                .map(LeaseContractDO::getAmount)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (amounts.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal sum = amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(amounts.size()), 2, RoundingMode.HALF_UP);
    }

    private String normalize(String value) {
        return value == null || value.trim().isEmpty() ? UNKNOWN : value.trim();
    }

    private boolean present(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String yearOf(LocalDateTime time) {
        return time == null ? UNKNOWN : String.valueOf(time.getYear());
    }

    private String idRef(String prefix, Long id) {
        return id == null ? UNKNOWN : prefix + id;
    }

    private LocalDateTime resolveStartTime(String timeRange) {
        JijianQueryTimeRangeEnum e = JijianQueryTimeRangeEnum.of(timeRange);
        if (e == null) {
            e = JijianQueryTimeRangeEnum.ONE_MONTH;
        }
        return e.startDate().atStartOfDay();
    }
}
