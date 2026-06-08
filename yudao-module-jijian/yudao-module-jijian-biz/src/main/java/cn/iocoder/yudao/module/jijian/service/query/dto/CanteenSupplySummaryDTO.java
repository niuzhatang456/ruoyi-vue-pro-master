package cn.iocoder.yudao.module.jijian.service.query.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class CanteenSupplySummaryDTO {

    private long totalCount;
    private long totalProjectCount;
    private List<NameCountDTO> byProjectName = new ArrayList<>();
    private List<NameCountDTO> byPricePoint = new ArrayList<>();
    private List<PriceVarianceItemDTO> priceVarianceItems = new ArrayList<>();
    private List<PriceExtremumItemDTO> maxPriceItems = new ArrayList<>();
    private List<PriceExtremumItemDTO> minPriceItems = new ArrayList<>();

    @Data
    public static class NameCountDTO {
        private String name;
        private long count;
    }

    @Data
    public static class PriceVarianceItemDTO {
        private String projectName;
        private String specLevel;
        private String unit;
        private BigDecimal minPrice = BigDecimal.ZERO;
        private BigDecimal maxPrice = BigDecimal.ZERO;
        private BigDecimal avgPrice = BigDecimal.ZERO;
        private BigDecimal priceDiff = BigDecimal.ZERO;
        private BigDecimal diffRate = BigDecimal.ZERO;
        private List<String> pricePoints = new ArrayList<>();
        private long recordCount;
    }

    @Data
    public static class PriceExtremumItemDTO {
        private String projectName;
        private String specLevel;
        private String unit;
        private String pricePoint;
        private BigDecimal price = BigDecimal.ZERO;
    }
}
