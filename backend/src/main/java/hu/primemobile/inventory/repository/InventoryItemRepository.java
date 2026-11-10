package hu.primemobile.inventory.repository;

import hu.primemobile.inventory.domain.InventoryItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InventoryItemRepository extends JpaRepository<InventoryItem, Long>, JpaSpecificationExecutor<InventoryItem> {

    @Query("SELECT COALESCE(SUM(i.currentQuantity), 0) FROM InventoryItem i WHERE i.supplier.id = :supplierId")
    long sumCurrentQuantityBySupplierId(@Param("supplierId") Long supplierId);

    @Query("SELECT COALESCE(SUM(i.initialQuantity), 0) FROM InventoryItem i WHERE i.supplier.id = :supplierId")
    long sumInitialQuantityBySupplierId(@Param("supplierId") Long supplierId);

    @Query("SELECT COALESCE(SUM(i.receivedQuantity), 0) FROM InventoryItem i WHERE i.supplier.id = :supplierId")
    long sumReceivedQuantityBySupplierId(@Param("supplierId") Long supplierId);

    @Query("SELECT i FROM InventoryItem i WHERE i.currentQuantity > 0 AND LOWER(i.productName) LIKE LOWER(CONCAT('%', :productName, '%')) ORDER BY i.receivedAt ASC NULLS LAST, i.id ASC")
    List<InventoryItem> findInStockMatchingProduct(@Param("productName") String productName);
}
