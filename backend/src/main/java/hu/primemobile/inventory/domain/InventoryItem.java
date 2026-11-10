package hu.primemobile.inventory.domain;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "inventory_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryItem {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @Column(name = "line_index", nullable = false)
    @Builder.Default
    private Integer lineIndex = 1;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "raw_description", columnDefinition = "TEXT")
    private String rawDescription;

    @Column(name = "data_wipe_codes", columnDefinition = "TEXT")
    private String dataWipeCodes;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String unit = "db";

    @Column(name = "initial_quantity", nullable = false)
    @Builder.Default
    private Integer initialQuantity = 1;

    @Column(name = "received_quantity", nullable = false)
    @Builder.Default
    private Integer receivedQuantity = 0;

    @Column(name = "sold_quantity", nullable = false)
    @Builder.Default
    private Integer soldQuantity = 0;

    @Column(name = "current_quantity", nullable = false)
    @Builder.Default
    private Integer currentQuantity = 0;

    @Column(name = "unit_price_net", nullable = false)
    @Builder.Default
    private Long unitPriceNet = 0L;

    @Column(name = "vat_rate", nullable = false, length = 20)
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

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String status = "ORDERED"; // ORDERED, PARTIAL, RECEIVED

    @Column(name = "received_at")
    private Instant receivedAt;

    @Column(name = "expected_margin_percent")
    private Double expectedMarginPercent;

    @Column(name = "expected_sale_price")
    private Long expectedSalePrice;

    @Column(name = "is_fully_deducted", nullable = false)
    @Builder.Default
    private boolean fullyDeducted = false;

    public List<String> getWipeCodesList() {
        if (this.dataWipeCodes == null || this.dataWipeCodes.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return MAPPER.readValue(this.dataWipeCodes, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public void setWipeCodesList(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            this.dataWipeCodes = "[]";
            return;
        }
        try {
            this.dataWipeCodes = MAPPER.writeValueAsString(codes);
        } catch (Exception e) {
            this.dataWipeCodes = "[]";
        }
    }

    public Long getUnitPriceGross() {
        if (this.initialQuantity != null && this.initialQuantity > 0 && this.totalGross != null) {
            return this.totalGross / this.initialQuantity;
        }
        return this.unitPriceNet != null ? this.unitPriceNet : 0L;
    }

    public int getPendingQuantity() {
        int init = this.initialQuantity != null ? this.initialQuantity : 0;
        int rec = this.receivedQuantity != null ? this.receivedQuantity : 0;
        return Math.max(0, init - rec);
    }
}
