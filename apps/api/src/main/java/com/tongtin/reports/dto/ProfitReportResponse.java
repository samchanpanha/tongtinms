package com.tongtin.reports.dto;

import com.tongtin.common.money.Money;
import java.time.Instant;
import java.util.List;

/**
 * Host profit report per group: one line per cycle plus group totals.
 * winner/amount fields are null until a cycle is calculated. Host-only;
 * impacts the member-facing public summary (which must NEVER expose hostFee).
 */
public record ProfitReportResponse(
        Long groupId,
        String groupName,
        String currency,
        int formulaVersion,
        List<CycleLine> cycles,
        Totals totals) {

    public record CycleLine(
            int cycleNo,
            String status,
            Instant openedAt,
            Instant closedAt,
            Winner winner,
            Money winningBid,
            Money grossPot,
            Money hostFee,
            Money netPayout) {

        public record Winner(Long shareId, int shareNo, String memberName) {
        }
    }

    public record Totals(
            Money grossPot,
            Money hostFee,
            Money netPayout,
            Money settledHostFee) {
    }
}