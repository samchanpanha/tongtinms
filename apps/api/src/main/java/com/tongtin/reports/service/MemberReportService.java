package com.tongtin.reports.service;

import com.tongtin.common.errors.NotFoundException;
import com.tongtin.common.money.Money;
import com.tongtin.common.money.MoneyLookup;
import com.tongtin.cycles.entity.Cycle;
import com.tongtin.cycles.repository.CycleRepository;
import com.tongtin.groups.entity.Group;
import com.tongtin.groups.repository.GroupRepository;
import com.tongtin.groups.shares.entity.GroupShare;
import com.tongtin.groups.shares.repository.GroupShareRepository;
import com.tongtin.identity.entity.User;
import com.tongtin.identity.repository.UserRepository;
import com.tongtin.ledger.entity.LedgerEntry;
import com.tongtin.ledger.payments.repository.PaymentAllocationRepository;
import com.tongtin.ledger.repository.LedgerEntryRepository;
import com.tongtin.khqr.ObligationQrService;
import com.tongtin.members.entity.MemberProfile;
import com.tongtin.members.repository.MemberProfileRepository;
import com.tongtin.reports.dto.MemberStatementResponse;
import com.tongtin.reports.dto.PublicCycleSummary;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Step 15 member reports: per-share statement for a group the member belongs to,
 * and the PUBLIC cycle summary (01-DOMAIN §14 — never host profit / fees).
 * Member identity = phone -> member_profiles across hosts (same model as the
 * member portal).
 */
@Service
public class MemberReportService {

    private static final Collection<String> CONTRIBUTION_TYPES = List.of("CONTRIBUTION");
    private static final Collection<String> FEE_TYPES = List.of("LATE_FEE", "OTHER_FEE");

    private final UserRepository userRepository;
    private final MemberProfileRepository memberProfileRepository;
    private final GroupShareRepository shareRepository;
    private final GroupRepository groupRepository;
    private final CycleRepository cycleRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final PaymentAllocationRepository allocationRepository;
    private final MoneyLookup money;
    private final ObligationQrService obligationQrService;

    public MemberReportService(UserRepository userRepository,
                               MemberProfileRepository memberProfileRepository,
                               GroupShareRepository shareRepository,
                               GroupRepository groupRepository,
                               CycleRepository cycleRepository,
                               LedgerEntryRepository ledgerEntryRepository,
                               PaymentAllocationRepository allocationRepository,
                               MoneyLookup money,
                               ObligationQrService obligationQrService) {
        this.userRepository = userRepository;
        this.memberProfileRepository = memberProfileRepository;
        this.shareRepository = shareRepository;
        this.groupRepository = groupRepository;
        this.cycleRepository = cycleRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.allocationRepository = allocationRepository;
        this.money = money;
        this.obligationQrService = obligationQrService;
    }

    @Transactional(readOnly = true)
    public MemberStatementResponse statement(Long userId, Long groupId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new NotFoundException("group not found"));
        List<GroupShare> myShares = sharesOfUserInGroup(userId, groupId);
        String code = group.getCurrency();

        List<Long> myShareIds = myShares.stream().map(GroupShare::getId).toList();
        List<LedgerEntry> entries = ledgerEntryRepository
                .findByGroupIdAndShareIdInOrderByCreatedAtAscIdAsc(groupId, myShareIds);
        Map<Long, Integer> cycleNoById = cycleRepository.findByGroupIdOrderByCycleNo(groupId).stream()
                .collect(Collectors.toMap(Cycle::getId, Cycle::getCycleNo));
        Map<Long, Long> allocatedById = allocatedByEntryId(
                entries.stream().map(LedgerEntry::getId).toList());

        List<MemberStatementResponse.Share> shares = myShares.stream()
                .sorted(Comparator.comparing(GroupShare::getShareNo))
                .map(share -> toShare(group, share, entries, cycleNoById, allocatedById, code))
                .toList();

        MemberStatementResponse.Total total = new MemberStatementResponse.Total(
                money.money(code, sumOf(shares, MemberStatementResponse.Total::contributed)),
                money.money(code, sumOf(shares, MemberStatementResponse.Total::received)),
                money.money(code, sumOf(shares, MemberStatementResponse.Total::feesPaid)),
                money.money(code, sumOf(shares, MemberStatementResponse.Total::netPosition)));

