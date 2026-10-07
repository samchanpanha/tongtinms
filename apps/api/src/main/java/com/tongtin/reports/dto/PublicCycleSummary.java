package com.tongtin.reports.dto;

import com.tongtin.common.money.Money;
import java.time.Instant;

/**
 * Public cycle summary visible to members (01-DOMAIN §14 = grossPot, winningBid B,
 * netPayout, winner name). Host fee and host profit are HOST-ONLY and never
 * appear here.
 */
public record PublicCycleSummary(
        int cycleNo,
        String status,
        Instant openAt,
        Instant bidCloseAt,
        Instant dueAt,
        Winner winner,
        Money winningBid,
        Money grossPot,
        Money netPayout) {

    public record Winner(int shareNo, String memberName) {
    }
}