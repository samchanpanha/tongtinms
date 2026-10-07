package com.tongtin.groups.shares.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ShareAssignRequest(
        @NotNull Long memberProfileId,
        @Min(value = 1, message = "count must be >= 1") int count) {
}
