package com.tongtin.reports.dto;

import com.tongtin.common.money.Money;
import java.time.Instant;
import java.util.List;

/**
 * Host group ledger report: every append-only obligation of the group with its
 * paid/allocated amount. All amounts are the money shape; counters are scalar.
 */
public record LedgerReportResponse(
        Long groupId,
        String groupName,
        String currency,
        List<Entry> entries,
        Money totalIn,
        Money totalOut) {

    public record Entry(
            Long entryId,
            int cycleNo,
            String type,
            String direction,
            Integer shareNo,
            Long memberProfileId,
            String memberName,
            Money amount,
            String status,
            Instant dueAt,
            Money allocated,
            Money remaining) {
    }
}