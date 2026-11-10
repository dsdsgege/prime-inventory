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
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    @Column(name = "invoice_number", nullable = false, unique = true, length = 100)
    private String invoiceNumber;

    @Column(name = "issue_date")
    private LocalDate issueDate;

    @Column(name = "delivery_date")
    private LocalDate deliveryDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "payment_method", length = 100)
    private String paymentMethod;

    @Column(name = "total_net_amount", nullable = false)
    @Builder.Default
    private Long totalNetAmount = 0L;

    @Column(name = "total_vat_amount", nullable = false)
    @Builder.Default
    private Long totalVatAmount = 0L;

    @Column(name = "total_gross_amount", nullable = false)
    @Builder.Default
    private Long totalGrossAmount = 0L;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private InvoiceStatus status = InvoiceStatus.ACTIVE;

    @Column(name = "raw_pdf_path", length = 500)
    private String rawPdfPath;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<InvoiceItem> items = new ArrayList<>();

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
