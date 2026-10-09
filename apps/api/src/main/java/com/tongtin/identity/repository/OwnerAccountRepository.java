package com.tongtin.identity.repository;

import com.tongtin.identity.entity.OwnerAccount;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OwnerAccountRepository extends JpaRepository<OwnerAccount, Long> {

    Optional<OwnerAccount> findByUserId(Long userId);

    List<OwnerAccount> findByTelegramChatIdIsNotNull();

    List<OwnerAccount> findBySubscriptionEndsAtBeforeAndSubscriptionStatusNotIn(
            Instant cutoff, Collection<String> excludedStatuses);

    /** Row order: cohort (YYYY-MM), registered, active_now, churned. */
    @Query(value = """
            SELECT to_char(date_trunc('month', created_at), 'YYYY-MM') AS cohort,
                   COUNT(*),
                   COUNT(*) FILTER (WHERE subscription_ends_at >= now()
                       OR subscription_status = 'LIFETIME'),
                   COUNT(*) FILTER (WHERE subscription_ends_at < now()
                       AND subscription_status <> 'LIFETIME')
            FROM owner_accounts
            GROUP BY 1
            ORDER BY 1 DESC
            """, nativeQuery = true)
    List<Object[]> findRegistrationCohortStats();
}
