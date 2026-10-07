package com.tongtin.subscription.repository;

import com.tongtin.subscription.entity.SubscriptionOrder;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SubscriptionOrderRepository extends JpaRepository<SubscriptionOrder, Long> {
    Optional<SubscriptionOrder> findByTranId(String tranId);
    List<SubscriptionOrder> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);
    List<SubscriptionOrder> findAllByOrderByCreatedAtDesc();
}
