package com.tongtin.subscription.dto;

import com.tongtin.subscription.entity.SubscriptionPlan;

public record SubscriptionPlanDto(
        Long id,
        String code,
        String name,
        String description,
        Long priceMinor,
        String currency,
        Integer durationMonths,
        Integer maxGroups,
        Integer maxMembers,
        String featuresJson,
        String badge,
        Integer sortOrder,
        Boolean isActive
) {
    public static SubscriptionPlanDto fromEntity(SubscriptionPlan plan) {
        return new SubscriptionPlanDto(
                plan.getId(),
                plan.getCode(),
                plan.getName(),
                plan.getDescription(),
                plan.getPriceMinor(),
                plan.getCurrency(),
                plan.getDurationMonths(),
                plan.getMaxGroups(),
                plan.getMaxMembers(),
                plan.getFeaturesJson(),
                plan.getBadge(),
                plan.getSortOrder(),
                plan.getIsActive()
        );
    }
}
