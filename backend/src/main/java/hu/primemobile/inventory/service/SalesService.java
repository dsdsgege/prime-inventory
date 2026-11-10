package hu.primemobile.inventory.service;

import hu.primemobile.inventory.domain.*;
import hu.primemobile.inventory.dto.AppDtos;
import hu.primemobile.inventory.dto.ParserDto;
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
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class SalesService {

    private static final ZoneId HU_ZONE = ZoneId.of("Europe/Budapest");

    private final ParserClient parserClient;
    private final SaleRepository saleRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final TransactionLogRepository transactionLogRepository;

    @Transactional(readOnly = true)
    public AppDtos.SalesReportResponse getSalesReport(
            Long supplierId,
            LocalDate dateFrom,
            LocalDate dateTo,
            String search,
            Pageable pageable
    ) {
        log.debug("Generating sales report: supplierId={}, dateFrom={}, dateTo={}, search={}, pageable={}",
                supplierId, dateFrom, dateTo, search, pageable);

        Specification<Sale> spec = buildSalesSpec(supplierId, dateFrom, dateTo, search);
        Sort sort = Sort.by(Sort.Direction.DESC, "soldAt").and(Sort.by(Sort.Direction.DESC, "id"));

        List<Sale> allMatchingSales = saleRepository.findAll(spec, sort);

        int totalSalesCount = allMatchingSales.size();
        int totalUnitsSold = 0;
        long totalRevenue = 0L;
        long totalCost = 0L;
        long totalProfit = 0L;

        for (Sale s : allMatchingSales) {
            int qty = s.getQuantity() != null ? s.getQuantity() : 1;
            long unitGross = s.getUnitCostGross() != null ? s.getUnitCostGross() : 0L;
            long rev = s.getSalePrice() != null ? s.getSalePrice() : 0L;
            long prof = s.getProfit() != null ? s.getProfit() : 0L;

            totalUnitsSold += qty;
            totalRevenue += rev;
            totalCost += (unitGross * qty);
            totalProfit += prof;
        }

        double profitMarginPct = totalRevenue > 0
                ? Math.round((totalProfit * 10000.0) / totalRevenue) / 100.0
                : 0.0;

        List<AppDtos.SaleResponse> salesList;
        long totalElements = totalSalesCount;
        int totalPages = 1;
        int pageNum = 0;
        int pageSize = totalSalesCount;

        if (pageable != null && pageable.isPaged()) {
            Pageable sortedPageable = pageable.getSort().isSorted()
                    ? pageable
                    : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
            Page<Sale> salesPage = saleRepository.findAll(spec, sortedPageable);
            salesList = salesPage.getContent().stream().map(this::mapToSaleResponse).toList();
            totalElements = salesPage.getTotalElements();
            totalPages = salesPage.getTotalPages();
            pageNum = salesPage.getNumber();
            pageSize = salesPage.getSize();
        } else {
            salesList = allMatchingSales.stream().map(this::mapToSaleResponse).toList();
        }

        return AppDtos.SalesReportResponse.builder()
                .totalSalesCount(totalSalesCount)
                .totalUnitsSold(totalUnitsSold)
                .totalRevenue(totalRevenue)
                .totalCost(totalCost)
                .totalProfit(totalProfit)
                .profitMarginPercent(profitMarginPct)
                .sales(salesList)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .page(pageNum)
                .size(pageSize)
                .build();
    }

    private Specification<Sale> buildSalesSpec(
            Long supplierId,
            LocalDate dateFrom,
            LocalDate dateTo,
            String search
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            Join<Sale, InventoryItem> itemJoin = root.join("item", JoinType.LEFT);
            Join<Sale, Supplier> supplierJoin = root.join("supplier", JoinType.LEFT);
            Join<Sale, Invoice> invoiceJoin = root.join("invoice", JoinType.LEFT);

            if (supplierId != null && supplierId > 0) {
                predicates.add(cb.equal(supplierJoin.get("id"), supplierId));
            }
            if (dateFrom != null) {
                Instant startInstant = dateFrom.atStartOfDay(HU_ZONE).toInstant();
                predicates.add(cb.greaterThanOrEqualTo(root.get("soldAt"), startInstant));
            }
            if (dateTo != null) {
                Instant endInstant = dateTo.plusDays(1).atStartOfDay(HU_ZONE).toInstant();
                predicates.add(cb.lessThan(root.get("soldAt"), endInstant));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(itemJoin.get("productName")), pattern),
                        cb.like(cb.lower(root.get("note")), pattern),
                        cb.like(cb.lower(root.get("usedWipeCode")), pattern),
                        cb.like(cb.lower(root.get("customerName")), pattern),
                        cb.like(cb.lower(root.get("orderNumber")), pattern),
                        cb.like(cb.lower(invoiceJoin.get("invoiceNumber")), pattern),
                        cb.like(cb.lower(supplierJoin.get("name")), pattern)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    @Transactional
    public Map<String, Object> cancelSale(Long saleId) {
        Sale sale = saleRepository.findById(saleId)
                .orElseThrow(() -> new IllegalArgumentException("A megadott eladási tranzakció nem található."));

        InventoryItem item = sale.getItem();
        int restoredStock = 0;
        String productName = sale.getNote() != null ? sale.getNote() : "Termék";

        if (item != null) {
            productName = item.getProductName();
            int qtyBefore = item.getCurrentQuantity();
            item.setSoldQuantity(Math.max(0, item.getSoldQuantity() - sale.getQuantity()));
            item.setCurrentQuantity(item.getCurrentQuantity() + sale.getQuantity());
            int qtyAfter = item.getCurrentQuantity();
            restoredStock = qtyAfter;

            inventoryItemRepository.save(item);

            TransactionLog txLog = TransactionLog.builder()
                    .item(item)
                    .invoice(item.getInvoice())
                    .supplier(item.getSupplier())
                    .actionType("SALE_CANCEL")
                    .quantityChanged(sale.getQuantity())
                    .quantityBefore(qtyBefore)
                    .quantityAfter(qtyAfter)
                    .usedWipeCode(sale.getUsedWipeCode())
                    .note(String.format("Eladás visszavonása (#%d, %d db visszavétele készletre)", sale.getId(), sale.getQuantity()))
                    .createdAt(Instant.now())
                    .build();
            transactionLogRepository.save(txLog);
        }

        saleRepository.delete(sale);
        log.info("Cancelled sale #{} ({}), restored stock: {}", saleId, productName, restoredStock);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("message", String.format("Eladás (#%d) sikeresen visszavonva.", saleId));
        res.put("product_name", productName);
        res.put("restored_stock", restoredStock);
        return res;
    }

    @Transactional
    public AppDtos.BatchOperationResponse batchCancelSales(AppDtos.BatchCancelSaleRequest req) {
        log.info("Executing batch cancel for {} sales", req.getSaleIds().size());
        List<Object> results = new ArrayList<>();
        for (Long saleId : req.getSaleIds()) {
            results.add(cancelSale(saleId));
        }
        return AppDtos.BatchOperationResponse.builder()
                .message(String.format("Csoportos sztornó sikeres: %d eladás visszavonva, a termékek visszakerültek a készletre.", results.size()))
                .processedCount(results.size())
                .totalQuantity(results.size())
                .results(results)
                .build();
    }

    @Transactional
    public AppDtos.BatchSalesImportResult importSalesSpreadsheet(MultipartFile file) {
        log.info("Importing sales spreadsheet: {}", file.getOriginalFilename());
        ParserDto.ParsedSpreadsheetResponse parsed = parserClient.parseSpreadsheet(file);

        int ordersProcessed = 0;
        int itemsProcessed = 0;
        int matchedWithStock = 0;
        int missingCost = 0;
        long totalSales = 0L;
        long totalProfit = 0L;
        List<String> notices = new ArrayList<>();

        List<ParserDto.ParsedSalesOrder> orderList = parsed.getOrders();
        if (orderList == null || orderList.isEmpty()) {
            ParserDto.ParsedSalesOrder fallbackOrder = new ParserDto.ParsedSalesOrder();
            fallbackOrder.setOrderNumber("BATCH-" + System.currentTimeMillis());
            fallbackOrder.setOrderDate(LocalDate.now().toString());
            fallbackOrder.setCustomerName("Excel Import");
            fallbackOrder.setItems(parsed.getItems() != null ? parsed.getItems() : new ArrayList<>());
            orderList = List.of(fallbackOrder);
        }

        for (ParserDto.ParsedSalesOrder pOrder : orderList) {
            ordersProcessed++;
            String orderNum = pOrder.getOrderNumber() != null ? pOrder.getOrderNumber().trim() : "ORD-" + System.currentTimeMillis();
            LocalDate oDate = parseDateSafe(pOrder.getOrderDate());
            Instant soldInstant = oDate != null ? oDate.atStartOfDay(HU_ZONE).plusHours(12).toInstant() : Instant.now();

            if (pOrder.getItems() != null) {
                for (ParserDto.ParsedSalesItem pItem : pOrder.getItems()) {
                    itemsProcessed++;
                    String prodName = pItem.getProductName() != null ? pItem.getProductName().trim() : "Ismeretlen termék";
                    int qty = pItem.getQuantity() != null && pItem.getQuantity() > 0 ? pItem.getQuantity() : 1;
                    long saleGross = pItem.getSellingPriceGross() != null ? pItem.getSellingPriceGross() : 0L;
                    Long costGross = pItem.getCostPriceGross();
                    Long profitNet = pItem.getProfitNet();

                    InventoryItem matchedItem = null;
                    List<InventoryItem> matches = inventoryItemRepository.findInStockMatchingProduct(prodName);
                    if (!matches.isEmpty()) {
                        matchedItem = matches.get(0);
                    }

                    if (matchedItem != null) {
                        int deductQty = Math.min(qty, matchedItem.getCurrentQuantity());
                        int qtyBefore = matchedItem.getCurrentQuantity();
                        matchedItem.setSoldQuantity(matchedItem.getSoldQuantity() + deductQty);
                        matchedItem.setCurrentQuantity(matchedItem.getCurrentQuantity() - deductQty);
                        int qtyAfter = matchedItem.getCurrentQuantity();
                        inventoryItemRepository.save(matchedItem);

                        if (costGross == null || costGross <= 0) {
                            costGross = matchedItem.getUnitPriceGross();
                        }
                        matchedWithStock++;

                        TransactionLog txLog = TransactionLog.builder()
                                .item(matchedItem)
                                .invoice(matchedItem.getInvoice())
                                .supplier(matchedItem.getSupplier())
                                .actionType("SALE")
                                .quantityChanged(-deductQty)
                                .quantityBefore(qtyBefore)
                                .quantityAfter(qtyAfter)
                                .note(String.format("Excel import eladás (%s - %s): %,d Ft", orderNum, pOrder.getCustomerName(), saleGross))
                                .createdAt(Instant.now())
                                .build();
                        transactionLogRepository.save(txLog);
                    } else if (costGross == null || costGross <= 0) {
                        costGross = 0L;
                        missingCost++;
                        notices.add(String.format("Rendelés %s: '%s' nem található raktárkészleten (beker ár hiányzik)!", orderNum, prodName));
                    }

                    long totalCostGross = costGross * qty;
                    long profitGross = saleGross - totalCostGross;
                    if (profitNet == null) {
                        profitNet = Math.round(profitGross / 1.27);
                    }

                    Sale sale = Sale.builder()
                            .item(matchedItem)
                            .invoice(matchedItem != null ? matchedItem.getInvoice() : null)
                            .supplier(matchedItem != null ? matchedItem.getSupplier() : null)
                            .orderNumber(orderNum)
                            .quantity(qty)
                            .unitCostNet(matchedItem != null ? matchedItem.getUnitPriceNet() : costGross)
                            .unitCostGross(costGross)
                            .salePrice(saleGross)
                            .profit(profitGross)
                            .profitNet(profitNet)
                            .customerName(pOrder.getCustomerName())
                            .note(matchedItem == null ? prodName + " (" + orderNum + ")" : "Rendelés: " + orderNum)
                            .source("EXCEL_IMPORT")
                            .soldAt(soldInstant)
                            .createdAt(Instant.now())
                            .build();
                    saleRepository.save(sale);

                    totalSales += saleGross;
                    totalProfit += profitGross;
                }
            }
        }

        log.info("Excel import finished: {} orders, {} items, {} matched with stock",
                ordersProcessed, itemsProcessed, matchedWithStock);

        return AppDtos.BatchSalesImportResult.builder()
                .ordersProcessed(ordersProcessed)
                .itemsProcessed(itemsProcessed)
                .itemsMatchedWithStock(matchedWithStock)
                .itemsMissingCostPrice(missingCost)
                .totalSalesAmount(totalSales)
                .totalProfitCalculated(totalProfit)
                .notices(notices)
                .build();
    }

    @Transactional(readOnly = true)
    public String exportProfitCsv(Long supplierId, LocalDate dateFrom, LocalDate dateTo, String search) {
        Specification<Sale> spec = buildSalesSpec(supplierId, dateFrom, dateTo, search);
        Sort sort = Sort.by(Sort.Direction.DESC, "soldAt").and(Sort.by(Sort.Direction.DESC, "id"));
        List<Sale> sales = saleRepository.findAll(spec, sort);

        StringBuilder sb = new StringBuilder();
        sb.append("Sorszám\tDátum\tNév\tTermék\tEladási Ár br\tBeker Ár br\tProfit Br\tNetto Profit\n");

        for (Sale s : sales) {
            String orderNo = s.getOrderNumber() != null ? s.getOrderNumber() : ("#" + s.getId());
            String dateStr = s.getSoldAt() != null
                    ? s.getSoldAt().atZone(HU_ZONE).toLocalDate().toString()
                    : "";
            String cust = s.getCustomerName() != null ? s.getCustomerName() : "";
            String prod = s.getItem() != null ? s.getItem().getProductName() : (s.getNote() != null ? s.getNote() : "Termék");
            long costTotal = (s.getUnitCostGross() != null ? s.getUnitCostGross() : 0L) * (s.getQuantity() != null ? s.getQuantity() : 1);
            long profitGross = s.getProfit() != null ? s.getProfit() : 0L;
            long profitNet = s.getProfitNet() != null && s.getProfitNet() != 0L
                    ? s.getProfitNet()
                    : Math.round(profitGross / 1.27);

            sb.append(orderNo).append("\t")
                    .append(dateStr).append("\t")
                    .append(cust).append("\t")
                    .append(prod).append("\t")
                    .append(s.getSalePrice() != null ? s.getSalePrice() : 0L).append("\t")
                    .append(costTotal > 0 ? costTotal : "").append("\t")
                    .append(profitGross).append("\t")
                    .append(profitNet).append("\n");
        }
        return sb.toString();
    }

    // ==========================================
    // Audit Log Operations (Mozgásnapló)
    // ==========================================
    @Transactional(readOnly = true)
    public Page<AppDtos.TransactionLogResponse> getTransactionLogsPage(
            Long supplierId,
            Long invoiceId,
            Long inventoryItemId,
            String actionType,
            LocalDate dateFrom,
            LocalDate dateTo,
            Pageable pageable
    ) {
        log.debug("Fetching transaction logs page: supplierId={}, invoiceId={}, itemId={}, actionType={}, page={}",
                supplierId, invoiceId, inventoryItemId, actionType, pageable);

        Specification<TransactionLog> spec = buildLogSpec(supplierId, invoiceId, inventoryItemId, actionType, dateFrom, dateTo);
        Pageable sortedPageable = pageable.getSort().isSorted()
                ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(Sort.Direction.DESC, "createdAt"));

        return transactionLogRepository.findAll(spec, sortedPageable).map(this::mapToLogResponse);
    }

    @Transactional(readOnly = true)
    public List<AppDtos.TransactionLogResponse> getTransactionLogs(
            Long supplierId,
            Long invoiceId,
            Long inventoryItemId,
            String actionType,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
        log.debug("Fetching all transaction logs: supplierId={}, invoiceId={}, itemId={}, actionType={}",
                supplierId, invoiceId, inventoryItemId, actionType);

        Specification<TransactionLog> spec = buildLogSpec(supplierId, invoiceId, inventoryItemId, actionType, dateFrom, dateTo);
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");

        return transactionLogRepository.findAll(spec, sort).stream()
                .map(this::mapToLogResponse)
                .toList();
    }

    private Specification<TransactionLog> buildLogSpec(
            Long supplierId,
            Long invoiceId,
            Long inventoryItemId,
            String actionType,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            Join<TransactionLog, Supplier> supplierJoin = root.join("supplier", JoinType.LEFT);
            Join<TransactionLog, Invoice> invoiceJoin = root.join("invoice", JoinType.LEFT);
            Join<TransactionLog, InventoryItem> itemJoin = root.join("item", JoinType.LEFT);

            if (supplierId != null && supplierId > 0) {
                predicates.add(cb.equal(supplierJoin.get("id"), supplierId));
            }
            if (invoiceId != null && invoiceId > 0) {
                predicates.add(cb.equal(invoiceJoin.get("id"), invoiceId));
            }
            if (inventoryItemId != null && inventoryItemId > 0) {
                predicates.add(cb.equal(itemJoin.get("id"), inventoryItemId));
            }
            if (actionType != null && !actionType.isBlank()) {
                predicates.add(cb.equal(root.get("actionType"), actionType.trim()));
            }
            if (dateFrom != null) {
                Instant startInstant = dateFrom.atStartOfDay(HU_ZONE).toInstant();
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), startInstant));
            }
            if (dateTo != null) {
                Instant endInstant = dateTo.plusDays(1).atStartOfDay(HU_ZONE).toInstant();
                predicates.add(cb.lessThan(root.get("createdAt"), endInstant));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private AppDtos.SaleResponse mapToSaleResponse(Sale s) {
        long salePrice = s.getSalePrice() != null ? s.getSalePrice() : 0L;
        long profit = s.getProfit() != null ? s.getProfit() : 0L;
        double marginPct = salePrice > 0
                ? Math.round((profit * 10000.0) / salePrice) / 100.0
                : 0.0;

        String prodName = s.getItem() != null
                ? s.getItem().getProductName()
                : (s.getNote() != null ? s.getNote() : "Termék");

        return AppDtos.SaleResponse.builder()
                .id(s.getId())
                .inventoryItemId(s.getItem() != null ? s.getItem().getId() : null)
                .invoiceId(s.getInvoice() != null ? s.getInvoice().getId() : null)
                .supplierId(s.getSupplier() != null ? s.getSupplier().getId() : null)
                .orderNumber(s.getOrderNumber())
                .quantity(s.getQuantity() != null ? s.getQuantity() : 1)
                .unitCostNet(s.getUnitCostNet() != null ? s.getUnitCostNet() : 0L)
                .unitCostGross(s.getUnitCostGross() != null ? s.getUnitCostGross() : 0L)
                .salePrice(salePrice)
                .profit(profit)
                .profitMarginPercent(marginPct)
                .usedWipeCode(s.getUsedWipeCode())
                .customerName(s.getCustomerName())
                .note(s.getNote())
                .soldAt(s.getSoldAt())
                .createdAt(s.getCreatedAt())
                .productName(prodName)
                .supplierName(s.getSupplier() != null ? s.getSupplier().getName() : "Excel / Direkt")
                .invoiceNumber(s.getInvoice() != null ? s.getInvoice().getInvoiceNumber() : (s.getOrderNumber() != null ? s.getOrderNumber() : "–"))
                .build();
    }

    private AppDtos.TransactionLogResponse mapToLogResponse(TransactionLog l) {
        return AppDtos.TransactionLogResponse.builder()
                .id(l.getId())
                .inventoryItemId(l.getItem() != null ? l.getItem().getId() : null)
                .invoiceId(l.getInvoice() != null ? l.getInvoice().getId() : null)
                .supplierId(l.getSupplier() != null ? l.getSupplier().getId() : null)
                .actionType(l.getActionType())
                .quantityChanged(l.getQuantityChanged())
                .quantityBefore(l.getQuantityBefore())
                .quantityAfter(l.getQuantityAfter())
                .usedWipeCode(l.getUsedWipeCode())
                .note(l.getNote())
                .createdAt(l.getCreatedAt())
                .supplierName(l.getSupplier() != null ? l.getSupplier().getName() : "")
                .invoiceNumber(l.getInvoice() != null ? l.getInvoice().getInvoiceNumber() : "")
                .productName(l.getItem() != null ? l.getItem().getProductName() : "")
                .build();
    }

    private LocalDate parseDateSafe(String str) {
        if (str == null || str.isBlank()) return null;
        try {
            return LocalDate.parse(str.trim());
        } catch (Exception e) {
            return null;
        }
    }
}
