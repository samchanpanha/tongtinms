package com.tongtin.subscription.repository;

import com.tongtin.subscription.entity.PaymentEvent;
import java.util.List;
import org.springframework.data.repository.Repository;

/**
 * Append-only repository: only insert and read methods are declared, so no
 * update or delete path exists through Spring Data.
 */
public interface PaymentEventRepository extends Repository<PaymentEvent, Long> {

    PaymentEvent save(PaymentEvent event);

    List<PaymentEvent> findByTranIdOrderByCreatedAtDesc(String tranId);

    List<PaymentEvent> findAllByOrderByCreatedAtDesc();
}
