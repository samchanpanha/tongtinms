package com.tongtin.subscription.repository;

import com.tongtin.subscription.entity.SubscriptionPlan;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, Long> {
    List<SubscriptionPlan> findByIsActiveTrueOrderBySortOrderAsc();
    List<SubscriptionPlan> findAllByOrderBySortOrderAsc();
    Optional<SubscriptionPlan> findByCode(String code);
    boolean existsByCode(String code);
}
