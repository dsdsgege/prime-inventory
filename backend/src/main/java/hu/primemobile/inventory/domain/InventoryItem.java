package hu.primemobile.inventory.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "inventory_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_item_id")
    private InvoiceItem invoiceItem;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "serial_number", length = 100)
    private String serialNumber;

    @Column(name = "wipe_code", length = 100)
    private String wipeCode;

    @Column(name = "acquisition_net_price", nullable = false)
    @Builder.Default
    private Long acquisitionNetPrice = 0L;

    @Column(name = "acquisition_gross_price", nullable = false)
    @Builder.Default
    private Long acquisitionGrossPrice = 0L;

    @Column(name = "received_at")
    @Builder.Default
    private Instant receivedAt = Instant.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private InventoryStatus status = InventoryStatus.IN_STOCK;

    @Column(name = "selling_price_net")
    private Long sellingPriceNet;

    @Column(name = "selling_price_gross")
    private Long sellingPriceGross;

    @Column(name = "sold_at")
    private Instant soldAt;

    @Column(columnDefinition = "TEXT")
    private String notes;

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
