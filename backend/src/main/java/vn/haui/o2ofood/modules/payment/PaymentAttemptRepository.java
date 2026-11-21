package vn.haui.o2ofood.modules.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttemptEntity, Long> {
    Optional<PaymentAttemptEntity> findByTxnRef(String txnRef);
    List<PaymentAttemptEntity> findByOrderIdOrderByIdDesc(Long orderId);
}
