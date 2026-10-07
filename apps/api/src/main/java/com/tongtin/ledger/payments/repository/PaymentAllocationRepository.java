package com.tongtin.ledger.payments.repository;

import com.tongtin.ledger.payments.entity.PaymentAllocation;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentAllocationRepository extends JpaRepository<PaymentAllocation, Long> {

    List<PaymentAllocation> findByPaymentId(Long paymentId);

    List<PaymentAllocation> findByPaymentIdOrderByLedgerEntryId(Long paymentId);

    @Query("""
            SELECT a.ledgerEntryId, COALESCE(SUM(a.amountMinor), 0)
            FROM PaymentAllocation a
            WHERE a.ledgerEntryId IN :entryIds
            GROUP BY a.ledgerEntryId
            """)
    List<Object[]> sumAllocatedByEntryIds(@Param("entryIds") Collection<Long> entryIds);

    @Query("""
            SELECT COALESCE(SUM(a.amountMinor), 0)
            FROM PaymentAllocation a
            WHERE a.ledgerEntryId IN (
                SELECT e.id FROM LedgerEntry e
                WHERE e.groupId = :groupId AND e.shareId IN :shareIds AND e.type IN :types)
            """)
    long sumAllocatedByGroupAndShareInAndTypeIn(@Param("groupId") Long groupId,
                                                @Param("shareIds") Collection<Long> shareIds,
                                                @Param("types") Collection<String> types);
}
