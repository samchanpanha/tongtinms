package com.tongtin.ledger.payments.repository;

import com.tongtin.ledger.payments.entity.Payment;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByIdAndGroupId(Long id, Long groupId);

    Optional<Payment> findByGroupIdAndIdempotencyKey(Long groupId, String idempotencyKey);

    long countByGroupId(Long groupId);
}
