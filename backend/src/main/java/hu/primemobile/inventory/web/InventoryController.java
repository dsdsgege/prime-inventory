package hu.primemobile.inventory.web;

import hu.primemobile.inventory.dto.AppDtos;
import hu.primemobile.inventory.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import static hu.primemobile.inventory.web.InvoiceController.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class InventoryController {

    private final InventoryService inventoryService;

    // ==========================================
    // Stage 1: Invoices & Receiving (/api/receiving)
    // ==========================================
    @GetMapping("/receiving")
    public ResponseEntity<List<AppDtos.InventoryItemResponse>> listReceivingItems(
            @RequestParam(name = "supplier_id", required = false) String supplierId,
            @RequestParam(name = "invoice_id", required = false) String invoiceId,
            @RequestParam(name = "status", required = false) String statusFilter,
            @RequestParam(name = "search", required = false) String search,
            @PageableDefault(size = 200) Pageable pageable
    ) {
        Page<AppDtos.InventoryItemResponse> page = inventoryService.getReceivingItemsPage(
                cleanOptionalLong(supplierId),
                cleanOptionalLong(invoiceId),
                cleanOptionalStr(statusFilter),
                cleanOptionalStr(search),
                pageable
        );
        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(page.getTotalElements()))
                .header("X-Total-Pages", String.valueOf(page.getTotalPages()))
                .body(page.getContent());
    }

    @GetMapping("/receiving/page")
    public ResponseEntity<Page<AppDtos.InventoryItemResponse>> listReceivingItemsPaged(
            @RequestParam(name = "supplier_id", required = false) String supplierId,
            @RequestParam(name = "invoice_id", required = false) String invoiceId,
            @RequestParam(name = "status", required = false) String statusFilter,
            @RequestParam(name = "search", required = false) String search,
            @PageableDefault(size = 25) Pageable pageable
    ) {
        return ResponseEntity.ok(inventoryService.getReceivingItemsPage(
                cleanOptionalLong(supplierId),
                cleanOptionalLong(invoiceId),
                cleanOptionalStr(statusFilter),
                cleanOptionalStr(search),
                pageable
        ));
    }

    @PostMapping("/receiving/{itemId}/receive")
    public ResponseEntity<Map<String, Object>> receiveItem(
            @PathVariable Long itemId,
            @Valid @RequestBody AppDtos.ReceiveRequest req
    ) {
        return ResponseEntity.ok(inventoryService.receiveInventoryItem(itemId, req));
    }

    @PostMapping("/receiving/batch-receive")
    public ResponseEntity<AppDtos.BatchOperationResponse> batchReceiveItems(
            @Valid @RequestBody AppDtos.BatchReceiveRequest req
    ) {
        return ResponseEntity.ok(inventoryService.batchReceiveItems(req));
    }

    @PostMapping("/receiving/{itemId}/cancel")
    public ResponseEntity<Map<String, Object>> cancelReceive(
            @PathVariable Long itemId,
            @Valid @RequestBody AppDtos.CancelReceiveRequest req
    ) {
        return ResponseEntity.ok(inventoryService.cancelReceiveInventoryItem(itemId, req));
    }

    @PostMapping("/receiving/batch-cancel")
    public ResponseEntity<AppDtos.BatchOperationResponse> batchCancelReceive(
            @Valid @RequestBody AppDtos.BatchCancelReceiveRequest req
    ) {
        return ResponseEntity.ok(inventoryService.batchCancelReceiveItems(req));
    }

    // ==========================================
    // Stage 2: Physical Store Stock & Selling (/api/stock)
    // ==========================================
    @GetMapping("/stock")
    public ResponseEntity<List<AppDtos.InventoryItemResponse>> listStockItems(
            @RequestParam(name = "supplier_id", required = false) String supplierId,
            @RequestParam(name = "invoice_id", required = false) String invoiceId,
            @RequestParam(name = "only_in_stock", required = false, defaultValue = "true") String onlyInStock,
            @RequestParam(name = "search", required = false) String search,
            @PageableDefault(size = 200) Pageable pageable
    ) {
        Page<AppDtos.InventoryItemResponse> page = inventoryService.getStockItemsPage(
                cleanOptionalLong(supplierId),
                cleanOptionalLong(invoiceId),
                cleanOptionalBool(onlyInStock, true),
                cleanOptionalStr(search),
                pageable
        );
        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(page.getTotalElements()))
                .header("X-Total-Pages", String.valueOf(page.getTotalPages()))
                .body(page.getContent());
    }

    @GetMapping("/stock/page")
    public ResponseEntity<Page<AppDtos.InventoryItemResponse>> listStockItemsPaged(
            @RequestParam(name = "supplier_id", required = false) String supplierId,
            @RequestParam(name = "invoice_id", required = false) String invoiceId,
            @RequestParam(name = "only_in_stock", required = false, defaultValue = "true") String onlyInStock,
            @RequestParam(name = "search", required = false) String search,
            @PageableDefault(size = 25) Pageable pageable
    ) {
        return ResponseEntity.ok(inventoryService.getStockItemsPage(
                cleanOptionalLong(supplierId),
                cleanOptionalLong(invoiceId),
                cleanOptionalBool(onlyInStock, true),
                cleanOptionalStr(search),
                pageable
        ));
    }

    @PostMapping("/stock/{itemId}/sell")
    public ResponseEntity<Map<String, Object>> sellItem(
            @PathVariable Long itemId,
            @Valid @RequestBody AppDtos.SaleRequest req
    ) {
        return ResponseEntity.ok(inventoryService.sellInventoryItem(itemId, req));
    }

    @PostMapping("/stock/batch-sell")
    public ResponseEntity<AppDtos.BatchOperationResponse> batchSellItems(
            @Valid @RequestBody AppDtos.BatchSaleRequest req
    ) {
        return ResponseEntity.ok(inventoryService.batchSellItems(req));
    }

    // ==========================================
    // Legacy Inventory & Deduction Endpoints (/api/inventory)
    // ==========================================
    @GetMapping("/inventory")
    public ResponseEntity<List<AppDtos.InventoryItemResponse>> listInventoryLegacy(
            @RequestParam(name = "supplier_id", required = false) String supplierId,
            @RequestParam(name = "invoice_id", required = false) String invoiceId,
            @RequestParam(name = "only_available", required = false) String onlyAvailable,
            @RequestParam(name = "search", required = false) String search,
            @PageableDefault(size = 200) Pageable pageable
    ) {
        boolean onlyAvail = cleanOptionalBool(onlyAvailable, false);
        Page<AppDtos.InventoryItemResponse> page = inventoryService.getReceivingItemsPage(
                cleanOptionalLong(supplierId),
                cleanOptionalLong(invoiceId),
                onlyAvail ? "PENDING" : "ALL",
                cleanOptionalStr(search),
                pageable
        );
        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(page.getTotalElements()))
                .header("X-Total-Pages", String.valueOf(page.getTotalPages()))
                .body(page.getContent());
    }

    @PostMapping("/inventory/{itemId}/deduct")
    public ResponseEntity<Map<String, Object>> deductItemLegacy(
            @PathVariable Long itemId,
            @Valid @RequestBody AppDtos.DeductRequest req
    ) {
        return ResponseEntity.ok(inventoryService.deductInventoryItem(itemId, req));
    }

    @PostMapping("/inventory/{itemId}/restore")
    public ResponseEntity<Map<String, Object>> restoreItemLegacy(
            @PathVariable Long itemId,
            @Valid @RequestBody AppDtos.RestoreRequest req
    ) {
        return ResponseEntity.ok(inventoryService.restoreInventoryItem(itemId, req));
    }
}
