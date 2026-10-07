package com.tongtin.subscription.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record PlanCreateUpdateRequest(
        @NotBlank String code,
        @NotBlank String name,
        String description,
        @NotNull @PositiveOrZero Long priceMinor,
        @NotBlank String currency,
        @NotNull @Positive Integer durationMonths,
        Integer maxGroups,
        Integer maxMembers,
        String featuresJson,
        String badge,
        Integer sortOrder,
        Boolean isActive
) {}
