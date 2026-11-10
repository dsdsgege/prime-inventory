package hu.primemobile.inventory.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "invoices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @Column(name = "invoice_number", nullable = false, unique = true, length = 100)
    private String invoiceNumber;

    @Column(name = "issue_date")
    private LocalDate issueDate;

    @Column(name = "delivery_date")
    private LocalDate deliveryDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "payment_method", length = 50)
    private String paymentMethod;

    @Column(name = "total_net_amount")
    private Long totalNetAmount;

    @Column(name = "total_vat_amount")
    private Long totalVatAmount;

    @Column(name = "total_gross_amount")
    private Long totalGrossAmount;

    @Column(name = "pdf_filename", nullable = false)
    private String pdfFilename;

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant uploadedAt = Instant.now();

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<InventoryItem> items = new ArrayList<>();
}
