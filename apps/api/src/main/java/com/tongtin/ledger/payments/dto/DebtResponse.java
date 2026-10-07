package com.tongtin.ledger.payments.dto;

import java.time.Instant;

public record DebtResponse(
        Long ledgerEntryId,
        Long cycleId,
        int cycleNo,
        Long shareId,
        Long memberProfileId,
        String type,
        String direction,
        long amountMinor,
        long allocatedMinor,
        long remainingMinor,
        String currency,
        String status,
        Instant dueAt,
        long overdueDays) {
}
