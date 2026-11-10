package hu.primemobile.inventory.service;

import hu.primemobile.inventory.domain.*;
import hu.primemobile.inventory.dto.InvoiceDto;
import hu.primemobile.inventory.dto.ParserDto;
import hu.primemobile.inventory.repository.InvoiceItemRepository;
import hu.primemobile.inventory.repository.InvoiceRepository;
import hu.primemobile.inventory.repository.NmhhWipeCodeRepository;
import hu.primemobile.inventory.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceService {

    private final ParserClient parserClient;
    private final SupplierRepository supplierRepository;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceItemRepository invoiceItemRepository;
    private final NmhhWipeCodeRepository wipeCodeRepository;

    @Transactional
    public InvoiceDto.InvoiceDetail uploadAndProcessInvoice(MultipartFile file) {
        ParserDto.ParsedInvoiceResponse parsed = parserClient.parseInvoicePdf(file);

        if (parsed.getInvoice() == null || parsed.getInvoice().getInvoiceNumber() == null) {
            throw new IllegalArgumentException("A számla sorszáma nem található a feltöltött PDF-ben.");
        }

        String invNum = parsed.getInvoice().getInvoiceNumber().trim();
        if (invoiceRepository.existsByInvoiceNumber(invNum)) {
            throw new IllegalStateException("Ez a számla már szerepel a rendszerben: " + invNum);
        }

        // 1. Supplier
        Supplier supplier = null;
        if (parsed.getSupplier() != null && parsed.getSupplier().getName() != null) {
            String suppName = parsed.getSupplier().getName().trim();
            supplier = supplierRepository.findByNameIgnoreCase(suppName)
                    .orElseGet(() -> supplierRepository.save(Supplier.builder()
                            .name(suppName)
                            .taxNumber(parsed.getSupplier().getTaxNumber())
                            .euTaxNumber(parsed.getSupplier().getEuTaxNumber())
                            .address(parsed.getSupplier().getAddress())
                            .bankAccount(parsed.getSupplier().getBankAccount())
                            .build()));
        }

        // 2. Invoice
        Invoice invoice = Invoice.builder()
                .supplier(supplier)
                .invoiceNumber(invNum)
                .issueDate(parseDateSafe(parsed.getInvoice().getIssueDate()))
                .deliveryDate(parseDateSafe(parsed.getInvoice().getDeliveryDate()))
                .dueDate(parseDateSafe(parsed.getInvoice().getDueDate()))
                .paymentMethod(parsed.getInvoice().getPaymentMethod())
                .totalNetAmount(parsed.getInvoice().getTotalNetAmount() != null ? parsed.getInvoice().getTotalNetAmount() : 0L)
                .totalVatAmount(parsed.getInvoice().getTotalVatAmount() != null ? parsed.getInvoice().getTotalVatAmount() : 0L)
                .totalGrossAmount(parsed.getInvoice().getTotalGrossAmount() != null ? parsed.getInvoice().getTotalGrossAmount() : 0L)
                .status(InvoiceStatus.ACTIVE)
                .rawPdfPath(file.getOriginalFilename())
                .items(new ArrayList<>())
                .build();

        Invoice savedInvoice = invoiceRepository.save(invoice);

        // 3. Line items & NMHH codes
        if (parsed.getItems() != null) {
            for (ParserDto.ParsedInvoiceItem parsedItem : parsed.getItems()) {
                InvoiceItem item = InvoiceItem.builder()
                        .invoice(savedInvoice)
                        .lineIndex(parsedItem.getLineIndex() != null ? parsedItem.getLineIndex() : 1)
                        .productName(parsedItem.getProductName() != null ? parsedItem.getProductName() : "Ismeretlen termék")
                        .rawDescription(parsedItem.getRawDescription())
                        .unit(parsedItem.getUnit() != null ? parsedItem.getUnit() : "db")
                        .initialQuantity(parsedItem.getInitialQuantity() != null ? parsedItem.getInitialQuantity() : 1)
                        .currentQuantity(parsedItem.getCurrentQuantity() != null ? parsedItem.getCurrentQuantity() : 1)
                        .unitPriceNet(parsedItem.getUnitPriceNet() != null ? parsedItem.getUnitPriceNet() : 0L)
                        .vatRate(parsedItem.getVatRate() != null ? parsedItem.getVatRate() : "K.AFA")
                        .totalNet(parsedItem.getTotalNet() != null ? parsedItem.getTotalNet() : 0L)
                        .totalVat(parsedItem.getTotalVat() != null ? parsedItem.getTotalVat() : 0L)
                        .totalGross(parsedItem.getTotalGross() != null ? parsedItem.getTotalGross() : 0L)
                        .fullyDeducted(false)
                        .wipeCodes(new ArrayList<>())
                        .build();

                InvoiceItem savedItem = invoiceItemRepository.save(item);

                if (parsedItem.getWipeCodes() != null) {
                    for (String code : parsedItem.getWipeCodes()) {
                        NmhhWipeCode wipeCode = NmhhWipeCode.builder()
                                .invoiceItem(savedItem)
                                .wipeCode(code.trim())
                                .used(false)
                                .build();
                        wipeCodeRepository.save(wipeCode);
                        savedItem.getWipeCodes().add(wipeCode);
                    }
                }
                savedInvoice.getItems().add(savedItem);
            }
        }

        log.info("Invoice {} saved successfully with {} items", savedInvoice.getInvoiceNumber(), savedInvoice.getItems().size());
        return mapToDetail(savedInvoice);
    }

    @Transactional(readOnly = true)
    public List<InvoiceDto.InvoiceSummary> getAllInvoices() {
        return invoiceRepository.findAllWithItems().stream()
                .map(this::mapToSummary)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public InvoiceDto.InvoiceDetail getInvoiceById(Long id) {
        Invoice invoice = invoiceRepository.findByIdWithItems(id)
                .orElseThrow(() -> new IllegalArgumentException("A számla nem található: " + id));
        return mapToDetail(invoice);
    }

    @Transactional
    public void cancelInvoice(Long id) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("A számla nem található: " + id));
        invoice.setStatus(InvoiceStatus.CANCELLED);
        invoiceRepository.save(invoice);
        log.info("Invoice {} marked as CANCELLED", invoice.getInvoiceNumber());
    }

    private InvoiceDto.InvoiceSummary mapToSummary(Invoice i) {
        return InvoiceDto.InvoiceSummary.builder()
                .id(i.getId())
                .invoiceNumber(i.getInvoiceNumber())
                .supplierName(i.getSupplier() != null ? i.getSupplier().getName() : "Ismeretlen")
                .issueDate(i.getIssueDate())
                .deliveryDate(i.getDeliveryDate())
                .dueDate(i.getDueDate())
                .paymentMethod(i.getPaymentMethod())
                .totalNetAmount(i.getTotalNetAmount())
                .totalVatAmount(i.getTotalVatAmount())
                .totalGrossAmount(i.getTotalGrossAmount())
                .status(i.getStatus().name())
                .itemsCount(i.getItems() != null ? i.getItems().size() : 0)
                .build();
    }

    private InvoiceDto.InvoiceDetail mapToDetail(Invoice i) {
        List<InvoiceDto.InvoiceItemDetail> itemDtos = i.getItems().stream().map(it ->
                InvoiceDto.InvoiceItemDetail.builder()
                        .id(it.getId())
                        .lineIndex(it.getLineIndex())
                        .productName(it.getProductName())
                        .rawDescription(it.getRawDescription())
                        .unit(it.getUnit())
                        .initialQuantity(it.getInitialQuantity())
                        .currentQuantity(it.getCurrentQuantity())
                        .unitPriceNet(it.getUnitPriceNet())
                        .vatRate(it.getVatRate())
                        .totalNet(it.getTotalNet())
                        .totalVat(it.getTotalVat())
                        .totalGross(it.getTotalGross())
                        .fullyDeducted(it.isFullyDeducted())
                        .wipeCodes(it.getWipeCodes().stream().map(NmhhWipeCode::getWipeCode).collect(Collectors.toList()))
                        .build()
        ).collect(Collectors.toList());

        InvoiceDto.SupplierDto suppDto = null;
        if (i.getSupplier() != null) {
            suppDto = InvoiceDto.SupplierDto.builder()
                    .id(i.getSupplier().getId())
                    .name(i.getSupplier().getName())
                    .taxNumber(i.getSupplier().getTaxNumber())
                    .euTaxNumber(i.getSupplier().getEuTaxNumber())
                    .address(i.getSupplier().getAddress())
                    .bankAccount(i.getSupplier().getBankAccount())
                    .build();
        }

        return InvoiceDto.InvoiceDetail.builder()
                .id(i.getId())
                .invoiceNumber(i.getInvoiceNumber())
                .supplier(suppDto)
                .issueDate(i.getIssueDate())
                .deliveryDate(i.getDeliveryDate())
                .dueDate(i.getDueDate())
                .paymentMethod(i.getPaymentMethod())
                .totalNetAmount(i.getTotalNetAmount())
                .totalVatAmount(i.getTotalVatAmount())
                .totalGrossAmount(i.getTotalGrossAmount())
                .status(i.getStatus().name())
                .items(itemDtos)
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
