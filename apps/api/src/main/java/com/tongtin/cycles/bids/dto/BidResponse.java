package com.tongtin.cycles.bids.dto;

import com.tongtin.cycles.bids.entity.Bid;
import java.time.Instant;

public record BidResponse(
        Long id,
        Long cycleId,
        Long shareId,
        long amountMinor,
        String currency,
        Instant submittedAt,
        boolean latest) {

    public static BidResponse from(Bid bid) {
        return new BidResponse(
                bid.getId(),
                bid.getCycleId(),
                bid.getShareId(),
                bid.getAmountMinor(),
                bid.getCurrency(),
                bid.getSubmittedAt(),
                bid.isLatest());
    }
}