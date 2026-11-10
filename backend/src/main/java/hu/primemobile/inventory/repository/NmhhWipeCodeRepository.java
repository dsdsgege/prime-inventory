package hu.primemobile.inventory.repository;

import hu.primemobile.inventory.domain.NmhhWipeCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NmhhWipeCodeRepository extends JpaRepository<NmhhWipeCode, Long> {
    Optional<NmhhWipeCode> findByWipeCode(String wipeCode);
    List<NmhhWipeCode> findByUsedFalse();
    List<NmhhWipeCode> findByInvoiceItemId(Long invoiceItemId);
}
