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

    Optional<SubscriptionOrder> findFirstByOwnerIdAndPlanIdAndStatusAndCreatedAtAfterOrderByCreatedAtDesc(
            Long ownerId, Long planId, String status, java.time.Instant createdAfter);

    /** Row order: plan_id, plan_name, currency, paid_orders, revenue_minor. */
    @org.springframework.data.jpa.repository.Query(value = """
            SELECT o.plan_id, p.name, o.currency, COUNT(*), SUM(o.amount_minor)
            FROM subscription_orders o
            JOIN subscription_plans p ON p.id = o.plan_id
            WHERE o.status = 'PAID'
            GROUP BY o.plan_id, p.name, o.currency
            ORDER BY SUM(o.amount_minor) DESC, p.name
            """, nativeQuery = true)
    List<Object[]> findPaidRevenueByPlan();
}
