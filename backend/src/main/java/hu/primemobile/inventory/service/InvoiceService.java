package hu.primemobile.inventory.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import hu.primemobile.inventory.domain.*;
import hu.primemobile.inventory.dto.AppDtos;
import hu.primemobile.inventory.dto.ParserDto;
import hu.primemobile.inventory.repository.InventoryItemRepository;
import hu.primemobile.inventory.repository.InvoiceRepository;
import hu.primemobile.inventory.repository.SupplierRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ParserClient parserClient;
    private final SupplierRepository supplierRepository;
    private final InvoiceRepository invoiceRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final TransactionLogRepository transactionLogRepository;

    @Transactional(readOnly = true)
    public List<AppDtos.SupplierSummary> getAllSuppliersWithSummary() {
        log.debug("Fetching all suppliers with stock summary");
        List<Supplier> suppliers = supplierRepository.findAllByOrderByNameAsc();
        List<AppDtos.SupplierSummary> result = new ArrayList<>();

        for (Supplier s : suppliers) {
            long invCount = invoiceRepository.countBySupplierId(s.getId());
            long stockSum = inventoryItemRepository.sumCurrentQuantityBySupplierId(s.getId());
            long orderedSum = inventoryItemRepository.sumInitialQuantityBySupplierId(s.getId());
            long receivedSum = inventoryItemRepository.sumReceivedQuantityBySupplierId(s.getId());

            result.add(AppDtos.SupplierSummary.builder()
                    .id(s.getId())
                    .name(s.getName())
                    .taxNumber(s.getTaxNumber())
                    .euTaxNumber(s.getEuTaxNumber())
                    .address(s.getAddress())
                    .bankAccount(s.getBankAccount())
                    .createdAt(s.getCreatedAt())
                    .invoiceCount((int) invCount)
                    .totalInStockQty((int) stockSum)
                    .totalOrderedQty((int) orderedSum)
                    .totalReceivedQty((int) receivedSum)
                    .build());
        }
        return result;
    }

    @Transactional(readOnly = true)
    public Page<AppDtos.InvoiceSummary> getInvoicesPage(
            Long supplierId,
            String status,
            String search,
            Pageable pageable
    ) {
        log.debug("Querying invoices page: supplierId={}, status={}, search={}, page={}",
                supplierId, status, search, pageable);

        Specification<Invoice> spec = buildInvoiceSpec(supplierId, status, search);
        Pageable sortedPageable = pageable.getSort().isSorted()
                ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "issueDate").and(Sort.by(Sort.Direction.DESC, "uploadedAt")));

        return invoiceRepository.findAll(spec, sortedPageable).map(this::mapToSummary);
    }

    @Transactional(readOnly = true)
    public List<AppDtos.InvoiceSummary> getAllInvoicesWithSummary(
            Long supplierId,
            String status,
            String search
    ) {
        log.debug("Querying all invoices with summary: supplierId={}, status={}, search={}",
                supplierId, status, search);

        Specification<Invoice> spec = buildInvoiceSpec(supplierId, status, search);
        Sort sort = Sort.by(Sort.Direction.DESC, "issueDate").and(Sort.by(Sort.Direction.DESC, "uploadedAt"));
        return invoiceRepository.findAll(spec, sort).stream()
                .map(this::mapToSummary)
                .toList();
    }

    private Specification<Invoice> buildInvoiceSpec(Long supplierId, String status, String search) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            Join<Invoice, Supplier> supplierJoin = root.join("supplier", JoinType.LEFT);

            if (supplierId != null && supplierId > 0) {
                predicates.add(cb.equal(supplierJoin.get("id"), supplierId));
            }
            if (status != null && !status.isBlank() && !status.equalsIgnoreCase("ALL")) {
                predicates.add(cb.equal(root.get("status"), status.trim()));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("invoiceNumber")), pattern),
                        cb.like(cb.lower(supplierJoin.get("name")), pattern)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    @Transactional
    public AppDtos.UploadSuccessResponse uploadAndProcessInvoice(MultipartFile file) {
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "invoice.pdf";
        if (!filename.toLowerCase().endsWith(".pdf")) {
            throw new IllegalArgumentException("Csak PDF formátumú számla tölthető fel.");
        }

        log.info("Uploading and processing invoice PDF: {}", filename);
        ParserDto.ParsedInvoiceResponse parsed = parserClient.parseInvoicePdf(file);

        if (parsed == null || parsed.getInvoice() == null || parsed.getInvoice().getInvoiceNumber() == null) {
            throw new IllegalArgumentException("A számla sorszáma nem található a feltöltött PDF-ben.");
        }

        String invNum = parsed.getInvoice().getInvoiceNumber().trim();
        List<ParserDto.ParsedInvoiceItem> parsedItems = parsed.getItems() != null ? parsed.getItems() : List.of();
        List<String> filteredServices = parsed.getFilteredServices() != null ? parsed.getFilteredServices() : List.of();
        String parsedSupplierName = (parsed.getSupplier() != null && parsed.getSupplier().getName() != null)
                ? parsed.getSupplier().getName().trim()
                : "Ismeretlen Beszállító";

        // If the invoice contains ONLY expenses/services (0 physical inventory items), do NOT save to DB!
        if (parsedItems.isEmpty() && !filteredServices.isEmpty()) {
            String expenseMsg = String.format(
                    "A(z) %s (%s) számla kizárólag általános költség- vagy szolgáltatási tételeket tartalmaz (%d tétel), " +
                    "fizikai raktári terméket nem, ezért nem került rögzítésre a készletnyilvántartó adatbázisban.",
                    invNum, parsedSupplierName, filteredServices.size()
            );
            log.info("Expense-only invoice {} ({}) skipped DB persistence: {} expense rows detected",
                    invNum, parsedSupplierName, filteredServices.size());

            return AppDtos.UploadSuccessResponse.builder()
                    .message(expenseMsg)
                    .invoiceId(null)
                    .invoiceNumber(invNum)
                    .supplierName(parsedSupplierName)
                    .itemCount(0)
                    .unitCount(0)
                    .expenseOnly(true)
                    .filteredServices(filteredServices)
                    .build();
        }

        if (invoiceRepository.existsByInvoiceNumber(invNum)) {
            log.warn("Duplicate invoice upload rejected: {}", invNum);
            throw new IllegalStateException("A(z) '" + invNum + "' sorszámú számla már korábban rögzítésre került!");
        }

        // 1. Get or create Supplier
        Supplier supplier = getOrCreateSupplier(parsed.getSupplier());

        // 2. Save Invoice
        String safeFilename = invNum.replace("/", "_") + ".pdf";
        ParserDto.ParsedInvoice invData = parsed.getInvoice();
        Invoice invoice = Invoice.builder()
                .supplier(supplier)
                .invoiceNumber(invNum)
                .issueDate(parseDateSafe(invData.getIssueDate()))
                .deliveryDate(parseDateSafe(invData.getDeliveryDate()))
                .dueDate(parseDateSafe(invData.getDueDate()))
                .paymentMethod(invData.getPaymentMethod())
                .totalNetAmount(invData.getTotalNetAmount())
                .totalVatAmount(invData.getTotalVatAmount())
                .totalGrossAmount(invData.getTotalGrossAmount())
                .pdfFilename(safeFilename)
                .status("ACTIVE")
                .uploadedAt(Instant.now())
                .items(new ArrayList<>())
                .build();

        Invoice savedInvoice = invoiceRepository.save(invoice);

        // 3. Save InventoryItems & INITIAL_IMPORT TransactionLogs
        int totalUnits = 0;

        for (ParserDto.ParsedInvoiceItem itemData : parsedItems) {
            int qty = itemData.getInitialQuantity() != null && itemData.getInitialQuantity() > 0
                    ? itemData.getInitialQuantity() : 1;
            totalUnits += qty;

            String wipeCodesJson = "[]";
            try {
                if (itemData.getWipeCodes() != null && !itemData.getWipeCodes().isEmpty()) {
                    wipeCodesJson = MAPPER.writeValueAsString(itemData.getWipeCodes());
                }
            } catch (Exception e) {
                log.warn("Could not serialize wipe codes for product {}", itemData.getProductName(), e);
            }

            InventoryItem item = InventoryItem.builder()
                    .invoice(savedInvoice)
                    .supplier(supplier)
                    .lineIndex(itemData.getLineIndex() != null ? itemData.getLineIndex() : 1)
                    .productName(itemData.getProductName() != null ? itemData.getProductName() : "Ismeretlen termék")
                    .rawDescription(itemData.getRawDescription())
                    .dataWipeCodes(wipeCodesJson)
                    .unit(itemData.getUnit() != null ? itemData.getUnit() : "db")
                    .initialQuantity(qty)
                    .receivedQuantity(0)
                    .soldQuantity(0)
                    .currentQuantity(0)
                    .unitPriceNet(itemData.getUnitPriceNet() != null ? itemData.getUnitPriceNet() : 0L)
                    .vatRate(itemData.getVatRate() != null ? itemData.getVatRate() : "K.AFA")
                    .totalNet(itemData.getTotalNet() != null ? itemData.getTotalNet() : 0L)
                    .totalVat(itemData.getTotalVat() != null ? itemData.getTotalVat() : 0L)
                    .totalGross(itemData.getTotalGross() != null ? itemData.getTotalGross() : 0L)
                    .status("ORDERED")
                    .receivedAt(null)
                    .fullyDeducted(false)
                    .build();

            InventoryItem savedItem = inventoryItemRepository.save(item);
            savedInvoice.getItems().add(savedItem);

            TransactionLog txLog = TransactionLog.builder()
                    .item(savedItem)
                    .invoice(savedInvoice)
                    .supplier(supplier)
                    .actionType("INITIAL_IMPORT")
                    .quantityChanged(qty)
                    .quantityBefore(0)
                    .quantityAfter(0)
                    .note("Kezdeti számla import (beérkezésre vár)")
                    .createdAt(Instant.now())
                    .build();
            transactionLogRepository.save(txLog);
        }

        String filterMsg = "";
        if (!filteredServices.isEmpty()) {
            filterMsg = String.format(" (%d költség/szolgáltatás tétel kiszűrve: %s)",
                    filteredServices.size(), String.join(", ", filteredServices));
        }

        String msg = String.format(
                "Számla sikeresen feldolgozva: %s (%s) – %d tétel (%d db készülék) rögzítve beérkezésre váró állapotban.%s",
                savedInvoice.getInvoiceNumber(),
                supplier.getName(),
                parsedItems.size(),
                totalUnits,
                filterMsg
        );

        log.info("Invoice {} processed: {} items, {} units, supplier={}",
                savedInvoice.getInvoiceNumber(), parsedItems.size(), totalUnits, supplier.getName());

        return AppDtos.UploadSuccessResponse.builder()
                .message(msg)
                .invoiceId(savedInvoice.getId())
                .invoiceNumber(savedInvoice.getInvoiceNumber())
                .supplierName(supplier.getName())
                .itemCount(parsedItems.size())
                .unitCount(totalUnits)
                .expenseOnly(false)
                .filteredServices(filteredServices)
                .build();
    }

    private Supplier getOrCreateSupplier(ParserDto.ParsedSupplier supplierData) {
        if (supplierData == null) {
            return supplierRepository.findByNameIgnoreCase("Ismeretlen Beszállító")
                    .orElseGet(() -> supplierRepository.save(Supplier.builder()
                            .name("Ismeretlen Beszállító")
                            .taxNumber("UNKNOWN-" + System.currentTimeMillis())
                            .build()));
        }

        String tax = supplierData.getTaxNumber() != null ? supplierData.getTaxNumber().trim() : "";
        String name = supplierData.getName() != null && !supplierData.getName().isBlank()
                ? supplierData.getName().trim() : "Ismeretlen Beszállító";

        Optional<Supplier> existingOpt = Optional.empty();
        if (!tax.isEmpty()) {
            existingOpt = supplierRepository.findByTaxNumber(tax);
        }
        if (existingOpt.isEmpty()) {
            existingOpt = supplierRepository.findByNameIgnoreCase(name);
        }

        if (existingOpt.isPresent()) {
            Supplier existing = existingOpt.get();
            boolean updated = false;
            if ((existing.getAddress() == null || existing.getAddress().isBlank()) && supplierData.getAddress() != null) {
                existing.setAddress(supplierData.getAddress());
                updated = true;
            }
            if ((existing.getBankAccount() == null || existing.getBankAccount().isBlank()) && supplierData.getBankAccount() != null) {
                existing.setBankAccount(supplierData.getBankAccount());
                updated = true;
            }
            if ((existing.getEuTaxNumber() == null || existing.getEuTaxNumber().isBlank()) && supplierData.getEuTaxNumber() != null) {
                existing.setEuTaxNumber(supplierData.getEuTaxNumber());
                updated = true;
            }
            return updated ? supplierRepository.save(existing) : existing;
        }

        Supplier newSupplier = Supplier.builder()
                .name(name)
                .taxNumber(tax.isEmpty() ? "TAX-" + System.currentTimeMillis() : tax)
                .euTaxNumber(supplierData.getEuTaxNumber())
                .address(supplierData.getAddress())
                .bankAccount(supplierData.getBankAccount())
                .createdAt(Instant.now())
                .build();
        return supplierRepository.save(newSupplier);
    }

    private AppDtos.InvoiceSummary mapToSummary(Invoice inv) {
        int initialSum = 0;
        int receivedSum = 0;
        int currentSum = 0;
        if (inv.getItems() != null) {
            for (InventoryItem it : inv.getItems()) {
                initialSum += (it.getInitialQuantity() != null ? it.getInitialQuantity() : 0);
                receivedSum += (it.getReceivedQuantity() != null ? it.getReceivedQuantity() : 0);
                currentSum += (it.getCurrentQuantity() != null ? it.getCurrentQuantity() : 0);
            }
        }
        return AppDtos.InvoiceSummary.builder()
                .id(inv.getId())
                .supplierId(inv.getSupplier() != null ? inv.getSupplier().getId() : null)
                .supplierName(inv.getSupplier() != null ? inv.getSupplier().getName() : "")
                .invoiceNumber(inv.getInvoiceNumber())
                .issueDate(inv.getIssueDate())
                .deliveryDate(inv.getDeliveryDate())
                .dueDate(inv.getDueDate())
                .paymentMethod(inv.getPaymentMethod())
                .totalNetAmount(inv.getTotalNetAmount())
                .totalVatAmount(inv.getTotalVatAmount())
                .totalGrossAmount(inv.getTotalGrossAmount())
                .pdfFilename(inv.getPdfFilename())
                .status(inv.getStatus())
                .uploadedAt(inv.getUploadedAt())
                .totalInitialQuantity(initialSum)
                .totalReceivedQuantity(receivedSum)
                .totalCurrentQuantity(currentSum)
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
