package hu.primemobile.inventory.web;

import hu.primemobile.inventory.dto.AppDtos;
import hu.primemobile.inventory.service.InvoiceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class InvoiceController {

    private final InvoiceService invoiceService;

    @GetMapping("/suppliers")
    public ResponseEntity<List<AppDtos.SupplierSummary>> listSuppliers() {
        return ResponseEntity.ok(invoiceService.getAllSuppliersWithSummary());
    }

    @PostMapping(value = "/invoices/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AppDtos.UploadSuccessResponse> uploadInvoice(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(invoiceService.uploadAndProcessInvoice(file));
    }

    @GetMapping("/invoices")
    public ResponseEntity<List<AppDtos.InvoiceSummary>> listInvoices(
            @RequestParam(name = "supplier_id", required = false) String supplierId,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "search", required = false) String search,
            @PageableDefault(size = 200) Pageable pageable
    ) {
        Page<AppDtos.InvoiceSummary> page = invoiceService.getInvoicesPage(
                cleanOptionalLong(supplierId),
                cleanOptionalStr(status),
                cleanOptionalStr(search),
                pageable
        );
        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(page.getTotalElements()))
                .header("X-Total-Pages", String.valueOf(page.getTotalPages()))
                .body(page.getContent());
    }

    @GetMapping("/invoices/page")
    public ResponseEntity<Page<AppDtos.InvoiceSummary>> listInvoicesPaged(
            @RequestParam(name = "supplier_id", required = false) String supplierId,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "search", required = false) String search,
            @PageableDefault(size = 25) Pageable pageable
    ) {
        return ResponseEntity.ok(invoiceService.getInvoicesPage(
                cleanOptionalLong(supplierId),
                cleanOptionalStr(status),
                cleanOptionalStr(search),
                pageable
        ));
    }

    static Long cleanOptionalLong(String val) {
        if (val == null || val.isBlank()) return null;
        String s = val.trim().toLowerCase();
        if (s.equals("null") || s.equals("undefined") || s.equals("none") || s.equals("0")) return null;
        try {
            long parsed = Long.parseLong(s);
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static String cleanOptionalStr(String val) {
        if (val == null || val.isBlank()) return null;
        String s = val.trim();
        if (s.equalsIgnoreCase("null") || s.equalsIgnoreCase("undefined") || s.equalsIgnoreCase("none")) return null;
        return s;
    }

    static boolean cleanOptionalBool(String val, boolean defaultVal) {
        if (val == null || val.isBlank()) return defaultVal;
        String s = val.trim().toLowerCase();
        if (s.equals("null") || s.equals("undefined") || s.equals("none")) return defaultVal;
        return !s.equals("false") && !s.equals("0");
    }
}
