package hu.primemobile.inventory.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "sales_orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalesOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_number", nullable = false, length = 100)
    private String orderNumber;

    @Column(name = "order_date")
    private LocalDate orderDate;

    @Column(name = "customer_name")
    private String customerName;

    @Column(name = "total_selling_price_gross", nullable = false)
    @Builder.Default
    private Long totalSellingPriceGross = 0L;

    @Column(name = "total_cost_price_gross", nullable = false)
    @Builder.Default
    private Long totalCostPriceGross = 0L;

    @Column(name = "total_profit_gross", nullable = false)
    @Builder.Default
    private Long totalProfitGross = 0L;

    @Column(name = "total_profit_net", nullable = false)
    @Builder.Default
    private Long totalProfitNet = 0L;

    @Column(nullable = false, length = 50)
    @Builder.Default
    private String source = "MANUAL"; // 'EXCEL_IMPORT', 'MANUAL_POS'

    @OneToMany(mappedBy = "salesOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<SaleItem> items = new ArrayList<>();

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
