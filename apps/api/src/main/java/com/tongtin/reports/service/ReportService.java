package com.tongtin.reports.service;

import com.tongtin.common.errors.NotFoundException;
import com.tongtin.common.money.MoneyLookup;
import com.tongtin.cycles.entity.Cycle;
import com.tongtin.cycles.repository.CycleRepository;
import com.tongtin.groups.entity.Group;
import com.tongtin.groups.repository.GroupRepository;
import com.tongtin.groups.shares.entity.GroupShare;
import com.tongtin.groups.shares.repository.GroupShareRepository;
import com.tongtin.ledger.engine.FormulaEngine;
import com.tongtin.ledger.entity.LedgerEntry;
import com.tongtin.ledger.payments.repository.PaymentAllocationRepository;
import com.tongtin.ledger.repository.LedgerEntryRepository;
import com.tongtin.members.entity.MemberProfile;
import com.tongtin.members.repository.MemberProfileRepository;
import com.tongtin.reports.dto.LedgerReportResponse;
import com.tongtin.reports.dto.PageMeta;
import com.tongtin.reports.dto.ProfitReportResponse;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Step 15 host reports: full group ledger and per-group profit statement.
 * Money is always the {currency, amountMinor, exponent, symbol} shape (07 §5);
 * tenant-bound -> unknown/cross-owner group is a 404.
 */
@Service
public class ReportService {

    private final GroupRepository groupRepository;
    private final CycleRepository cycleRepository;
    private final GroupShareRepository shareRepository;
    private final MemberProfileRepository memberProfileRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final PaymentAllocationRepository allocationRepository;
    private final MoneyLookup money;

    public ReportService(GroupRepository groupRepository,
                         CycleRepository cycleRepository,
                         GroupShareRepository shareRepository,
                         MemberProfileRepository memberProfileRepository,
                         LedgerEntryRepository ledgerEntryRepository,
                         PaymentAllocationRepository allocationRepository,
                         MoneyLookup money) {
        this.groupRepository = groupRepository;
        this.cycleRepository = cycleRepository;
        this.shareRepository = shareRepository;
        this.memberProfileRepository = memberProfileRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.allocationRepository = allocationRepository;
        this.money = money;
    }

    @Transactional(readOnly = true)
    public LedgerReportResponse ledger(Long ownerId, Long groupId) {
        Group group = groupRepository.findByOwnerIdAndId(ownerId, groupId)
                .orElseThrow(() -> new NotFoundException("group not found"));

        Map<Long, Integer> cycleNoById = cycleNoById(groupId);
        List<LedgerEntry> entries = ledgerEntryRepository.findByGroupIdOrderByCreatedAtAscIdAsc(groupId);
        Map<Long, Long> allocatedById = allocatedByEntryId(
                entries.stream().map(LedgerEntry::getId).toList());

        Map<Long, GroupShare> shareById = sharesById(group);
        Map<Long, MemberProfile> memberById = membersById(entries);

        List<LedgerEntry> sorted = entries.stream()
                .sorted(ledgerOrder(cycleNoById))
                .toList();
        long totalIn = 0;
        long totalOut = 0;
        for (LedgerEntry e : sorted) {
            if ("IN".equals(e.getDirection())) {
                totalIn += e.getAmountMinor();
            } else {
                totalOut += e.getAmountMinor();
            }
        }
        List<LedgerReportResponse.Entry> lines = sorted.stream()
                .map(e -> toLedgerLine(e, cycleNoById, allocatedById, shareById, memberById))
                .toList();

        return new LedgerReportResponse(
                group.getId(), group.getName(), group.getCurrency(), lines,
                money.money(group.getCurrency(), totalIn),
                money.money(group.getCurrency(), totalOut));
    }

    /** Step 42: Paginated + filtered ledger report. */
    @Transactional(readOnly = true)
    public LedgerReportResponse ledgerPaged(Long ownerId, Long groupId,
                                            Long cycleId, String type, String status,
                                            int page, int size) {
        Group group = groupRepository.findByOwnerIdAndId(ownerId, groupId)
                .orElseThrow(() -> new NotFoundException("group not found"));

        Pageable pageable = PageRequest.of(page, Math.min(size, 200));
        Page<LedgerEntry> entryPage = ledgerEntryRepository
                .findByGroupIdFiltered(groupId, cycleId, type, status, pageable);

        List<LedgerEntry> entries = entryPage.getContent();
        Map<Long, Integer> cycleNoById = cycleNoById(groupId);
        Map<Long, Long> allocatedById = allocatedByEntryId(
                entries.stream().map(LedgerEntry::getId).toList());
        Map<Long, GroupShare> shareById = sharesById(group);
        Map<Long, MemberProfile> memberById = membersById(entries);

        long totalIn = 0;
        long totalOut = 0;
        for (LedgerEntry e : entries) {
            if ("IN".equals(e.getDirection())) totalIn += e.getAmountMinor();
            else totalOut += e.getAmountMinor();
        }

        List<LedgerReportResponse.Entry> lines = entries.stream()
                .map(e -> toLedgerLine(e, cycleNoById, allocatedById, shareById, memberById))
                .toList();

        PageMeta meta = new PageMeta(
                entryPage.getNumber(), entryPage.getSize(),
                entryPage.getTotalElements(), entryPage.getTotalPages());

        return new LedgerReportResponse(
                group.getId(), group.getName(), group.getCurrency(), lines,
                money.money(group.getCurrency(), totalIn),
                money.money(group.getCurrency(), totalOut),
                meta);
    }

