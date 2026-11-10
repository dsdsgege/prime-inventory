package hu.primemobile.inventory.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "invoice_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @Column(name = "line_index", nullable = false)
    @Builder.Default
    private Integer lineIndex = 1;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "raw_description", columnDefinition = "TEXT")
    private String rawDescription;

    @Column(length = 20, nullable = false)
    @Builder.Default
    private String unit = "db";

    @Column(name = "initial_quantity", nullable = false)
    @Builder.Default
    private Integer initialQuantity = 1;

    @Column(name = "current_quantity", nullable = false)
    @Builder.Default
    private Integer currentQuantity = 1;

    @Column(name = "unit_price_net", nullable = false)
    @Builder.Default
    private Long unitPriceNet = 0L;

    @Column(name = "vat_rate", length = 50, nullable = false)
    @Builder.Default
    private String vatRate = "K.AFA";

    @Column(name = "total_net", nullable = false)
    @Builder.Default
    private Long totalNet = 0L;

    @Column(name = "total_vat", nullable = false)
    @Builder.Default
    private Long totalVat = 0L;

    @Column(name = "total_gross", nullable = false)
    @Builder.Default
    private Long totalGross = 0L;

    @Column(name = "is_fully_deducted", nullable = false)
    @Builder.Default
    private boolean fullyDeducted = false;

    @OneToMany(mappedBy = "invoiceItem", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<NmhhWipeCode> wipeCodes = new ArrayList<>();

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
