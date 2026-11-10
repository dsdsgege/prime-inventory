package hu.primemobile.inventory.repository;

import hu.primemobile.inventory.domain.SalesOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface SalesOrderRepository extends JpaRepository<SalesOrder, Long> {
    Optional<SalesOrder> findByOrderNumber(String orderNumber);
    List<SalesOrder> findByOrderDateBetweenOrderByOrderDateDesc(LocalDate startDate, LocalDate endDate);

    @Query("SELECT o FROM SalesOrder o LEFT JOIN FETCH o.items ORDER BY o.orderDate DESC, o.id DESC")
    List<SalesOrder> findAllWithItems();

    @Query("SELECT o FROM SalesOrder o LEFT JOIN FETCH o.items WHERE o.orderDate BETWEEN :startDate AND :endDate ORDER BY o.orderDate DESC")
    List<SalesOrder> findWithItemsBetweenDates(LocalDate startDate, LocalDate endDate);
}
