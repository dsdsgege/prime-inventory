package hu.primemobile.inventory.repository;

import hu.primemobile.inventory.domain.SaleItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SaleItemRepository extends JpaRepository<SaleItem, Long> {
    List<SaleItem> findBySalesOrderId(Long salesOrderId);
}
