package hu.primemobile.inventory.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "nmhh_wipe_codes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NmhhWipeCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_item_id")
    private InvoiceItem invoiceItem;

    @Column(name = "wipe_code", nullable = false, length = 100)
    private String wipeCode;

    @Column(name = "is_used", nullable = false)
    @Builder.Default
    private boolean used = false;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
