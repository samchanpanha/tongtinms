package com.tongtin.cycles.bids.dto;

import jakarta.validation.constraints.NotNull;

public record BidSubmitRequest(
        @NotNull Long shareId,
        @NotNull Long amountMinor) {
}