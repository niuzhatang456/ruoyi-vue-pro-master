package cn.iocoder.yudao.module.jijian.service.query;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.jijian.controller.admin.query.vo.JijianQueryPageReqVO;
import cn.iocoder.yudao.module.jijian.controller.admin.query.vo.JijianQueryPageRespVO;
import cn.iocoder.yudao.module.jijian.dal.dataobject.canteensupplier.CanteenSupplierDO;
import cn.iocoder.yudao.module.jijian.dal.mysql.canteensupplier.CanteenSupplierMapper;
import cn.iocoder.yudao.module.jijian.enums.query.JijianQueryTimeRangeEnum;
import cn.iocoder.yudao.module.jijian.service.query.dto.CanteenSupplySummaryDTO;
import cn.iocoder.yudao.module.jijian.service.query.dto.JijianAiQueryIntent;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CanteenSupplyQueryServiceImpl implements CanteenSupplyQueryService {

    private static final String UNKNOWN_PROJECT = "未填写项目名称";
    private static final String UNKNOWN_SPEC = "未填写规格/等级";
    private static final String UNKNOWN_UNIT = "未填写单位";
    private static final String UNKNOWN_PRICE_POINT = "未填写采价点";

    @Resource
    private CanteenSupplierMapper canteenSupplierMapper;

    @Override
    public JijianQueryPageRespVO queryPage(JijianQueryPageReqVO req) {
        LocalDateTime startTime = resolveStartTime(req.getTimeRange());
        PageResult<CanteenSupplierDO> doPage = canteenSupplierMapper.selectPageForQuery(req, startTime);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (CanteenSupplierDO item : doPage.getList()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("itemName", item.getItemName());
            row.put("specLevel", item.getSpecLevel());
            row.put("unit", item.getUnit());
            row.put("price", item.getPrice());
            row.put("purchasePoint", item.getPurchasePoint());
            row.put("createTime", item.getCreateTime());
            rows.add(row);
        }

        JijianQueryPageRespVO resp = new JijianQueryPageRespVO();
        resp.setPageResult(new PageResult<>(rows, doPage.getTotal()));
        resp.setSummary(buildSummary(canteenSupplierMapper.selectListForQuery(startTime)));
        resp.setColumns(Arrays.asList(
                JijianQueryPageRespVO.ColumnDef.of("itemName", "项目名称", "text"),
                JijianQueryPageRespVO.ColumnDef.of("specLevel", "规格/等级", "text"),
                JijianQueryPageRespVO.ColumnDef.of("unit", "单位", "text"),
                JijianQueryPageRespVO.ColumnDef.of("price", "价格", "number"),
                JijianQueryPageRespVO.ColumnDef.of("purchasePoint", "采价点", "text"),
                JijianQueryPageRespVO.ColumnDef.of("createTime", "创建时间", "date")
        ));
        return resp;
    }

    @Override
    public CanteenSupplySummaryDTO querySummary(JijianAiQueryIntent intent) {
        return buildSummary(canteenSupplierMapper.selectListForQuery(resolveStartTime(intent.getTimeRange())));
    }

    @Override
    public Map<String, Object> queryMatrix(JijianAiQueryIntent intent) {
        CanteenSupplySummaryDTO summary = querySummary(intent);
        Map<String, Object> matrix = new LinkedHashMap<>();
        matrix.put("formType", "CANTEEN_SUPPLY");
        matrix.put("timeRange", intent.getTimeRange());
        matrix.put("totalCount", summary.getTotalCount());
        matrix.put("totalProjectCount", summary.getTotalProjectCount());
        matrix.put("priceVarianceCount", summary.getPriceVarianceItems().size());
        matrix.put("byProjectName", summary.getByProjectName());
        matrix.put("byPricePoint", summary.getByPricePoint());
        matrix.put("priceVarianceItems", summary.getPriceVarianceItems());
        matrix.put("maxPriceItems", summary.getMaxPriceItems());
        matrix.put("minPriceItems", summary.getMinPriceItems());
        return matrix;
    }

    private CanteenSupplySummaryDTO buildSummary(List<CanteenSupplierDO> all) {
        CanteenSupplySummaryDTO dto = new CanteenSupplySummaryDTO();
        dto.setTotalCount(all.size());
        dto.setTotalProjectCount(all.stream()
                .map(item -> normalize(item.getItemName(), UNKNOWN_PROJECT))
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .size());
        dto.setByProjectName(groupCount(all, item -> normalize(item.getItemName(), UNKNOWN_PROJECT)));
        dto.setByPricePoint(groupCount(all, item -> normalize(item.getPurchasePoint(), UNKNOWN_PRICE_POINT)));
        dto.setPriceVarianceItems(buildVarianceItems(all));
        dto.setMaxPriceItems(buildExtremumItems(all, true));
        dto.setMinPriceItems(buildExtremumItems(all, false));
        return dto;
    }

    private List<CanteenSupplySummaryDTO.NameCountDTO> groupCount(List<CanteenSupplierDO> all,
                                                                 java.util.function.Function<CanteenSupplierDO, String> classifier) {
        Map<String, Long> grouped = all.stream().collect(Collectors.groupingBy(
                classifier,
                LinkedHashMap::new,
                Collectors.counting()));
        List<CanteenSupplySummaryDTO.NameCountDTO> result = new ArrayList<>();
        grouped.forEach((key, count) -> {
            CanteenSupplySummaryDTO.NameCountDTO dto = new CanteenSupplySummaryDTO.NameCountDTO();
            dto.setName(key);
            dto.setCount(count);
            result.add(dto);
        });
        return result;
    }

    private List<CanteenSupplySummaryDTO.PriceVarianceItemDTO> buildVarianceItems(List<CanteenSupplierDO> all) {
        Map<String, List<CanteenSupplierDO>> grouped = all.stream()
                .filter(item -> item.getPrice() != null)
                .collect(Collectors.groupingBy(this::varianceKey, LinkedHashMap::new, Collectors.toList()));
        List<CanteenSupplySummaryDTO.PriceVarianceItemDTO> result = new ArrayList<>();
        grouped.values().forEach(items -> {
            CanteenSupplySummaryDTO.PriceVarianceItemDTO dto = new CanteenSupplySummaryDTO.PriceVarianceItemDTO();
            CanteenSupplierDO first = items.get(0);
            dto.setProjectName(normalize(first.getItemName(), UNKNOWN_PROJECT));
            dto.setSpecLevel(normalize(first.getSpecLevel(), UNKNOWN_SPEC));
            dto.setUnit(normalize(first.getUnit(), UNKNOWN_UNIT));
            dto.setRecordCount(items.size());

            BigDecimal min = items.stream().map(CanteenSupplierDO::getPrice)
                    .filter(Objects::nonNull).min(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
            BigDecimal max = items.stream().map(CanteenSupplierDO::getPrice)
                    .filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
            BigDecimal sum = items.stream().map(CanteenSupplierDO::getPrice)
                    .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal avg = items.isEmpty() ? BigDecimal.ZERO
                    : sum.divide(BigDecimal.valueOf(items.size()), 2, RoundingMode.HALF_UP);
            BigDecimal diff = max.subtract(min);

            dto.setMinPrice(min);
            dto.setMaxPrice(max);
            dto.setAvgPrice(avg);
            dto.setPriceDiff(diff);
            dto.setDiffRate(min.compareTo(BigDecimal.ZERO) == 0
                    ? BigDecimal.ZERO
                    : diff.divide(min, 4, RoundingMode.HALF_UP));
            dto.setPricePoints(distinctPricePoints(items));
            result.add(dto);
        });
        result.sort(Comparator.comparing(CanteenSupplySummaryDTO.PriceVarianceItemDTO::getPriceDiff).reversed());
        return result;
    }

    private List<CanteenSupplySummaryDTO.PriceExtremumItemDTO> buildExtremumItems(List<CanteenSupplierDO> all,
                                                                                 boolean max) {
        return all.stream()
                .filter(item -> item.getPrice() != null)
                .sorted(max
                        ? Comparator.comparing(CanteenSupplierDO::getPrice).reversed()
                        : Comparator.comparing(CanteenSupplierDO::getPrice))
                .limit(10)
                .map(this::toExtremumItem)
                .collect(Collectors.toList());
    }

    private CanteenSupplySummaryDTO.PriceExtremumItemDTO toExtremumItem(CanteenSupplierDO item) {
        CanteenSupplySummaryDTO.PriceExtremumItemDTO dto = new CanteenSupplySummaryDTO.PriceExtremumItemDTO();
        dto.setProjectName(normalize(item.getItemName(), UNKNOWN_PROJECT));
        dto.setSpecLevel(normalize(item.getSpecLevel(), UNKNOWN_SPEC));
        dto.setUnit(normalize(item.getUnit(), UNKNOWN_UNIT));
        dto.setPricePoint(normalize(item.getPurchasePoint(), UNKNOWN_PRICE_POINT));
        dto.setPrice(item.getPrice());
        return dto;
    }

    private List<String> distinctPricePoints(List<CanteenSupplierDO> items) {
        Set<String> points = items.stream()
                .map(item -> normalize(item.getPurchasePoint(), UNKNOWN_PRICE_POINT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return new ArrayList<>(points);
    }

    private String varianceKey(CanteenSupplierDO item) {
        return normalize(item.getItemName(), UNKNOWN_PROJECT)
                + "|" + normalize(item.getSpecLevel(), UNKNOWN_SPEC)
                + "|" + normalize(item.getUnit(), UNKNOWN_UNIT);
    }

    private String normalize(String value, String defaultValue) {
        return value == null || value.trim().isEmpty() ? defaultValue : value.trim();
    }

    private LocalDateTime resolveStartTime(String timeRange) {
        JijianQueryTimeRangeEnum e = JijianQueryTimeRangeEnum.of(timeRange);
        if (e == null) {
            e = JijianQueryTimeRangeEnum.ONE_MONTH;
        }
        return e.startDate().atStartOfDay();
    }
}
