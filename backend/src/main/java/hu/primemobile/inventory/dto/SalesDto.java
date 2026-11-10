package hu.primemobile.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class SalesDto {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SalesOrderView {
        private Long id;
        private String orderNumber;
        private LocalDate orderDate;
        private String customerName;
        private Long totalSellingPriceGross;
        private Long totalCostPriceGross;
        private Long totalProfitGross;
        private Long totalProfitNet;
        private String source;
        @Builder.Default
        private List<SaleItemView> items = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SaleItemView {
        private Long id;
        private Long salesOrderId;
        private Long inventoryItemId;
        private String productName;
        private Integer quantity;
        private Long sellingPriceGross;
        private Long costPriceGross;
        private Long profitGross;
        private Long profitNet;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ProfitSummary {
        private LocalDate startDate;
        private LocalDate endDate;
        private Long totalSalesGross;
        private Long totalCostGross;
        private Long totalProfitGross;
        private Long totalProfitNet;
        private int totalOrdersCount;
        private int totalItemsSold;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BatchSalesImportResult {
        private int ordersProcessed;
        private int itemsProcessed;
        private int itemsMatchedWithStock;
        private int itemsMissingCostPrice;
        private Long totalSalesAmount;
        private Long totalProfitCalculated;
        @Builder.Default
        private List<String> notices = new ArrayList<>();
    }
}
