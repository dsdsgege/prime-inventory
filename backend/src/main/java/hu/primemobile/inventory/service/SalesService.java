package hu.primemobile.inventory.service;

import hu.primemobile.inventory.domain.*;
import hu.primemobile.inventory.dto.ParserDto;
import hu.primemobile.inventory.dto.SalesDto;
import hu.primemobile.inventory.repository.InventoryItemRepository;
import hu.primemobile.inventory.repository.SaleItemRepository;
import hu.primemobile.inventory.repository.SalesOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SalesService {

    private final ParserClient parserClient;
    private final SalesOrderRepository salesOrderRepository;
    private final SaleItemRepository saleItemRepository;
    private final InventoryItemRepository inventoryItemRepository;

    @Transactional
    public SalesDto.BatchSalesImportResult importSalesSpreadsheet(MultipartFile file) {
        ParserDto.ParsedSpreadsheetResponse parsed = parserClient.parseSpreadsheet(file);

        int ordersProcessed = 0;
        int itemsProcessed = 0;
        int matchedWithStock = 0;
        int missingCost = 0;
        long totalSales = 0L;
        long totalProfit = 0L;
        List<String> notices = new ArrayList<>();

        List<ParserDto.ParsedSalesOrder> orderList = parsed.getOrders();

        // If no grouped orders, wrap items in generic batches
        if (orderList == null || orderList.isEmpty()) {
            ParserDto.ParsedSalesOrder fallbackOrder = new ParserDto.ParsedSalesOrder();
            fallbackOrder.setOrderNumber("BATCH-" + System.currentTimeMillis());
            fallbackOrder.setOrderDate(LocalDate.now().toString());
            fallbackOrder.setCustomerName("Excel Import");
            fallbackOrder.setItems(parsed.getItems() != null ? parsed.getItems() : new ArrayList<>());
            orderList = List.of(fallbackOrder);
        }

        for (ParserDto.ParsedSalesOrder pOrder : orderList) {
            String orderNum = pOrder.getOrderNumber() != null ? pOrder.getOrderNumber().trim() : "ORD-" + System.currentTimeMillis();
            LocalDate oDate = parseDateSafe(pOrder.getOrderDate());

            SalesOrder salesOrder = SalesOrder.builder()
                    .orderNumber(orderNum)
                    .orderDate(oDate != null ? oDate : LocalDate.now())
                    .customerName(pOrder.getCustomerName())
                    .source("EXCEL_IMPORT")
                    .items(new ArrayList<>())
                    .build();

            SalesOrder savedOrder = salesOrderRepository.save(salesOrder);
            ordersProcessed++;

            long orderTotalSales = 0L;
            long orderTotalCost = 0L;
            long orderTotalProfitGross = 0L;
            long orderTotalProfitNet = 0L;

            if (pOrder.getItems() != null) {
                for (ParserDto.ParsedSalesItem pItem : pOrder.getItems()) {
                    itemsProcessed++;
                    String prodName = pItem.getProductName() != null ? pItem.getProductName().trim() : "Ismeretlen termék";
                    long saleGross = pItem.getSellingPriceGross() != null ? pItem.getSellingPriceGross() : 0L;
                    Long costGross = pItem.getCostPriceGross();
                    Long profitNet = pItem.getProfitNet();

                    InventoryItem matchedStockItem = null;

                    // If cost price is missing from Excel, automatically match with warehouse stock!
                    if (costGross == null || costGross <= 0) {
                        Optional<InventoryItem> inStockOpt = inventoryItemRepository.findFirstInStockMatchingProduct(prodName);
                        if (inStockOpt.isPresent()) {
                            matchedStockItem = inStockOpt.get();
                            costGross = matchedStockItem.getAcquisitionGrossPrice();

                            // Mark stock item as SOLD
                            matchedStockItem.setStatus(InventoryStatus.SOLD);
                            matchedStockItem.setSellingPriceGross(saleGross);
                            matchedStockItem.setSoldAt(Instant.now());
                            matchedStockItem.setNotes("Eladva a(z) " + orderNum + " sz. rendelésben (" + pOrder.getCustomerName() + ")");
                            inventoryItemRepository.save(matchedStockItem);

                            matchedWithStock++;
                            log.info("Matched order item '{}' with warehouse stock item #{} (Cost: {} Ft)",
                                    prodName, matchedStockItem.getId(), costGross);
                        } else {
                            costGross = 0L;
                            missingCost++;
                            notices.add(String.format("Rendelés %s: '%s' nem található a raktárkészletben!", orderNum, prodName));
                        }
                    }

                    long profitGross = saleGross - costGross;
                    if (profitNet == null) {
                        // Standard net profit calculation (Hungary 27% VAT assumption for standard margin, or gross-cost if KAFA)
                        profitNet = Math.round(profitGross / 1.27);
                    }

                    SaleItem saleItem = SaleItem.builder()
                            .salesOrder(savedOrder)
                            .inventoryItem(matchedStockItem)
                            .productName(prodName)
                            .quantity(pItem.getQuantity() != null ? pItem.getQuantity() : 1)
                            .sellingPriceGross(saleGross)
                            .costPriceGross(costGross)
                            .profitGross(profitGross)
                            .profitNet(profitNet)
                            .build();

                    saleItemRepository.save(saleItem);
                    savedOrder.getItems().add(saleItem);

                    orderTotalSales += saleGross;
                    orderTotalCost += costGross;
                    orderTotalProfitGross += profitGross;
                    orderTotalProfitNet += profitNet;
                }
            }

            savedOrder.setTotalSellingPriceGross(orderTotalSales);
            savedOrder.setTotalCostPriceGross(orderTotalCost);
            savedOrder.setTotalProfitGross(orderTotalProfitGross);
            savedOrder.setTotalProfitNet(orderTotalProfitNet);
            salesOrderRepository.save(savedOrder);

            totalSales += orderTotalSales;
            totalProfit += orderTotalProfitGross;
        }

        return SalesDto.BatchSalesImportResult.builder()
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
    public List<SalesDto.SalesOrderView> getOrders(LocalDate startDate, LocalDate endDate) {
        List<SalesOrder> orders;
        if (startDate != null && endDate != null) {
            orders = salesOrderRepository.findWithItemsBetweenDates(startDate, endDate);
        } else {
            orders = salesOrderRepository.findAllWithItems();
        }
        return orders.stream().map(this::mapToOrderView).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public SalesDto.ProfitSummary getProfitSummary(LocalDate startDate, LocalDate endDate) {
        LocalDate start = startDate != null ? startDate : LocalDate.now().minusMonths(1);
        LocalDate end = endDate != null ? endDate : LocalDate.now();

        List<SalesOrder> orders = salesOrderRepository.findWithItemsBetweenDates(start, end);

        long totalSales = 0L;
        long totalCost = 0L;
        long totalProfitGross = 0L;
        long totalProfitNet = 0L;
        int totalItemsCount = 0;

        for (SalesOrder o : orders) {
            totalSales += o.getTotalSellingPriceGross();
            totalCost += o.getTotalCostPriceGross();
            totalProfitGross += o.getTotalProfitGross();
            totalProfitNet += o.getTotalProfitNet();
            totalItemsCount += (o.getItems() != null ? o.getItems().size() : 0);
        }

        return SalesDto.ProfitSummary.builder()
                .startDate(start)
                .endDate(end)
                .totalSalesGross(totalSales)
                .totalCostGross(totalCost)
                .totalProfitGross(totalProfitGross)
                .totalProfitNet(totalProfitNet)
                .totalOrdersCount(orders.size())
                .totalItemsSold(totalItemsCount)
                .build();
    }

    @Transactional(readOnly = true)
    public String exportProfitCsv(LocalDate startDate, LocalDate endDate) {
        LocalDate start = startDate != null ? startDate : LocalDate.now().minusMonths(1);
        LocalDate end = endDate != null ? endDate : LocalDate.now();

        List<SalesOrder> orders = salesOrderRepository.findWithItemsBetweenDates(start, end);

        StringBuilder sb = new StringBuilder();
        // Exact column headers matching user's Excel
        sb.append("Sorszám\tDátum\tNév\tTermék\tEladási Ár br\tBeker Ár br\tProfit Br\tNetto Profit\n");

        for (SalesOrder order : orders) {
            for (SaleItem item : order.getItems()) {
                sb.append(order.getOrderNumber()).append("\t")
                        .append(order.getOrderDate() != null ? order.getOrderDate().toString() : "").append("\t")
                        .append(order.getCustomerName() != null ? order.getCustomerName() : "").append("\t")
                        .append(item.getProductName()).append("\t")
                        .append(item.getSellingPriceGross()).append("\t")
                        .append(item.getCostPriceGross() != null ? item.getCostPriceGross() : "").append("\t")
                        .append(item.getProfitGross() != null ? item.getProfitGross() : "").append("\t")
                        .append(item.getProfitNet() != null ? item.getProfitNet() : "").append("\n");
            }
        }

        return sb.toString();
    }

    private SalesDto.SalesOrderView mapToOrderView(SalesOrder o) {
        List<SalesDto.SaleItemView> itemViews = o.getItems().stream().map(it ->
                SalesDto.SaleItemView.builder()
                        .id(it.getId())
                        .salesOrderId(o.getId())
                        .inventoryItemId(it.getInventoryItem() != null ? it.getInventoryItem().getId() : null)
                        .productName(it.getProductName())
                        .quantity(it.getQuantity())
                        .sellingPriceGross(it.getSellingPriceGross())
                        .costPriceGross(it.getCostPriceGross())
                        .profitGross(it.getProfitGross())
                        .profitNet(it.getProfitNet())
                        .build()
        ).collect(Collectors.toList());

        return SalesDto.SalesOrderView.builder()
                .id(o.getId())
                .orderNumber(o.getOrderNumber())
                .orderDate(o.getOrderDate())
                .customerName(o.getCustomerName())
                .totalSellingPriceGross(o.getTotalSellingPriceGross())
                .totalCostPriceGross(o.getTotalCostPriceGross())
                .totalProfitGross(o.getTotalProfitGross())
                .totalProfitNet(o.getTotalProfitNet())
                .source(o.getSource())
                .items(itemViews)
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
