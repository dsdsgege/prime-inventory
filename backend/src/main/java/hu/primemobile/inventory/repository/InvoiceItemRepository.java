package hu.primemobile.inventory.repository;

import hu.primemobile.inventory.domain.InvoiceItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InvoiceItemRepository extends JpaRepository<InvoiceItem, Long> {
    List<InvoiceItem> findByInvoiceId(Long invoiceId);
    List<InvoiceItem> findByFullyDeductedFalse();
}