    @Transactional(readOnly = true)
    public ProfitReportResponse profit(Long ownerId, Long groupId) {
        Group group = groupRepository.findByOwnerIdAndId(ownerId, groupId)
                .orElseThrow(() -> new NotFoundException("group not found"));
        String code = group.getCurrency();

        Map<Long, GroupShare> shareById = sharesById(group);
        Map<Long, MemberProfile> memberById = memberIdByProfile(shareRepository.findByGroupIdOrderByShareNo(groupId));

        List<Cycle> cycles = cycleRepository.findByGroupIdOrderByCycleNo(groupId);
        long grossPot = 0;
        long hostFee = 0;
        long netPayout = 0;
        List<ProfitReportResponse.CycleLine> lines = new java.util.ArrayList<>();
        for (Cycle cycle : cycles) {
            long gp = cycle.getGrossPot() != null ? cycle.getGrossPot() : 0;
            long hf = cycle.getHostFee() != null ? cycle.getHostFee() : 0;
            long np = cycle.getNetPayout() != null ? cycle.getNetPayout() : 0;
            grossPot += gp;
            hostFee += hf;
            netPayout += np;
            lines.add(toProfitLine(cycle, shareById, memberById));
        }

        ProfitReportResponse.Totals totals = new ProfitReportResponse.Totals(
                money.money(code, grossPot),
                money.money(code, hostFee),
                money.money(code, netPayout),
                money.money(code, cycleRepository.sumHostFeeByGroupAndSettled(groupId)));

        return new ProfitReportResponse(
                group.getId(), group.getName(), code, FormulaEngine.ENGINE_VERSION, lines, totals);
    }

    private LedgerReportResponse.Entry toLedgerLine(LedgerEntry e,
                                                    Map<Long, Integer> cycleNoById,
                                                    Map<Long, Long> allocatedById,
                                                    Map<Long, GroupShare> shareById,
                                                    Map<Long, MemberProfile> memberById) {
        long allocated = allocatedById.getOrDefault(e.getId(), 0L);
        long remaining = Math.max(0, e.getAmountMinor() - allocated);
        GroupShare share = e.getShareId() != null ? shareById.get(e.getShareId()) : null;
        MemberProfile member = e.getMemberProfileId() != null ? memberById.get(e.getMemberProfileId()) : null;
        return new LedgerReportResponse.Entry(
                e.getId(),
                cycleNoById.getOrDefault(e.getCycleId(), 0),
                e.getType(),
                e.getDirection(),
                share != null ? share.getShareNo() : null,
                e.getMemberProfileId(),
                member != null ? member.getFullName() : null,
                money.money(e.getCurrency(), e.getAmountMinor()),
                e.getStatus(),
                e.getDueAt(),
                money.money(e.getCurrency(), allocated),
                money.money(e.getCurrency(), remaining));
    }

    private ProfitReportResponse.CycleLine toProfitLine(Cycle cycle,
                                                        Map<Long, GroupShare> shareById,
                                                        Map<Long, MemberProfile> memberById) {
        ProfitReportResponse.CycleLine.Winner winner = null;
        if (cycle.getWinnerShareId() != null) {
            GroupShare share = shareById.get(cycle.getWinnerShareId());
            if (share != null) {
                MemberProfile member = memberById.get(share.getMemberProfileId());
                winner = new ProfitReportResponse.CycleLine.Winner(
                        share.getId(), share.getShareNo(),
                        member != null ? member.getFullName() : null);
            }
        }
        String code = cycle.getCurrency();
        return new ProfitReportResponse.CycleLine(
                cycle.getCycleNo(),
                cycle.getStatus(),
                cycle.getOpenAt(),
                cycle.getCalculatedAt(),
                winner,
                money.nullable(code, cycle.getWinningBid()),
                money.nullable(code, cycle.getGrossPot()),
                money.nullable(code, cycle.getHostFee()),
                money.nullable(code, cycle.getNetPayout()));
    }

    private static Comparator<LedgerEntry> ledgerOrder(Map<Long, Integer> cycleNoById) {
        return Comparator
                .comparingInt((LedgerEntry e) -> cycleNoById.getOrDefault(e.getCycleId(), 0))
                .thenComparing(Comparator.comparing(LedgerEntry::getShareId,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .thenComparingLong(LedgerEntry::getId);
    }

    private Map<Long, Integer> cycleNoById(Long groupId) {
        return cycleRepository.findByGroupIdOrderByCycleNo(groupId).stream()
                .collect(Collectors.toMap(Cycle::getId, Cycle::getCycleNo));
    }

    private Map<Long, Long> allocatedByEntryId(Collection<Long> entryIds) {
        if (entryIds.isEmpty()) {
            return Map.of();
        }
        return allocationRepository.sumAllocatedByEntryIds(entryIds).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
    }

    private Map<Long, GroupShare> sharesById(Group group) {
        return shareRepository.findByGroupIdOrderByShareNo(group.getId()).stream()
                .collect(Collectors.toMap(GroupShare::getId, Function.identity()));
    }

    private Map<Long, MemberProfile> membersById(List<LedgerEntry> entries) {
        List<Long> ids = entries.stream()
                .map(LedgerEntry::getMemberProfileId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return memberProfileRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(MemberProfile::getId, Function.identity()));
    }

    private Map<Long, MemberProfile> memberIdByProfile(List<GroupShare> shares) {
        List<Long> ids = shares.stream()
                .map(GroupShare::getMemberProfileId)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return memberProfileRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(MemberProfile::getId, Function.identity()));
    }
}