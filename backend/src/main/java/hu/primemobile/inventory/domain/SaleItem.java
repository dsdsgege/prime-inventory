package hu.primemobile.inventory.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "sale_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sales_order_id")
    private SalesOrder salesOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventory_item_id")
    private InventoryItem inventoryItem;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(nullable = false)
    @Builder.Default
    private Integer quantity = 1;

    @Column(name = "selling_price_gross", nullable = false)
    @Builder.Default
    private Long sellingPriceGross = 0L;

    @Column(name = "cost_price_gross", nullable = false)
    @Builder.Default
    private Long costPriceGross = 0L;

    @Column(name = "profit_gross", nullable = false)
    @Builder.Default
    private Long profitGross = 0L;

    @Column(name = "profit_net", nullable = false)
    @Builder.Default
    private Long profitNet = 0L;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
