package com.tongtin.subscription.dto;

import java.util.List;

public record AdminInsightsDto(
        List<PlanRevenue> revenueByPlan,
        List<CurrencyTotal> totalsByCurrency,
        List<Cohort> cohorts) {

    public record PlanRevenue(
            Long planId,
            String planName,
            String currency,
            long paidOrders,
            long revenueMinor) {}

    public record CurrencyTotal(
            String currency,
            long paidOrders,
            long revenueMinor) {}

    /** Registration-month cohort. churned = subscription lapsed (endsAt past, not LIFETIME). */
    public record Cohort(
            String month,
            long registered,
            long activeNow,
            long churned) {}
}
