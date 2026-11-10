package hu.primemobile.inventory.service;

import hu.primemobile.inventory.domain.*;
import hu.primemobile.inventory.dto.InventoryDto;
import hu.primemobile.inventory.repository.InventoryItemRepository;
import hu.primemobile.inventory.repository.InvoiceItemRepository;
import hu.primemobile.inventory.repository.InvoiceRepository;
import hu.primemobile.inventory.repository.NmhhWipeCodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    private final InventoryItemRepository inventoryItemRepository;
    private final InvoiceItemRepository invoiceItemRepository;
    private final InvoiceRepository invoiceRepository;
    private final NmhhWipeCodeRepository wipeCodeRepository;

    @Transactional
    public List<InventoryDto.InventoryItemView> receiveInvoiceItem(InventoryDto.ReceiveInvoiceItemRequest request) {
        InvoiceItem invoiceItem = invoiceItemRepository.findById(request.getInvoiceItemId())
                .orElseThrow(() -> new IllegalArgumentException("A számlatétel nem található: " + request.getInvoiceItemId()));

        int qtyToReceive = request.getQuantity() != null && request.getQuantity() > 0 ? request.getQuantity() : 1;
        if (qtyToReceive > invoiceItem.getCurrentQuantity()) {
            throw new IllegalArgumentException(String.format("Nem vételezhető be %d db, mert már csak %d db érhető el a számlán.",
                    qtyToReceive, invoiceItem.getCurrentQuantity()));
        }

        // Decrement invoice line quantity
        invoiceItem.setCurrentQuantity(invoiceItem.getCurrentQuantity() - qtyToReceive);
        if (invoiceItem.getCurrentQuantity() == 0) {
            invoiceItem.setFullyDeducted(true);
        }
        invoiceItemRepository.save(invoiceItem);

        // Check if entire invoice is fully deducted
        Invoice invoice = invoiceItem.getInvoice();
        boolean allDeducted = invoice.getItems().stream().allMatch(InvoiceItem::isFullyDeducted);
        if (allDeducted) {
            invoice.setStatus(InvoiceStatus.DEDUCTED);
            invoiceRepository.save(invoice);
        }

        // Create physical inventory items in IN_STOCK status
        List<InventoryDto.InventoryItemView> createdViews = new ArrayList<>();
        long unitNet = invoiceItem.getUnitPriceNet();
        long unitGross = invoiceItem.getInitialQuantity() > 0 ? invoiceItem.getTotalGross() / invoiceItem.getInitialQuantity() : unitNet;

        // Find available wipe code if not explicitly given
        List<NmhhWipeCode> unusedCodes = wipeCodeRepository.findByInvoiceItemId(invoiceItem.getId()).stream()
                .filter(c -> !c.isUsed())
                .collect(Collectors.toList());

        for (int i = 0; i < qtyToReceive; i++) {
            String assignedCode = request.getWipeCode();
            if (assignedCode == null && !unusedCodes.isEmpty()) {
                NmhhWipeCode codeObj = unusedCodes.remove(0);
                codeObj.setUsed(true);
                codeObj.setUsedAt(Instant.now());
                wipeCodeRepository.save(codeObj);
                assignedCode = codeObj.getWipeCode();
            }

            InventoryItem item = InventoryItem.builder()
                    .invoiceItem(invoiceItem)
                    .productName(invoiceItem.getProductName())
                    .serialNumber(request.getSerialNumber())
                    .wipeCode(assignedCode)
                    .acquisitionNetPrice(unitNet)
                    .acquisitionGrossPrice(unitGross)
                    .receivedAt(Instant.now())
                    .status(InventoryStatus.IN_STOCK)
                    .build();

            InventoryItem saved = inventoryItemRepository.save(item);
            createdViews.add(mapToView(saved));
        }

        log.info("Received {} pcs of '{}' into warehouse stock", qtyToReceive, invoiceItem.getProductName());
        return createdViews;
    }

    @Transactional(readOnly = true)
    public List<InventoryDto.InventoryItemView> getInventory(InventoryStatus status, String search) {
        InventoryStatus targetStatus = status != null ? status : InventoryStatus.IN_STOCK;
        List<InventoryItem> items;
        if (search != null && !search.isBlank()) {
            items = inventoryItemRepository.searchByStatusAndProductName(targetStatus, search.trim());
        } else {
            items = inventoryItemRepository.findByStatusOrderByReceivedAtDesc(targetStatus);
        }
        return items.stream().map(this::mapToView).collect(Collectors.toList());
    }

    @Transactional
    public InventoryDto.InventoryItemView sellItem(InventoryDto.SellItemRequest request) {
        InventoryItem item = inventoryItemRepository.findById(request.getInventoryItemId())
                .orElseThrow(() -> new IllegalArgumentException("A raktári tétel nem található: " + request.getInventoryItemId()));

        if (item.getStatus() == InventoryStatus.SOLD) {
            throw new IllegalStateException("Ez a tétel már el van adva.");
        }

        item.setStatus(InventoryStatus.SOLD);
        item.setSellingPriceGross(request.getSellingPriceGross());
        item.setSellingPriceNet(request.getSellingPriceNet() != null ? request.getSellingPriceNet() : request.getSellingPriceGross());
        item.setSoldAt(Instant.now());
        if (request.getNotes() != null) {
            item.setNotes(request.getNotes());
        }

        InventoryItem updated = inventoryItemRepository.save(item);
        log.info("Item #{} ('{}') marked as SOLD for {} Ft", updated.getId(), updated.getProductName(), updated.getSellingPriceGross());
        return mapToView(updated);
    }

    @Transactional
    public InventoryDto.InventoryItemView updateStatus(Long id, InventoryDto.UpdateStatusRequest request) {
        InventoryItem item = inventoryItemRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("A raktári tétel nem található: " + id));

        item.setStatus(request.getStatus());
        if (request.getNotes() != null) {
            item.setNotes(request.getNotes());
        }

        return mapToView(inventoryItemRepository.save(item));
    }

    private InventoryDto.InventoryItemView mapToView(InventoryItem item) {
        return InventoryDto.InventoryItemView.builder()
                .id(item.getId())
                .invoiceItemId(item.getInvoiceItem() != null ? item.getInvoiceItem().getId() : null)
                .productName(item.getProductName())
                .serialNumber(item.getSerialNumber())
                .wipeCode(item.getWipeCode())
                .acquisitionNetPrice(item.getAcquisitionNetPrice())
                .acquisitionGrossPrice(item.getAcquisitionGrossPrice())
                .receivedAt(item.getReceivedAt())
                .status(item.getStatus())
                .sellingPriceNet(item.getSellingPriceNet())
                .sellingPriceGross(item.getSellingPriceGross())
                .soldAt(item.getSoldAt())
                .notes(item.getNotes())
                .build();
    }
}
