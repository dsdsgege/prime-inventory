package hu.primemobile.inventory.service;

import hu.primemobile.inventory.domain.*;
import hu.primemobile.inventory.dto.AppDtos;
import hu.primemobile.inventory.repository.InventoryItemRepository;
import hu.primemobile.inventory.repository.SaleRepository;
import hu.primemobile.inventory.repository.TransactionLogRepository;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    private final InventoryItemRepository inventoryItemRepository;
    private final SaleRepository saleRepository;
    private final TransactionLogRepository transactionLogRepository;

    // ==========================================
    // Stage 1: Receiving Items (Számlák & Átvétel)
    // ==========================================
    @Transactional(readOnly = true)
    public Page<AppDtos.InventoryItemResponse> getReceivingItemsPage(
            Long supplierId,
            Long invoiceId,
            String statusFilter,
            String search,
            Pageable pageable
    ) {
        log.debug("Fetching receiving items page: supplierId={}, invoiceId={}, status={}, search={}, page={}",
                supplierId, invoiceId, statusFilter, search, pageable);

        Specification<InventoryItem> spec = buildReceivingSpec(supplierId, invoiceId, statusFilter, search);
        Pageable sortedPageable = pageable.getSort().isSorted()
                ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.ASC, "status")
                        .and(Sort.by(Sort.Direction.DESC, "invoice.issueDate"))
                        .and(Sort.by(Sort.Direction.ASC, "lineIndex")));

        return inventoryItemRepository.findAll(spec, sortedPageable).map(this::mapToItemResponse);
    }

    @Transactional(readOnly = true)
    public List<AppDtos.InventoryItemResponse> getReceivingItems(
            Long supplierId,
            Long invoiceId,
            String statusFilter,
            String search
    ) {
        log.debug("Fetching all receiving items: supplierId={}, invoiceId={}, status={}, search={}",
                supplierId, invoiceId, statusFilter, search);

        Specification<InventoryItem> spec = buildReceivingSpec(supplierId, invoiceId, statusFilter, search);
        Sort sort = Sort.by(Sort.Direction.ASC, "status")
                .and(Sort.by(Sort.Direction.DESC, "invoice.issueDate"))
                .and(Sort.by(Sort.Direction.ASC, "lineIndex"));

        return inventoryItemRepository.findAll(spec, sort).stream()
                .map(this::mapToItemResponse)
                .toList();
    }

    private Specification<InventoryItem> buildReceivingSpec(
            Long supplierId,
            Long invoiceId,
            String statusFilter,
            String search
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            Join<InventoryItem, Supplier> supplierJoin = root.join("supplier", JoinType.LEFT);
            Join<InventoryItem, Invoice> invoiceJoin = root.join("invoice", JoinType.LEFT);

            if (supplierId != null && supplierId > 0) {
                predicates.add(cb.equal(supplierJoin.get("id"), supplierId));
            }
            if (invoiceId != null && invoiceId > 0) {
                predicates.add(cb.equal(invoiceJoin.get("id"), invoiceId));
            }
            if ("PENDING".equalsIgnoreCase(statusFilter)) {
                predicates.add(cb.lessThan(root.get("receivedQuantity"), root.get("initialQuantity")));
            } else if ("RECEIVED".equalsIgnoreCase(statusFilter)) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("receivedQuantity"), root.get("initialQuantity")));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("productName")), pattern),
                        cb.like(cb.lower(root.get("dataWipeCodes")), pattern),
                        cb.like(cb.lower(invoiceJoin.get("invoiceNumber")), pattern),
                        cb.like(cb.lower(supplierJoin.get("name")), pattern)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    @Transactional
    public Map<String, Object> receiveInventoryItem(Long itemId, AppDtos.ReceiveRequest req) {
        InventoryItem item = inventoryItemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("A megadott számlatétel nem található."));

        int pendingQty = item.getPendingQuantity();
        if (req.getQuantity() > pendingQty) {
            throw new IllegalArgumentException(String.format(
                    "Nem vehető át %d db! Ebből a számlatételből még legfeljebb %d db vár beérkezésre.",
                    req.getQuantity(), pendingQty
            ));
        }

        int qtyBefore = item.getCurrentQuantity();
        item.setReceivedQuantity(item.getReceivedQuantity() + req.getQuantity());
        item.setCurrentQuantity(item.getCurrentQuantity() + req.getQuantity());
        int qtyAfter = item.getCurrentQuantity();

        item.setReceivedAt(req.getReceivedAt() != null ? req.getReceivedAt() : Instant.now());

        if (item.getReceivedQuantity() >= item.getInitialQuantity()) {
            item.setStatus("RECEIVED");
            item.setFullyDeducted(true);
        } else {
            item.setStatus("PARTIAL");
        }

        inventoryItemRepository.save(item);

        String wipeCodeStr = (req.getConfirmedWipeCodes() != null && !req.getConfirmedWipeCodes().isEmpty())
                ? String.join(", ", req.getConfirmedWipeCodes())
                : null;

        String noteStr = (req.getNote() != null && !req.getNote().isBlank())
                ? req.getNote().trim()
                : String.format("Raktári átvétel: %d db", req.getQuantity());

        TransactionLog txLog = TransactionLog.builder()
                .item(item)
                .invoice(item.getInvoice())
                .supplier(item.getSupplier())
                .actionType("STOCK_RECEIVE")
                .quantityChanged(req.getQuantity())
                .quantityBefore(qtyBefore)
                .quantityAfter(qtyAfter)
                .usedWipeCode(wipeCodeStr)
                .note(noteStr)
                .createdAt(Instant.now())
                .build();
        transactionLogRepository.save(txLog);

        log.info("Received {} units of item #{} ({}) into store stock. New stock: {}",
                req.getQuantity(), item.getId(), item.getProductName(), qtyAfter);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("id", item.getId());
        res.put("product_name", item.getProductName());
        res.put("received_quantity", item.getReceivedQuantity());
        res.put("current_quantity", item.getCurrentQuantity());
        res.put("quantity_after", item.getCurrentQuantity());
        res.put("status", item.getStatus());
        res.put("received_at", item.getReceivedAt());
        return res;
    }

    @Transactional
    public AppDtos.BatchOperationResponse batchReceiveItems(AppDtos.BatchReceiveRequest req) {
        log.info("Executing batch receive for {} items", req.getItems().size());
        List<Object> results = new ArrayList<>();
        int totalQty = 0;

        for (AppDtos.BatchReceiveItemEntry entry : req.getItems()) {
            AppDtos.ReceiveRequest singleReq = new AppDtos.ReceiveRequest(
                    entry.getQuantity(),
                    entry.getConfirmedWipeCodes(),
                    Instant.now(),
                    entry.getNote() != null && !entry.getNote().isBlank() ? entry.getNote() : req.getNote()
            );
            Map<String, Object> res = receiveInventoryItem(entry.getItemId(), singleReq);
            results.add(res);
            totalQty += entry.getQuantity();
        }

        return AppDtos.BatchOperationResponse.builder()
                .message(String.format("Csoportos átvétel sikeres: %d tétel (%d db készülék) raktárra véve.", results.size(), totalQty))
                .processedCount(results.size())
                .totalQuantity(totalQty)
                .results(results)
                .build();
    }

    @Transactional
    public Map<String, Object> cancelReceiveInventoryItem(Long itemId, AppDtos.CancelReceiveRequest req) {
        InventoryItem item = inventoryItemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("A megadott tétel nem található."));

        if (item.getReceivedQuantity() < req.getQuantity()) {
            throw new IllegalArgumentException(String.format(
                    "Nem vonható vissza %d db, mert összesen csak %d db lett átvéve.",
                    req.getQuantity(), item.getReceivedQuantity()
            ));
        }

        if (item.getCurrentQuantity() < req.getQuantity()) {
            throw new IllegalArgumentException(String.format(
                    "Nem vonható vissza %d db átvétel, mert időközben már eladásra került belőle! Jelenlegi készlet: %d db.",
                    req.getQuantity(), item.getCurrentQuantity()
            ));
        }

        int qtyBefore = item.getCurrentQuantity();
        item.setReceivedQuantity(item.getReceivedQuantity() - req.getQuantity());
        item.setCurrentQuantity(item.getCurrentQuantity() - req.getQuantity());
        int qtyAfter = item.getCurrentQuantity();

        if (item.getReceivedQuantity() == 0) {
            item.setStatus("ORDERED");
            item.setReceivedAt(null);
            item.setFullyDeducted(false);
        } else if (item.getReceivedQuantity() < item.getInitialQuantity()) {
            item.setStatus("PARTIAL");
            item.setFullyDeducted(false);
        } else {
            item.setStatus("RECEIVED");
            item.setFullyDeducted(true);
        }

        inventoryItemRepository.save(item);

        String noteStr = (req.getNote() != null && !req.getNote().isBlank())
                ? req.getNote().trim()
                : String.format("Átvétel visszavonása (-%d db)", req.getQuantity());

        TransactionLog txLog = TransactionLog.builder()
                .item(item)
                .invoice(item.getInvoice())
                .supplier(item.getSupplier())
                .actionType("STOCK_RECEIVE_CANCEL")
                .quantityChanged(-req.getQuantity())
                .quantityBefore(qtyBefore)
                .quantityAfter(qtyAfter)
                .note(noteStr)
                .createdAt(Instant.now())
                .build();
        transactionLogRepository.save(txLog);

        log.info("Cancelled receipt of {} units for item #{} ({}). New stock: {}",
                req.getQuantity(), item.getId(), item.getProductName(), qtyAfter);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("id", item.getId());
        res.put("product_name", item.getProductName());
        res.put("received_quantity", item.getReceivedQuantity());
        res.put("current_quantity", item.getCurrentQuantity());
        res.put("quantity_after", item.getCurrentQuantity());
        res.put("status", item.getStatus());
        return res;
    }

    @Transactional
    public AppDtos.BatchOperationResponse batchCancelReceiveItems(AppDtos.BatchCancelReceiveRequest req) {
        log.info("Executing batch cancel receive for {} items", req.getItems().size());
        List<Object> results = new ArrayList<>();
        int totalQty = 0;

        for (AppDtos.BatchCancelReceiveItemEntry entry : req.getItems()) {
            AppDtos.CancelReceiveRequest singleReq = new AppDtos.CancelReceiveRequest(
                    entry.getQuantity(),
                    entry.getNote() != null && !entry.getNote().isBlank() ? entry.getNote() : req.getNote()
            );
            Map<String, Object> res = cancelReceiveInventoryItem(entry.getItemId(), singleReq);
            results.add(res);
            totalQty += entry.getQuantity();
        }

        return AppDtos.BatchOperationResponse.builder()
                .message(String.format("Csoportos visszavonás sikeres: %d tétel (-%d db) visszavéve.", results.size(), totalQty))
                .processedCount(results.size())
                .totalQuantity(totalQty)
                .results(results)
                .build();
    }

    // ==========================================
    // Stage 2: Physical Store Stock & Selling (Raktárkészlet & Eladás)
    // ==========================================
    @Transactional(readOnly = true)
    public Page<AppDtos.InventoryItemResponse> getStockItemsPage(
            Long supplierId,
            Long invoiceId,
            boolean onlyInStock,
            String search,
            Pageable pageable
    ) {
        log.debug("Fetching stock items page: supplierId={}, invoiceId={}, onlyInStock={}, search={}, page={}",
                supplierId, invoiceId, onlyInStock, search, pageable);

        Specification<InventoryItem> spec = buildStockSpec(supplierId, invoiceId, onlyInStock, search);
        Pageable sortedPageable = pageable.getSort().isSorted()
                ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "currentQuantity")
                        .and(Sort.by(Sort.Direction.DESC, "receivedAt"))
                        .and(Sort.by(Sort.Direction.ASC, "productName")));

        return inventoryItemRepository.findAll(spec, sortedPageable).map(this::mapToItemResponse);
    }

    @Transactional(readOnly = true)
    public List<AppDtos.InventoryItemResponse> getStockItems(
            Long supplierId,
            Long invoiceId,
            boolean onlyInStock,
            String search
    ) {
        log.debug("Fetching all stock items: supplierId={}, invoiceId={}, onlyInStock={}, search={}",
                supplierId, invoiceId, onlyInStock, search);

        Specification<InventoryItem> spec = buildStockSpec(supplierId, invoiceId, onlyInStock, search);
        Sort sort = Sort.by(Sort.Direction.DESC, "currentQuantity")
                .and(Sort.by(Sort.Direction.DESC, "receivedAt"))
                .and(Sort.by(Sort.Direction.ASC, "productName"));

        return inventoryItemRepository.findAll(spec, sort).stream()
                .map(this::mapToItemResponse)
                .toList();
    }

    private Specification<InventoryItem> buildStockSpec(
            Long supplierId,
            Long invoiceId,
            boolean onlyInStock,
            String search
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            Join<InventoryItem, Supplier> supplierJoin = root.join("supplier", JoinType.LEFT);
            Join<InventoryItem, Invoice> invoiceJoin = root.join("invoice", JoinType.LEFT);

            // Must be at least partially received into the store
            predicates.add(cb.greaterThan(root.get("receivedQuantity"), 0));

            if (onlyInStock) {
                predicates.add(cb.greaterThan(root.get("currentQuantity"), 0));
            }
            if (supplierId != null && supplierId > 0) {
                predicates.add(cb.equal(supplierJoin.get("id"), supplierId));
            }
            if (invoiceId != null && invoiceId > 0) {
                predicates.add(cb.equal(invoiceJoin.get("id"), invoiceId));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("productName")), pattern),
                        cb.like(cb.lower(root.get("dataWipeCodes")), pattern),
                        cb.like(cb.lower(invoiceJoin.get("invoiceNumber")), pattern),
                        cb.like(cb.lower(supplierJoin.get("name")), pattern)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    @Transactional
    public Map<String, Object> sellInventoryItem(Long itemId, AppDtos.SaleRequest req) {
        InventoryItem item = inventoryItemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("A megadott termék nem található a raktárkészletben."));

        if (item.getCurrentQuantity() < req.getQuantity()) {
            throw new IllegalArgumentException(String.format(
                    "Nem értékesíthető %d db! A jelenlegi elérhető készlet csak %d db.",
                    req.getQuantity(), item.getCurrentQuantity()
            ));
        }

        int qtyBefore = item.getCurrentQuantity();
        item.setSoldQuantity(item.getSoldQuantity() + req.getQuantity());
        item.setCurrentQuantity(item.getCurrentQuantity() - req.getQuantity());
        int qtyAfter = item.getCurrentQuantity();

        long unitGrossCost = item.getUnitPriceGross();
        long unitNetCost = item.getUnitPriceNet() != null ? item.getUnitPriceNet() : 0L;
        long totalCost = unitGrossCost * req.getQuantity();
        long profit = req.getSalePrice() - totalCost;
        long profitNet = Math.round(profit / 1.27);

        inventoryItemRepository.save(item);

        Sale sale = Sale.builder()
                .item(item)
                .invoice(item.getInvoice())
                .supplier(item.getSupplier())
                .quantity(req.getQuantity())
                .unitCostNet(unitNetCost)
                .unitCostGross(unitGrossCost)
                .salePrice(req.getSalePrice())
                .profit(profit)
                .profitNet(profitNet)
                .usedWipeCode(req.getUsedWipeCode())
                .customerName(req.getCustomerName())
                .note(req.getNote())
                .source("POS")
                .soldAt(req.getSoldAt() != null ? req.getSoldAt() : Instant.now())
                .createdAt(Instant.now())
                .build();
        Sale savedSale = saleRepository.save(sale);

        String logNote = String.format("Eladás: %,d Ft (Haszon: %,d Ft). %s",
                req.getSalePrice(), profit, req.getNote() != null ? req.getNote() : "").trim();

        TransactionLog txLog = TransactionLog.builder()
                .item(item)
                .invoice(item.getInvoice())
                .supplier(item.getSupplier())
                .actionType("SALE")
                .quantityChanged(-req.getQuantity())
                .quantityBefore(qtyBefore)
                .quantityAfter(qtyAfter)
                .usedWipeCode(req.getUsedWipeCode())
                .note(logNote)
                .createdAt(Instant.now())
                .build();
        transactionLogRepository.save(txLog);

        double marginPct = req.getSalePrice() > 0
                ? Math.round((profit * 10000.0) / req.getSalePrice()) / 100.0
                : 0.0;

        log.info("Sold {} units of item #{} ({}) for {} Ft (Profit: {} Ft, Remaining: {})",
                req.getQuantity(), item.getId(), item.getProductName(), req.getSalePrice(), profit, qtyAfter);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("sale_id", savedSale.getId());
        res.put("product_name", item.getProductName());
        res.put("quantity_sold", req.getQuantity());
        res.put("sale_price", req.getSalePrice());
        res.put("total_cost", totalCost);
        res.put("profit", profit);
        res.put("profit_margin_percent", marginPct);
        res.put("remaining_stock", item.getCurrentQuantity());
        res.put("sold_at", savedSale.getSoldAt());
        return res;
    }

    @Transactional
    public AppDtos.BatchOperationResponse batchSellItems(AppDtos.BatchSaleRequest req) {
        log.info("Executing batch sale for {} items (customer: {})", req.getItems().size(), req.getCustomerName());
        List<Object> results = new ArrayList<>();
        int totalQty = 0;
        long totalRevenue = 0L;
        long totalProfit = 0L;

        for (AppDtos.BatchSaleItemEntry entry : req.getItems()) {
            String note = entry.getNote() != null && !entry.getNote().isBlank() ? entry.getNote() : req.getNote();
            AppDtos.SaleRequest singleReq = new AppDtos.SaleRequest(
                    entry.getQuantity(),
                    entry.getSalePrice(),
                    entry.getUsedWipeCode(),
                    req.getCustomerName(),
                    note,
                    req.getSoldAt() != null ? req.getSoldAt() : Instant.now()
            );
            Map<String, Object> res = sellInventoryItem(entry.getItemId(), singleReq);
            results.add(res);
            totalQty += entry.getQuantity();
            totalRevenue += entry.getSalePrice();
            totalProfit += ((Number) res.get("profit")).longValue();
        }

        return AppDtos.BatchOperationResponse.builder()
                .message(String.format("Csoportos eladás sikeres: %d tétel (%d db), Bevétel: %,d Ft, Haszon: +%,d Ft",
                        results.size(), totalQty, totalRevenue, totalProfit))
                .processedCount(results.size())
                .totalQuantity(totalQty)
                .results(results)
                .build();
    }

    // ==========================================
    // Legacy Inventory & Deduct / Restore
    // ==========================================
    @Transactional
    public Map<String, Object> deductInventoryItem(Long itemId, AppDtos.DeductRequest req) {
        AppDtos.ReceiveRequest receiveReq = new AppDtos.ReceiveRequest(
                req.getQuantity(),
                req.getUsedWipeCode() != null ? List.of(req.getUsedWipeCode()) : null,
                Instant.now(),
                req.getNote()
        );
        return receiveInventoryItem(itemId, receiveReq);
    }

    @Transactional
    public Map<String, Object> restoreInventoryItem(Long itemId, AppDtos.RestoreRequest req) {
        AppDtos.CancelReceiveRequest cancelReq = new AppDtos.CancelReceiveRequest(
                req.getQuantity(),
                req.getNote()
        );
        return cancelReceiveInventoryItem(itemId, cancelReq);
    }

    public AppDtos.InventoryItemResponse mapToItemResponse(InventoryItem it) {
        return AppDtos.InventoryItemResponse.builder()
                .id(it.getId())
                .invoiceId(it.getInvoice() != null ? it.getInvoice().getId() : null)
                .supplierId(it.getSupplier() != null ? it.getSupplier().getId() : null)
                .lineIndex(it.getLineIndex())
                .productName(it.getProductName())
                .rawDescription(it.getRawDescription())
                .unit(it.getUnit())
                .initialQuantity(it.getInitialQuantity())
                .receivedQuantity(it.getReceivedQuantity())
                .soldQuantity(it.getSoldQuantity())
                .currentQuantity(it.getCurrentQuantity())
                .pendingQuantity(it.getPendingQuantity())
                .unitPriceNet(it.getUnitPriceNet())
                .unitPriceGross(it.getUnitPriceGross())
                .vatRate(it.getVatRate())
                .totalNet(it.getTotalNet())
                .totalVat(it.getTotalVat())
                .totalGross(it.getTotalGross())
                .status(it.getStatus())
                .receivedAt(it.getReceivedAt())
                .isFullyDeducted(it.isFullyDeducted())
                .wipeCodes(it.getWipeCodesList())
                .supplierName(it.getSupplier() != null ? it.getSupplier().getName() : "")
                .invoiceNumber(it.getInvoice() != null ? it.getInvoice().getInvoiceNumber() : "")
                .build();
    }
}
