package hu.primemobile.inventory.web;

import hu.primemobile.inventory.dto.AppDtos;
import hu.primemobile.inventory.service.SalesService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static hu.primemobile.inventory.web.InvoiceController.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class SalesController {

    private final SalesService salesService;

    // ==========================================
    // Stage 3: Sales Report & Profit Analytics (/api/sales)
    // ==========================================
    @GetMapping("/sales")
    public ResponseEntity<AppDtos.SalesReportResponse> getSalesReport(
            @RequestParam(name = "supplier_id", required = false) String supplierId,
            @RequestParam(name = "date_from", required = false) String dateFrom,
            @RequestParam(name = "date_to", required = false) String dateTo,
            @RequestParam(name = "search", required = false) String search,
            @PageableDefault(size = 200) Pageable pageable
    ) {
        return ResponseEntity.ok(salesService.getSalesReport(
                cleanOptionalLong(supplierId),
                parseDate(dateFrom),
                parseDate(dateTo),
                cleanOptionalStr(search),
                pageable
        ));
    }

    @PostMapping("/sales/{saleId}/cancel")
    public ResponseEntity<Map<String, Object>> cancelSale(@PathVariable Long saleId) {
        return ResponseEntity.ok(salesService.cancelSale(saleId));
    }

    @PostMapping("/sales/batch-cancel")
    public ResponseEntity<AppDtos.BatchOperationResponse> batchCancelSales(
            @Valid @RequestBody AppDtos.BatchCancelSaleRequest req
    ) {
        return ResponseEntity.ok(salesService.batchCancelSales(req));
    }

    @PostMapping(value = "/sales/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AppDtos.BatchSalesImportResult> importSalesSpreadsheet(
            @RequestParam("file") MultipartFile file
    ) {
        return ResponseEntity.ok(salesService.importSalesSpreadsheet(file));
    }

    @GetMapping("/sales/export")
    public ResponseEntity<byte[]> exportProfitCsv(
            @RequestParam(name = "supplier_id", required = false) String supplierId,
            @RequestParam(name = "date_from", required = false) String dateFrom,
            @RequestParam(name = "date_to", required = false) String dateTo,
            @RequestParam(name = "search", required = false) String search
    ) {
        LocalDate from = parseDate(dateFrom);
        LocalDate to = parseDate(dateTo);
        String csvData = salesService.exportProfitCsv(
                cleanOptionalLong(supplierId),
                from,
                to,
                cleanOptionalStr(search)
        );
        String filename = String.format("profit_report_%s_%s.csv",
                from != null ? from : "all",
                to != null ? to : "all");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csvData.getBytes(StandardCharsets.UTF_8));
    }

    // ==========================================
    // Audit Log Endpoints (/api/logs)
    // ==========================================
    @GetMapping("/logs/page")
    public ResponseEntity<Page<AppDtos.TransactionLogResponse>> listLogsPaged(
            @RequestParam(name = "supplier_id", required = false) String supplierId,
            @RequestParam(name = "invoice_id", required = false) String invoiceId,
            @RequestParam(name = "inventory_item_id", required = false) String inventoryItemId,
            @RequestParam(name = "action_type", required = false) String actionType,
            @RequestParam(name = "date_from", required = false) String dateFrom,
            @RequestParam(name = "date_to", required = false) String dateTo,
            @PageableDefault(size = 50) Pageable pageable
    ) {
        return ResponseEntity.ok(salesService.getTransactionLogsPage(
                cleanOptionalLong(supplierId),
                cleanOptionalLong(invoiceId),
                cleanOptionalLong(inventoryItemId),
                cleanOptionalStr(actionType),
                parseDate(dateFrom),
                parseDate(dateTo),
                pageable
        ));
    }

    private LocalDate parseDate(String str) {
        String cleaned = cleanOptionalStr(str);
        if (cleaned == null) return null;
        try {
            return LocalDate.parse(cleaned);
        } catch (Exception e) {
            return null;
        }
    }
}
