package hu.primemobile.inventory.dto;

import hu.primemobile.inventory.domain.InventoryStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

public class InventoryDto {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class InventoryItemView {
        private Long id;
        private Long invoiceItemId;
        private String productName;
        private String serialNumber;
        private String wipeCode;
        private Long acquisitionNetPrice;
        private Long acquisitionGrossPrice;
        private Instant receivedAt;
        private InventoryStatus status;
        private Long sellingPriceNet;
        private Long sellingPriceGross;
        private Instant soldAt;
        private String notes;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReceiveInvoiceItemRequest {
        @NotNull
        private Long invoiceItemId;
        private Integer quantity = 1;
        private String serialNumber;
        private String wipeCode;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SellItemRequest {
        @NotNull
        private Long inventoryItemId;
        @NotNull
        private Long sellingPriceGross;
        private Long sellingPriceNet;
        private String customerName;
        private String notes;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateStatusRequest {
        @NotNull
        private InventoryStatus status;
        private String notes;
    }
}
