package com.tongtin.dashboard.service;

import com.tongtin.common.money.Currency;
import com.tongtin.common.money.CurrencyRepository;
import com.tongtin.cycles.entity.Cycle;
import com.tongtin.cycles.repository.CycleRepository;
import com.tongtin.dashboard.dto.HostDashboardResponse;
import com.tongtin.groups.entity.Group;
import com.tongtin.groups.repository.GroupRepository;
import com.tongtin.ledger.entity.LedgerEntry;
import com.tongtin.ledger.repository.LedgerEntryRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Host dashboard read model.
 *
 * Profit definition (02-ARCHITECTURE §5, 07-MULTI-CURRENCY §4.7):
 * hostProfit = SUM(cycles.host_fee) over the owner's SETTLED cycles, per currency.
 * Per-group profit = sum over that group's SETTLED cycles only. Pending/payout-pending
 * cycles are NOT counted yet (fee is realized once a cycle settles).
 *
 * nextDueAt = earliest due_at among the group's UNPAID/PARTIAL obligations
 * (an overdue obligation surfaces first because its due_at is earliest).
 */
@Service
public class DashboardService {

    private static final Collection<String> OPEN_STATUSES = List.of("UNPAID", "PARTIAL");

    private final GroupRepository groupRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final CycleRepository cycleRepository;
    private final CurrencyRepository currencyRepository;

    public DashboardService(GroupRepository groupRepository,
                            LedgerEntryRepository ledgerEntryRepository,
                            CycleRepository cycleRepository,
                            CurrencyRepository currencyRepository) {
        this.groupRepository = groupRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.cycleRepository = cycleRepository;
        this.currencyRepository = currencyRepository;
    }

    @Transactional(readOnly = true)
    public HostDashboardResponse dashboard(Long ownerId) {
        List<Group> groups = groupRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId);
        Instant now = Instant.now();
        Map<String, Currency> currencyMeta = currencyRepository.findAll().stream()
                .collect(Collectors.toMap(Currency::getCode, c -> c));

        Map<String, Long> profitByCurrency = new LinkedHashMap<>();
        List<HostDashboardResponse.GroupRow> rows = groups.stream()
                .map(group -> {
                    long unpaid = ledgerEntryRepository
                            .countByGroupIdAndStatusIn(group.getId(), OPEN_STATUSES);
                    long overdue = ledgerEntryRepository
                            .countByGroupIdAndStatusInAndDueAtLessThan(group.getId(), OPEN_STATUSES, now);
                    Instant nextDue = ledgerEntryRepository
                            .findFirstByGroupIdAndStatusInOrderByDueAtAsc(group.getId(), OPEN_STATUSES)
                            .map(LedgerEntry::getDueAt)
                            .orElse(null);
                    long profit = cycleRepository.sumHostFeeByGroupAndSettled(group.getId());
                    profitByCurrency.merge(group.getCurrency(), profit, Long::sum);
                    Cycle latest = cycleRepository.findTopByGroupIdOrderByCycleNoDesc(group.getId()).orElse(null);
                    return new HostDashboardResponse.GroupRow(
                            group.getId(),
                            group.getCode(),
                            group.getName(),
                            group.getType(),
                            group.getStatus(),
                            group.getCurrency(),
                            group.getShareCount(),
                            group.getCycleCount(),
                            latest != null ? latest.getCycleNo() : null,
                            latest != null ? latest.getStatus() : null,
                            nextDue,
                            unpaid,
                            overdue,
                            profit);
                })
                .toList();

        List<HostDashboardResponse.CurrencyProfit> profits = profitByCurrency.entrySet().stream()
                .map(entry -> {
                    Currency meta = currencyMeta.get(entry.getKey());
                    return new HostDashboardResponse.CurrencyProfit(
                            entry.getKey(),
                            entry.getValue(),
                            meta != null ? meta.getExponent() : (short) 0,
                            meta != null ? meta.getSymbol() : entry.getKey());
                })
                .toList();

        return new HostDashboardResponse(profits, rows);
    }
}