package com.tongtin.memberportal.dto;

import java.util.List;

/**
 * Member balance statement per group (01-DOMAIN §9.5):
 * contributed = sum allocated toward CONTRIBUTION entries of own shares,
 * received    = sum of PAID PAYOUT entries,
 * feesPaid    = sum allocated toward LATE_FEE / OTHER_FEE,
 * netPosition = received - contributed - feesPaid (positive = still ahead).
 */
public record BalanceResponse(
        Long groupId,
        String groupName,
        String currency,
        long contributedMinor,
        long receivedMinor,
        long feesPaidMinor,
        long netPositionMinor,
        List<ShareBalance> shares) {

    public record ShareBalance(
            Long shareId,
            int shareNo,
            String status,
            Long wonCycleId,
            long contributedMinor,
            long receivedMinor,
            long feesPaidMinor,
            long netPositionMinor) {
    }
}