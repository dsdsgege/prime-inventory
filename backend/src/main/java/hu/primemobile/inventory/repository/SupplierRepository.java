package hu.primemobile.inventory.repository;

import hu.primemobile.inventory.domain.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SupplierRepository extends JpaRepository<Supplier, Long> {
    Optional<Supplier> findByTaxNumber(String taxNumber);
    Optional<Supplier> findByNameIgnoreCase(String name);
    List<Supplier> findAllByOrderByNameAsc();
}
