package hu.primemobile.inventory.repository;

import hu.primemobile.inventory.domain.InventoryItem;
import hu.primemobile.inventory.domain.InventoryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryItemRepository extends JpaRepository<InventoryItem, Long> {
    List<InventoryItem> findByStatusOrderByReceivedAtDesc(InventoryStatus status);

    @Query("SELECT i FROM InventoryItem i WHERE i.status = :status AND LOWER(i.productName) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<InventoryItem> searchByStatusAndProductName(InventoryStatus status, String query);

    @Query(value = "SELECT * FROM inventory_items WHERE status = 'IN_STOCK' AND LOWER(product_name) LIKE LOWER(CONCAT('%', :productName, '%')) ORDER BY received_at ASC LIMIT 1", nativeQuery = true)
    Optional<InventoryItem> findFirstInStockMatchingProduct(String productName);

    long countByStatus(InventoryStatus status);
}
