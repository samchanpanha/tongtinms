package com.tongtin.dashboard.dto;

import java.time.Instant;
import java.util.List;

/**
 * Step 12 host dashboard. Money rules:
 * - per-group amounts carry the group currency on the same row (codebase convention)
 * - aggregate profit is a map by currency using the §5 money shape
 *   (currency/amountMinor/exponent/symbol) — NEVER summed across currencies
 */
public record HostDashboardResponse(
        List<CurrencyProfit> currencies,
        List<GroupRow> groups) {

    public record CurrencyProfit(String currency, long amountMinor, short exponent, String symbol) {
    }

    public record GroupRow(
            Long id,
            String code,
            String name,
            String type,
            String status,
            String currency,
            int shareCount,
            int cycleCount,
            Integer currentCycleNo,
            String currentCycleStatus,
            Instant nextDueAt,
            long unpaidCount,
            long overdueCount,
            long hostProfitMinor) {
    }
}