package hu.primemobile.inventory.repository;

import hu.primemobile.inventory.domain.Invoice;
import hu.primemobile.inventory.domain.InvoiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);
    boolean existsByInvoiceNumber(String invoiceNumber);
    List<Invoice> findByStatusOrderByIssueDateDesc(InvoiceStatus status);

    @Query("SELECT i FROM Invoice i LEFT JOIN FETCH i.items LEFT JOIN FETCH i.supplier ORDER BY i.issueDate DESC")
    List<Invoice> findAllWithItems();

    @Query("SELECT i FROM Invoice i LEFT JOIN FETCH i.items LEFT JOIN FETCH i.supplier WHERE i.id = :id")
    Optional<Invoice> findByIdWithItems(Long id);
}
