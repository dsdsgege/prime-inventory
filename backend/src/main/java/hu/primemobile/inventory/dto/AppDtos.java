package hu.primemobile.inventory.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class AppDtos {

    // ==========================================
    // Supplier DTOs
    // ==========================================
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SupplierSummary {
        private Long id;
        private String name;
        @JsonProperty("tax_number")
        private String taxNumber;
        @JsonProperty("eu_tax_number")
        private String euTaxNumber;
        private String address;
        @JsonProperty("bank_account")
        private String bankAccount;
        @JsonProperty("created_at")
        private Instant createdAt;
        @JsonProperty("invoice_count")
        private int invoiceCount;
        @JsonProperty("total_in_stock_qty")
        private int totalInStockQty;
        @JsonProperty("total_ordered_qty")
        private int totalOrderedQty;
        @JsonProperty("total_received_qty")
        private int totalReceivedQty;
    }

    // ==========================================
    // Invoice DTOs
    // ==========================================
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class InvoiceSummary {
        private Long id;
        @JsonProperty("supplier_id")
        private Long supplierId;
        @JsonProperty("supplier_name")
        private String supplierName;
        @JsonProperty("invoice_number")
        private String invoiceNumber;
        @JsonProperty("issue_date")
        private LocalDate issueDate;
        @JsonProperty("delivery_date")
        private LocalDate deliveryDate;
        @JsonProperty("due_date")
        private LocalDate dueDate;
        @JsonProperty("payment_method")
        private String paymentMethod;
        @JsonProperty("total_net_amount")
        private Long totalNetAmount;
        @JsonProperty("total_vat_amount")
        private Long totalVatAmount;
        @JsonProperty("total_gross_amount")
        private Long totalGrossAmount;
        @JsonProperty("pdf_filename")
        private String pdfFilename;
        private String status;
        @JsonProperty("uploaded_at")
        private Instant uploadedAt;
        @JsonProperty("total_initial_quantity")
        private int totalInitialQuantity;
        @JsonProperty("total_received_quantity")
        private int totalReceivedQuantity;
        @JsonProperty("total_current_quantity")
        private int totalCurrentQuantity;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UploadSuccessResponse {
        private String message;
        @JsonProperty("invoice_id")
        private Long invoiceId;
        @JsonProperty("invoice_number")
        private String invoiceNumber;
        @JsonProperty("supplier_name")
        private String supplierName;
        @JsonProperty("item_count")
        private int itemCount;
        @JsonProperty("unit_count")
        private int unitCount;
        @JsonProperty("expense_only")
        private boolean expenseOnly;
        @JsonProperty("filtered_services")
        @Builder.Default
        private List<String> filteredServices = new ArrayList<>();
    }

    // ==========================================
    // Inventory Item DTOs (Stage 1 & Stage 2)
    // ==========================================
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class InventoryItemResponse {
        private Long id;
        @JsonProperty("invoice_id")
        private Long invoiceId;
        @JsonProperty("supplier_id")
        private Long supplierId;
        @JsonProperty("line_index")
        private int lineIndex;
        @JsonProperty("product_name")
        private String productName;
        @JsonProperty("raw_description")
        private String rawDescription;
        private String unit;
        @JsonProperty("initial_quantity")
        private int initialQuantity;
        @JsonProperty("received_quantity")
        private int receivedQuantity;
        @JsonProperty("sold_quantity")
        private int soldQuantity;
        @JsonProperty("current_quantity")
        private int currentQuantity;
        @JsonProperty("pending_quantity")
        private int pendingQuantity;
        @JsonProperty("unit_price_net")
        private Long unitPriceNet;
        @JsonProperty("unit_price_gross")
        private Long unitPriceGross;
        @JsonProperty("vat_rate")
        private String vatRate;
        @JsonProperty("total_net")
        private Long totalNet;
        @JsonProperty("total_vat")
        private Long totalVat;
        @JsonProperty("total_gross")
        private Long totalGross;
        private String status;
        @JsonProperty("received_at")
        private Instant receivedAt;
        @JsonProperty("expected_margin_percent")
        private Double expectedMarginPercent;
        @JsonProperty("expected_sale_price")
        private Long expectedSalePrice;
        @JsonProperty("wipe_codes")
        @Builder.Default
        private List<String> wipeCodes = new ArrayList<>();
        @JsonProperty("supplier_name")
        private String supplierName;
        @JsonProperty("invoice_number")
        private String invoiceNumber;
    }

    // ==========================================
    // Stage 1: Receiving Requests (Single & Batch)
    // ==========================================
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReceiveRequest {
        @Min(1)
        private int quantity = 1;
        @NotNull(message = "A várható eladási árrés (%) megadása kötelező!")
        @DecimalMin(value = "0.01", message = "A várható eladási árrésnek 0%-nál nagyobbnak kell lennie!")
        @JsonProperty("expected_margin_percent")
        private Double expectedMarginPercent;
        @JsonProperty("confirmed_wipe_codes")
        private List<String> confirmedWipeCodes;
        @JsonProperty("received_at")
        private Instant receivedAt;
        private String note;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BatchReceiveItemEntry {
        @NotNull
        @JsonProperty("item_id")
        private Long itemId;
        @Min(1)
        private int quantity = 1;
        @NotNull(message = "A várható eladási árrés (%) megadása kötelező!")
        @DecimalMin(value = "0.01", message = "A várható eladási árrésnek 0%-nál nagyobbnak kell lennie!")
        @JsonProperty("expected_margin_percent")
        private Double expectedMarginPercent;
        @JsonProperty("confirmed_wipe_codes")
        private List<String> confirmedWipeCodes;
        private String note;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BatchReceiveRequest {
        @NotEmpty
        private List<BatchReceiveItemEntry> items = new ArrayList<>();
        private String note;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CancelReceiveRequest {
        @Min(1)
        private int quantity = 1;
        private String note;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BatchCancelReceiveItemEntry {
        @NotNull
        @JsonProperty("item_id")
        private Long itemId;
        @Min(1)
        private int quantity = 1;
        private String note;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BatchCancelReceiveRequest {
        @NotEmpty
        private List<BatchCancelReceiveItemEntry> items = new ArrayList<>();
        private String note;
    }

    // ==========================================
    // Stage 2: Stock Selling Requests (Single & Batch)
    // ==========================================
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SaleRequest {
        @Min(1)
        private int quantity = 1;
        @NotNull
        @Min(1)
        @JsonProperty("sale_price")
        private Long salePrice;
        @JsonProperty("used_wipe_code")
        private String usedWipeCode;
        @JsonProperty("customer_name")
        private String customerName;
        private String note;
        @JsonProperty("sold_at")
        private Instant soldAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BatchSaleItemEntry {
        @NotNull
        @JsonProperty("item_id")
        private Long itemId;
        @Min(1)
        private int quantity = 1;
        @NotNull
        @Min(1)
        @JsonProperty("sale_price")
        private Long salePrice;
        @JsonProperty("used_wipe_code")
        private String usedWipeCode;
        private String note;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BatchSaleRequest {
        @NotEmpty
        private List<BatchSaleItemEntry> items = new ArrayList<>();
        @JsonProperty("customer_name")
        private String customerName;
        private String note;
        @JsonProperty("sold_at")
        private Instant soldAt;
    }

    // ==========================================
    // Stage 3: Sales Report & Cancel (Single & Batch)
    // ==========================================
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SaleResponse {
        private Long id;
        @JsonProperty("inventory_item_id")
        private Long inventoryItemId;
        @JsonProperty("invoice_id")
        private Long invoiceId;
        @JsonProperty("supplier_id")
        private Long supplierId;
        @JsonProperty("order_number")
        private String orderNumber;
        private int quantity;
        @JsonProperty("unit_cost_net")
        private Long unitCostNet;
        @JsonProperty("unit_cost_gross")
        private Long unitCostGross;
        @JsonProperty("sale_price")
        private Long salePrice;
        private Long profit;
        @JsonProperty("profit_margin_percent")
        private double profitMarginPercent;
        @JsonProperty("used_wipe_code")
        private String usedWipeCode;
        @JsonProperty("customer_name")
        private String customerName;
        private String note;
        @JsonProperty("sold_at")
        private Instant soldAt;
        @JsonProperty("created_at")
        private Instant createdAt;
        @JsonProperty("product_name")
        private String productName;
        @JsonProperty("supplier_name")
        private String supplierName;
        @JsonProperty("invoice_number")
        private String invoiceNumber;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SalesReportResponse {
        @JsonProperty("total_sales_count")
        private int totalSalesCount;
        @JsonProperty("total_units_sold")
        private int totalUnitsSold;
        @JsonProperty("total_revenue")
        private Long totalRevenue;
        @JsonProperty("total_cost")
        private Long totalCost;
        @JsonProperty("total_profit")
        private Long totalProfit;
        @JsonProperty("profit_margin_percent")
        private double profitMarginPercent;
        @Builder.Default
        private List<SaleResponse> sales = new ArrayList<>();
        // Pagination metadata when paged
        @JsonProperty("total_elements")
        private long totalElements;
        @JsonProperty("total_pages")
        private int totalPages;
        private int page;
        private int size;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BatchCancelSaleRequest {
        @NotEmpty
        @JsonProperty("sale_ids")
        private List<Long> saleIds = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BatchSalesImportResult {
        @JsonProperty("orders_processed")
        private int ordersProcessed;
        @JsonProperty("items_processed")
        private int itemsProcessed;
        @JsonProperty("items_matched_with_stock")
        private int itemsMatchedWithStock;
        @JsonProperty("items_missing_cost_price")
        private int itemsMissingCostPrice;
        @JsonProperty("total_sales_amount")
        private Long totalSalesAmount;
        @JsonProperty("total_profit_calculated")
        private Long totalProfitCalculated;
        @Builder.Default
        private List<String> notices = new ArrayList<>();
    }

    // ==========================================
    // Audit Log DTOs
    // ==========================================
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TransactionLogResponse {
        private Long id;
        @JsonProperty("inventory_item_id")
        private Long inventoryItemId;
        @JsonProperty("invoice_id")
        private Long invoiceId;
        @JsonProperty("supplier_id")
        private Long supplierId;
        @JsonProperty("action_type")
        private String actionType;
        @JsonProperty("quantity_changed")
        private int quantityChanged;
        @JsonProperty("quantity_before")
        private int quantityBefore;
        @JsonProperty("quantity_after")
        private int quantityAfter;
        @JsonProperty("used_wipe_code")
        private String usedWipeCode;
        private String note;
        @JsonProperty("created_at")
        private Instant createdAt;
        @JsonProperty("supplier_name")
        private String supplierName;
        @JsonProperty("invoice_number")
        private String invoiceNumber;
        @JsonProperty("product_name")
        private String productName;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BatchOperationResponse {
        private String message;
        @JsonProperty("processed_count")
        private int processedCount;
        @JsonProperty("total_quantity")
        private int totalQuantity;
        @Builder.Default
        private List<Object> results = new ArrayList<>();
    }
}
