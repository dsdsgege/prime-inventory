package hu.primemobile.inventory.web;

import hu.primemobile.inventory.dto.SalesDto;
import hu.primemobile.inventory.service.SalesService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/sales")
@RequiredArgsConstructor
public class SalesController {

    private final SalesService salesService;

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SalesDto.BatchSalesImportResult> importSalesSpreadsheet(
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(salesService.importSalesSpreadsheet(file));
    }

    @GetMapping("/orders")
    public ResponseEntity<List<SalesDto.SalesOrderView>> getOrders(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(salesService.getOrders(startDate, endDate));
    }

    @GetMapping("/profit-summary")
    public ResponseEntity<SalesDto.ProfitSummary> getProfitSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(salesService.getProfitSummary(startDate, endDate));
    }

    @GetMapping("/export")
    public ResponseEntity<String> exportProfitCsv(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        String csvData = salesService.exportProfitCsv(startDate, endDate);
        String filename = String.format("profit_report_%s_%s.csv",
                startDate != null ? startDate : "all",
                endDate != null ? endDate : "all");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csvData);
    }
}
