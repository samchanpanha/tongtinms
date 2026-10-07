package com.tongtin.cycles.dto;

import com.tongtin.cycles.entity.Cycle;
import java.time.Instant;

public record CycleResponse(
        Long id,
        Long groupId,
        int cycleNo,
        String status,
        String currency,
        Instant openAt,
        Instant bidCloseAt,
        Instant dueAt,
        Long winnerShareId,
        Long winningBid,
        Long grossPot,
        Long hostFee,
        Long netPayout,
        Instant calculatedAt) {

    public static CycleResponse from(Cycle cycle) {
        return new CycleResponse(
                cycle.getId(),
                cycle.getGroupId(),
                cycle.getCycleNo(),
                cycle.getStatus(),
                cycle.getCurrency(),
                cycle.getOpenAt(),
                cycle.getBidCloseAt(),
                cycle.getDueAt(),
                cycle.getWinnerShareId(),
                cycle.getWinningBid(),
                cycle.getGrossPot(),
                cycle.getHostFee(),
                cycle.getNetPayout(),
                cycle.getCalculatedAt());
    }
}