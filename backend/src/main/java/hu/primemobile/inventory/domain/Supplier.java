package hu.primemobile.inventory.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "suppliers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Supplier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "tax_number", length = 50)
    private String taxNumber;

    @Column(name = "eu_tax_number", length = 50)
    private String euTaxNumber;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(name = "bank_account", length = 100)
    private String bankAccount;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
