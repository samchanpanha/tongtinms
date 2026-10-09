package com.tongtin.reports.dto;

import com.tongtin.common.money.Money;
import java.time.Instant;
import java.util.List;

/**
 * Member statement per group: full per-share transaction history (own shares
 * only) plus totals, using the 01-DOMAIN §9.5 balance definitions. runningBalance
 * is the money actually moved: contributions/fees subtract the allocated amount,
 * payouts add the full amount.
 */
public record MemberStatementResponse(
        Long groupId,
        String groupName,
        String currency,
        List<Share> shares,
        Total totals) {

    public record Share(
            Long shareId,
            int shareNo,
            String status,
            List<Entry> entries,
            Total totals) {
    }

    public record Entry(
            Long entryId,
            int cycleNo,
            String type,
            String direction,
            Money amount,
            String status,
            Instant dueAt,
            Money allocated,
            Money remaining,
            Money runningBalance,
            String khqr) {
    }

    public record Total(
            Money contributed,
            Money received,
            Money feesPaid,
            Money netPosition) {
    }
}