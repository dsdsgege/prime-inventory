package hu.primemobile.inventory.repository;

import hu.primemobile.inventory.domain.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SupplierRepository extends JpaRepository<Supplier, Long> {
    Optional<Supplier> findByNameIgnoreCase(String name);
    Optional<Supplier> findByTaxNumber(String taxNumber);
}
