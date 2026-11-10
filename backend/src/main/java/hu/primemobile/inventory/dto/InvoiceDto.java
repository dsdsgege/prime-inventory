package hu.primemobile.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class InvoiceDto {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class InvoiceSummary {
        private Long id;
        private String invoiceNumber;
        private String supplierName;
        private LocalDate issueDate;
        private LocalDate deliveryDate;
        private LocalDate dueDate;
        private String paymentMethod;
        private Long totalNetAmount;
        private Long totalVatAmount;
        private Long totalGrossAmount;
        private String status;
        private int itemsCount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class InvoiceDetail {
        private Long id;
        private String invoiceNumber;
        private SupplierDto supplier;
        private LocalDate issueDate;
        private LocalDate deliveryDate;
        private LocalDate dueDate;
        private String paymentMethod;
        private Long totalNetAmount;
        private Long totalVatAmount;
        private Long totalGrossAmount;
        private String status;
        @Builder.Default
        private List<InvoiceItemDetail> items = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SupplierDto {
        private Long id;
        private String name;
        private String taxNumber;
        private String euTaxNumber;
        private String address;
        private String bankAccount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class InvoiceItemDetail {
        private Long id;
        private Integer lineIndex;
        private String productName;
        private String rawDescription;
        private String unit;
        private Integer initialQuantity;
        private Integer currentQuantity;
        private Long unitPriceNet;
        private String vatRate;
        private Long totalNet;
        private Long totalVat;
        private Long totalGross;
        private boolean fullyDeducted;
        @Builder.Default
        private List<String> wipeCodes = new ArrayList<>();
    }
}
