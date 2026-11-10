package hu.primemobile.inventory.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "sales")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Sale {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventory_item_id")
    private InventoryItem item;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id")
    private Invoice invoice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    @Column(name = "order_number", length = 100)
    private String orderNumber;

    @Column(nullable = false)
    @Builder.Default
    private Integer quantity = 1;

    @Column(name = "unit_cost_net", nullable = false)
    @Builder.Default
    private Long unitCostNet = 0L;

    @Column(name = "unit_cost_gross", nullable = false)
    @Builder.Default
    private Long unitCostGross = 0L;

    @Column(name = "sale_price", nullable = false)
    @Builder.Default
    private Long salePrice = 0L;

    @Column(nullable = false)
    @Builder.Default
    private Long profit = 0L;

    @Column(name = "profit_net", nullable = false)
    @Builder.Default
    private Long profitNet = 0L;

    @Column(name = "used_wipe_code", length = 100)
    private String usedWipeCode;

    @Column(name = "customer_name")
    private String customerName;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String source = "POS";

    @Column(name = "sold_at", nullable = false)
    @Builder.Default
    private Instant soldAt = Instant.now();

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
