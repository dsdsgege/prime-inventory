package hu.primemobile.inventory.web;

import hu.primemobile.inventory.dto.InvoiceDto;
import hu.primemobile.inventory.service.InvoiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService invoiceService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<InvoiceDto.InvoiceDetail> uploadInvoice(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(invoiceService.uploadAndProcessInvoice(file));
    }

    @GetMapping
    public ResponseEntity<List<InvoiceDto.InvoiceSummary>> getAllInvoices() {
        return ResponseEntity.ok(invoiceService.getAllInvoices());
    }

    @GetMapping("/{id}")
    public ResponseEntity<InvoiceDto.InvoiceDetail> getInvoiceById(@PathVariable Long id) {
        return ResponseEntity.ok(invoiceService.getInvoiceById(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancelInvoice(@PathVariable Long id) {
        invoiceService.cancelInvoice(id);
        return ResponseEntity.noContent().build();
    }
}
