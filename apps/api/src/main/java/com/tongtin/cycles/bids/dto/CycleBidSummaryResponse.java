package com.tongtin.cycles.bids.dto;

import java.time.Instant;
import java.util.List;

public record CycleBidSummaryResponse(
        Long cycleId,
        int cycleNo,
        String status,
        String currency,
        Instant bidCloseAt,
        boolean sealed,
        Long winnerShareId,
        Long winningBid,
        Long grossPot,
        Long hostFee,
        Long netPayout,
        Instant calculatedAt,
        List<Entry> entries) {

    public record Entry(
            Long shareId,
            int shareNo,
            Long memberProfileId,
            String shareStatus,
            boolean bidSubmitted,
            Long amountMinor,
            Instant submittedAt) {
    }
}
