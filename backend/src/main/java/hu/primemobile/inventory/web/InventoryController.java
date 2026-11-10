package hu.primemobile.inventory.web;

import hu.primemobile.inventory.domain.InventoryStatus;
import hu.primemobile.inventory.dto.InventoryDto;
import hu.primemobile.inventory.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping
    public ResponseEntity<List<InventoryDto.InventoryItemView>> getInventory(
            @RequestParam(required = false) InventoryStatus status,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(inventoryService.getInventory(status, search));
    }

    @PostMapping("/receive")
    public ResponseEntity<List<InventoryDto.InventoryItemView>> receiveInvoiceItem(
            @Valid @RequestBody InventoryDto.ReceiveInvoiceItemRequest request) {
        return ResponseEntity.ok(inventoryService.receiveInvoiceItem(request));
    }

    @PostMapping("/sell")
    public ResponseEntity<InventoryDto.InventoryItemView> sellItem(
            @Valid @RequestBody InventoryDto.SellItemRequest request) {
        return ResponseEntity.ok(inventoryService.sellItem(request));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<InventoryDto.InventoryItemView> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody InventoryDto.UpdateStatusRequest request) {
        return ResponseEntity.ok(inventoryService.updateStatus(id, request));
    }
}