        return new MemberStatementResponse(group.getId(), group.getName(), code, shares, total);
    }

    @Transactional(readOnly = true)
    public List<PublicCycleSummary> cycles(Long userId, Long groupId) {
        List<GroupShare> myShares = sharesOfUserInGroup(userId, groupId);
        if (myShares.isEmpty()) {
            throw new NotFoundException("group not found or not a member");
        }
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new NotFoundException("group not found"));
        Map<Long, GroupShare> shareById = shareRepository.findByGroupIdOrderByShareNo(groupId).stream()
                .collect(Collectors.toMap(GroupShare::getId, Function.identity()));
        List<Long> memberIds = shareById.values().stream()
                .map(GroupShare::getMemberProfileId)
                .distinct()
                .toList();
        Map<Long, MemberProfile> memberById = memberProfileRepository.findAllById(memberIds).stream()
                .collect(Collectors.toMap(MemberProfile::getId, Function.identity()));

        return cycleRepository.findByGroupIdOrderByCycleNo(groupId).stream()
                .map(cycle -> toPublicSummary(cycle, shareById, memberById))
                .toList();
    }

    private MemberStatementResponse.Share toShare(Group group,
                                                  GroupShare share,
                                                  List<LedgerEntry> entries,
                                                  Map<Long, Integer> cycleNoById,
                                                  Map<Long, Long> allocatedById,
                                                  String code) {
        List<LedgerEntry> shareEntries = entries.stream()
                .filter(e -> share.getId().equals(e.getShareId()))
                .sorted(Comparator
                        .comparingInt((LedgerEntry e) -> cycleNoById.getOrDefault(e.getCycleId(), 0))
                        .thenComparingLong(LedgerEntry::getId))
                .toList();

        long running = 0;
        long contributed = 0;
        long received = 0;
        long feesPaid = 0;
        List<MemberStatementResponse.Entry> lines = new java.util.ArrayList<>();
        for (LedgerEntry e : shareEntries) {
            long allocated = allocatedById.getOrDefault(e.getId(), 0L);
            long remaining = Math.max(0, e.getAmountMinor() - allocated);
            String khqr = remaining > 0 && "IN".equals(e.getDirection())
                    ? obligationQrService.payload(group.getCode(), e.getId(),
                            group.getName(), remaining, code)
                    : null;
            long delta;
            if ("OUT".equals(e.getDirection())) {
                delta = e.getAmountMinor();
            } else {
                delta = -allocated;
            }
            running += delta;
            if (CONTRIBUTION_TYPES.contains(e.getType())) {
                contributed += allocated;
            } else if (FEE_TYPES.contains(e.getType())) {
                feesPaid += allocated;
            } else if ("PAYOUT".equals(e.getType())) {
                received += e.getStatus().equals("PAID") ? e.getAmountMinor() : 0;
            }
            lines.add(new MemberStatementResponse.Entry(
                    e.getId(),
                    cycleNoById.getOrDefault(e.getCycleId(), 0),
                    e.getType(),
                    e.getDirection(),
                    money.money(code, e.getAmountMinor()),
                    e.getStatus(),
                    e.getDueAt(),
                    money.money(code, allocated),
                    money.money(code, remaining),
                    money.money(code, running),
                    khqr));
        }

        long netPosition = received - contributed - feesPaid;
        return new MemberStatementResponse.Share(
                share.getId(), share.getShareNo(), share.getStatus(), lines,
                new MemberStatementResponse.Total(
                        money.money(code, contributed),
                        money.money(code, received),
                        money.money(code, feesPaid),
                        money.money(code, netPosition)));
    }

    private PublicCycleSummary toPublicSummary(Cycle cycle,
                                               Map<Long, GroupShare> shareById,
                                               Map<Long, MemberProfile> memberById) {
        PublicCycleSummary.Winner winner = null;
        if (cycle.getWinnerShareId() != null && shareById.containsKey(cycle.getWinnerShareId())) {
            GroupShare share = shareById.get(cycle.getWinnerShareId());
            MemberProfile member = memberById.get(share.getMemberProfileId());
            winner = new PublicCycleSummary.Winner(
                    share.getShareNo(), member != null ? member.getFullName() : null);
        }
        String code = cycle.getCurrency();
        return new PublicCycleSummary(
                cycle.getCycleNo(),
                cycle.getStatus(),
                cycle.getOpenAt(),
                cycle.getBidCloseAt(),
                cycle.getDueAt(),
                winner,
                money.nullable(code, cycle.getWinningBid()),
                money.nullable(code, cycle.getGrossPot()),
                money.nullable(code, cycle.getNetPayout()));
    }

    private List<GroupShare> sharesOfUserInGroup(Long userId, Long groupId) {
        List<GroupShare> shares = shareRepository
                .findByGroupIdAndMemberProfileIdIn(groupId, profileIdsOfUser(userId));
        if (shares.isEmpty()) {
            throw new NotFoundException("group not found or not a member");
        }
        return shares;
    }

    private List<MemberProfile> profilesOfUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("member not found"));
        return memberProfileRepository.findByPhone(user.getPhone());
    }

    private List<Long> profileIdsOfUser(Long userId) {
        return profilesOfUser(userId).stream().map(MemberProfile::getId).toList();
    }

    private Map<Long, Long> allocatedByEntryId(Collection<Long> entryIds) {
        if (entryIds.isEmpty()) {
            return Map.of();
        }
        return allocationRepository.sumAllocatedByEntryIds(entryIds).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
    }

    private static long sumOf(List<MemberStatementResponse.Share> shares,
                              Function<MemberStatementResponse.Total, Money> getter) {
        return shares.stream().mapToLong(s -> getter.apply(s.totals()).amountMinor()).sum();
    }
}