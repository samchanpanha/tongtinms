package com.tongtin.ledger.dto;

import java.time.Instant;
import java.util.List;

/**
 * Step 29: host-triggered late-fee assessment (01-DOMAIN 9.6).
 * assessed = overdue CONTRIBUTION obligations examined,
 * created  = LATE_FEE rows written (0 when already up to date),
 * entries  = the rows created in this call.
 */
public record LateFeeAssessResponse(
        int assessed,
        int created,
        List<Entry> entries) {

    public record Entry(
            Long ledgerEntryId,
            Long cycleId,
            Long shareId,
            long amountMinor,
            String currency,
            Instant dueAt) {
    }

    public static LateFeeAssessResponse empty() {
        return new LateFeeAssessResponse(0, 0, List.of());
    }
}
