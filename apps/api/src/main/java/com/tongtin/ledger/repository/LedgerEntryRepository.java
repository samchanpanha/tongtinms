package com.tongtin.ledger.repository;

import com.tongtin.ledger.entity.LedgerEntry;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    List<LedgerEntry> findByCycleIdOrderByShareId(Long cycleId);

    List<LedgerEntry> findByGroupIdOrderByCreatedAtAscIdAsc(Long groupId);

    List<LedgerEntry> findByGroupIdAndShareIdInOrderByCreatedAtAscIdAsc(Long groupId,
                                                                        Collection<Long> shareIds);

    Optional<LedgerEntry> findByCycleIdAndShareIdAndType(Long cycleId, Long shareId, String type);

    List<LedgerEntry> findByGroupIdAndIdIn(Long groupId, Collection<Long> ids);

    @Query("""
            SELECT e FROM LedgerEntry e
            WHERE e.groupId = :groupId AND e.dueAt < :now
              AND e.status IN ('UNPAID', 'PARTIAL')
            ORDER BY e.dueAt
            """)
    List<LedgerEntry> findOverdueByGroup(@Param("groupId") Long groupId, @Param("now") java.time.Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM LedgerEntry e WHERE e.id = :id")
    Optional<LedgerEntry> findByIdForUpdate(@Param("id") Long id);

    long countByCycleId(Long cycleId);

    long countByGroupIdAndStatusIn(Long groupId, Collection<String> statuses);

    long countByGroupIdAndStatusInAndDueAtLessThan(Long groupId, Collection<String> statuses, java.time.Instant now);

    Optional<LedgerEntry> findFirstByGroupIdAndStatusInOrderByDueAtAsc(Long groupId, Collection<String> statuses);

    @Query("""
            SELECT COALESCE(SUM(e.amountMinor), 0)
            FROM LedgerEntry e WHERE e.cycleId = :cycleId AND e.direction = :direction
            """)
    long sumAmountByCycleAndDirection(@Param("cycleId") Long cycleId, @Param("direction") String direction);

    @Modifying
    @Query("""
            UPDATE LedgerEntry e SET e.status = 'PAID'
            WHERE e.cycleId = :cycleId AND e.type IN ('PAYOUT', 'HOST_FEE')
            """)
    void markCyclePayoutPaid(@Param("cycleId") Long cycleId);

    @Query("""
            SELECT COALESCE(SUM(e.amountMinor), 0)
            FROM LedgerEntry e
            WHERE e.groupId = :groupId AND e.shareId IN :shareIds
              AND e.type IN :types AND e.status = :status
            """)
    long sumAmountByGroupAndShareInAndTypeInAndStatus(@Param("groupId") Long groupId,
                                                      @Param("shareIds") Collection<Long> shareIds,
                                                      @Param("types") Collection<String> types,
                                                      @Param("status") String status);
}
