package hu.primemobile.inventory.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

public class ParserDto {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParsedInvoiceResponse {
        private ParsedSupplier supplier;
        private ParsedInvoice invoice;
        private List<ParsedInvoiceItem> items = new ArrayList<>();
        @JsonProperty("filtered_services")
        private List<String> filteredServices = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParsedSupplier {
        private String name;
        @JsonProperty("tax_number")
        private String taxNumber;
        @JsonProperty("eu_tax_number")
        private String euTaxNumber;
        private String address;
        @JsonProperty("bank_account")
        private String bankAccount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParsedInvoice {
        @JsonProperty("invoice_number")
        private String invoiceNumber;
        @JsonProperty("issue_date")
        private String issueDate;
        @JsonProperty("delivery_date")
        private String deliveryDate;
        @JsonProperty("due_date")
        private String dueDate;
        @JsonProperty("payment_method")
        private String paymentMethod;
        @JsonProperty("total_net_amount")
        private Long totalNetAmount;
        @JsonProperty("total_vat_amount")
        private Long totalVatAmount;
        @JsonProperty("total_gross_amount")
        private Long totalGrossAmount;
        private String status;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParsedInvoiceItem {
        @JsonProperty("line_index")
        private Integer lineIndex;
        @JsonProperty("product_name")
        private String productName;
        @JsonProperty("raw_description")
        private String rawDescription;
        @JsonProperty("wipe_codes")
        private List<String> wipeCodes = new ArrayList<>();
        private String unit;
        @JsonProperty("initial_quantity")
        private Integer initialQuantity;
        @JsonProperty("unit_price_net")
        private Long unitPriceNet;
        @JsonProperty("vat_rate")
        private String vatRate;
        @JsonProperty("total_net")
        private Long totalNet;
        @JsonProperty("total_vat")
        private Long totalVat;
        @JsonProperty("total_gross")
        private Long totalGross;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParsedSpreadsheetResponse {
        @JsonProperty("file_type")
        private String fileType;
        @JsonProperty("sheet_type")
        private String sheetType;
        @JsonProperty("total_orders")
        private Integer totalOrders;
        @JsonProperty("total_items")
        private Integer totalItems;
        private List<ParsedSalesOrder> orders = new ArrayList<>();
        private List<ParsedSalesItem> items = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParsedSalesOrder {
        @JsonProperty("order_number")
        private String orderNumber;
        @JsonProperty("order_date")
        private String orderDate;
        @JsonProperty("customer_name")
        private String customerName;
        private List<ParsedSalesItem> items = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParsedSalesItem {
        @JsonProperty("line_index")
        private Integer lineIndex;
        @JsonProperty("order_number")
        private String orderNumber;
        @JsonProperty("order_date")
        private String orderDate;
        @JsonProperty("customer_name")
        private String customerName;
        @JsonProperty("product_name")
        private String productName;
        private Integer quantity;
        @JsonProperty("selling_price_gross")
        private Long sellingPriceGross;
        @JsonProperty("cost_price_gross")
        private Long costPriceGross;
        @JsonProperty("profit_gross")
        private Long profitGross;
        @JsonProperty("profit_net")
        private Long profitNet;
        @JsonProperty("wipe_codes")
        private List<String> wipeCodes = new ArrayList<>();
    }
}
