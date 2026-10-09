package com.tongtin.cycles.service;

import com.tongtin.common.errors.BadRequestException;
import com.tongtin.common.errors.NotFoundException;
import com.tongtin.cycles.dto.CloseCalculateRequest;
import com.tongtin.cycles.dto.CycleResponse;
import com.tongtin.cycles.entity.Cycle;
import com.tongtin.cycles.bids.entity.Bid;
import com.tongtin.cycles.bids.repository.BidRepository;
import com.tongtin.cycles.repository.CycleRepository;
import com.tongtin.groups.entity.Group;
import com.tongtin.groups.repository.GroupRepository;
import com.tongtin.groups.shares.entity.GroupShare;
import com.tongtin.groups.shares.repository.GroupShareRepository;
import com.tongtin.ledger.engine.CycleInput;
import com.tongtin.ledger.engine.CycleResult;
import com.tongtin.ledger.engine.FormulaEngine;
import com.tongtin.ledger.engine.FormulaException;
import com.tongtin.ledger.service.LedgerService;
import com.tongtin.identity.service.AuditService;
import com.tongtin.notify.service.NotificationService;
import com.tongtin.telegram.TelegramMessages;
import com.tongtin.telegram.TelegramNotifier;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Step 10: close a cycle, pick the winner, run the engine, write ledger obligations.
 *
 * close-and-calculate: BIDDING/OPEN -> CLOSED_FOR_CALC (transient, same tx) -> PAYOUT_PENDING.
 * confirm-payout: PAYOUT_PENDING -> SETTLED; marks payout obligations PAID;
 * last cycle also moves the group to COMPLETED.
 *
 * Step 14 (in-app notifications): WINNER_PUBLISHED to all members on close,
 * PAYOUT_READY to the winner + GROUP_COMPLETED to all members on confirm.
 */
@Service
public class CycleCloseService {

    private final CycleRepository cycleRepository;
    private final GroupRepository groupRepository;
    private final GroupShareRepository shareRepository;
    private final BidRepository bidRepository;
    private final LedgerService ledgerService;
    private final NotificationService notificationService;
    private final TelegramNotifier telegramNotifier;
    private final AuditService auditService;

    public CycleCloseService(CycleRepository cycleRepository,
                             GroupRepository groupRepository,
                             GroupShareRepository shareRepository,
                             BidRepository bidRepository,
                             LedgerService ledgerService,
                             NotificationService notificationService,
                             TelegramNotifier telegramNotifier,
                             AuditService auditService) {
        this.cycleRepository = cycleRepository;
        this.groupRepository = groupRepository;
        this.shareRepository = shareRepository;
        this.bidRepository = bidRepository;
        this.ledgerService = ledgerService;
        this.notificationService = notificationService;
        this.telegramNotifier = telegramNotifier;
        this.auditService = auditService;
    }

    @Transactional
    public CycleResponse closeAndCalculate(Long userId, Long ownerId, Long cycleId, CloseCalculateRequest request) {
        Cycle cycle = findOwnedCycle(ownerId, cycleId);
        Group group = findGroup(ownerId, cycle.getGroupId());

        String status = cycle.getStatus();
        if (!"BIDDING".equals(status) && !"OPEN".equals(status)) {
            throw new BadRequestException("Cycle cannot be closed from status " + status);
        }

        List<GroupShare> shares = shareRepository.findByGroupIdOrderByShareNo(group.getId())
                .stream()
                .filter(s -> !"EXITED".equals(s.getStatus()))
                .toList();

        GroupShare winner;
        long winningBid;
        if ("BIDDING".equals(status)) {
            Bid winning = pickBiddingWinner(cycle, group, shares, request);
            winner = shareById(shares, winning.getShareId());
            winningBid = winning.getAmountMinor();
        } else {
            winner = shares.stream()
                    .filter(s -> "ALIVE".equals(s.getStatus()))
                    .min(java.util.Comparator.comparingInt(GroupShare::getShareNo))
                    .orElseThrow(() -> new BadRequestException("No ALIVE share left to receive the pot"));
            winningBid = 0L;
        }

        int n = shares.size();
        int alive = (int) shares.stream().filter(s -> "ALIVE".equals(s.getStatus())).count();
        int dead = n - alive;

        String preset = "FIXED".equals(group.getType()) ? "FIXED_EQUAL" : "BIDDING_CLASSIC";
        CycleInput input = new CycleInput(
                preset,
                FormulaEngine.ENGINE_VERSION,
                group.getBaseAmount(),
                n,
                dead,
                alive,
                winningBid,
                cycle.getCycleNo(),
                group.getCycleCount(),
                group.getHostFeeType(),
                group.getHostFeeMinor(),
                group.getHostFeeBps(),
                group.getMinBid(),
                group.getMaxBid(),
                group.getBidStep());

        CycleResult result;
        try {
            result = FormulaEngine.calculate(input);
        } catch (FormulaException ex) {
            throw new BadRequestException("Calculation failed: " + ex.getMessage());
        }

        cycle.setStatus("CLOSED_FOR_CALC");
        cycle.setWinnerShareId(winner.getId());
        cycle.setWinningBid(winningBid);
        cycle.setGrossPot(result.grossPot());
        cycle.setHostFee(result.T());
        cycle.setNetPayout(result.netPayout());
        cycle.setCalculatedAt(Instant.now());
        cycleRepository.save(cycle);

        ledgerService.writeCycleSettlement(group, cycle, shares, winner.getId(), result);

        winner.setStatus("DEAD");
        winner.setWonCycleId(cycle.getId());
        shareRepository.save(winner);

        cycle.setStatus("PAYOUT_PENDING");
        notificationService.notifyGroupMembers(group.getId(), "WINNER_PUBLISHED",
                "Cong bo ket qua ky " + cycle.getCycleNo(),
                String.format("Ky %d cua hoi \"%s\" da chot; tien ke %d d.",
                        cycle.getCycleNo(), group.getName(), result.netPayout()));
        telegramNotifier.notifyGroupOwner(group, "WINNER_PUBLISHED",
                TelegramMessages.winnerPublished(group, cycle.getCycleNo(),
                        winner.getShareNo(), winningBid, result.netPayout()));
        auditService.record(userId, "Cycle", cycle.getId(), "CYCLE_SETTLED",
                Map.of("cycleNo", cycle.getCycleNo(),
                        "winnerShareId", winner.getId(),
                        "winningBid", winningBid,
                        "grossPot", result.grossPot(),
                        "hostFee", result.T(),
                        "netPayout", result.netPayout(),
                        "formulaVersion", FormulaEngine.ENGINE_VERSION));
        return CycleResponse.from(cycle);
    }

    @Transactional
    public CycleResponse confirmPayout(Long userId, Long ownerId, Long cycleId) {
        Cycle cycle = findOwnedCycle(ownerId, cycleId);
        Group group = findGroup(ownerId, cycle.getGroupId());

        if (!"PAYOUT_PENDING".equals(cycle.getStatus())) {
            throw new BadRequestException("Cycle must be PAYOUT_PENDING to confirm payout, is "
                    + cycle.getStatus());
        }

        cycle.setStatus("SETTLED");
        cycleRepository.save(cycle);
        ledgerService.markCyclePayoutPaid(cycle.getId());
        telegramNotifier.notifyGroupOwner(group, "PAYOUT_CONFIRMED",
                TelegramMessages.payoutConfirmed(group, cycle.getCycleNo(), cycle.getNetPayout()));

        if (cycle.getWinnerShareId() != null) {
            shareRepository.findById(cycle.getWinnerShareId())
                    .map(GroupShare::getMemberProfileId)
                    .ifPresent(memberProfileId -> notificationService.notifyMemberProfile(
                            memberProfileId, "PAYOUT_READY",
                            "Nhan tien ky " + cycle.getCycleNo(),
                            String.format("Ban da nhan %d d cho ky %d cua hoi \"%s\".",
                                    cycle.getNetPayout(), cycle.getCycleNo(), group.getName())));
        }

        if (cycle.getCycleNo() == group.getCycleCount()) {
            group.setStatus("COMPLETED");
            groupRepository.save(group);
            notificationService.notifyGroupMembers(group.getId(), "GROUP_COMPLETED",
                    "Hoi hoan tat",
                    String.format("Hoi \"%s\" da hoan tat sau %d ky.",
                            group.getName(), group.getCycleCount()));
            telegramNotifier.notifyGroupOwner(group, "GROUP_COMPLETED",
                    TelegramMessages.groupCompleted(group, group.getCycleCount()));
        }
        auditService.record(userId, "Cycle", cycle.getId(), "PAYOUT_CONFIRMED",
                Map.of("cycleNo", cycle.getCycleNo(), "netPayout", cycle.getNetPayout()));
        return CycleResponse.from(cycle);
    }

    private Bid pickBiddingWinner(Cycle cycle, Group group, List<GroupShare> shares,
                                  CloseCalculateRequest request) {
        Map<Long, GroupShare> byId = shares.stream()
                .collect(Collectors.toMap(GroupShare::getId, Function.identity()));

        List<Bid> eligible = bidRepository.findByCycleIdAndLatestTrue(cycle.getId())
                .stream()
                .filter(b -> {
                    GroupShare share = byId.get(b.getShareId());
                    return share != null && "ALIVE".equals(share.getStatus());
                })
                .toList();
        if (eligible.isEmpty()) {
            throw new BadRequestException("No eligible bids: place a bid, extend the deadline or mark the cycle FAILED");
        }

        long highest = eligible.stream().mapToLong(Bid::getAmountMinor).max().orElseThrow();
        List<Bid> tied = eligible.stream().filter(b -> b.getAmountMinor() == highest).toList();
        if (tied.size() == 1) {
            return tied.get(0);
        }
        return tieBreak(group, tied, byId, request);
    }

    private Bid tieBreak(Group group, List<Bid> tied, Map<Long, GroupShare> byId,
                         CloseCalculateRequest request) {
        return switch (group.getTieBreak()) {
            case "EARLIEST_BID" -> tied.stream()
                    .min(java.util.Comparator.comparing(Bid::getSubmittedAt)
                            .thenComparingInt(b -> byId.get(b.getShareId()).getShareNo()))
                    .orElseThrow();
            case "LOWEST_MEMBER_CODE" -> tied.stream()
                    .min(java.util.Comparator.comparingInt(b -> byId.get(b.getShareId()).getShareNo()))
                    .orElseThrow();
            case "HOST_DECISION" -> {
                Long chosen = request != null ? request.winnerShareId() : null;
                Bid pick = chosen == null ? null : tied.stream()
                        .filter(b -> b.getShareId().equals(chosen))
                        .findFirst()
                        .orElse(null);
                if (pick == null) {
                    throw new BadRequestException(
                            "tieBreak HOST_DECISION requires winnerShareId of one of the tied bids");
                }
                yield pick;
            }
            default -> throw new BadRequestException("Unknown tieBreak: " + group.getTieBreak());
        };
    }

    private GroupShare shareById(List<GroupShare> shares, Long shareId) {
        return shares.stream()
                .filter(s -> s.getId().equals(shareId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Winning bid share missing from share list"));
    }

    private Cycle findOwnedCycle(Long ownerId, Long cycleId) {
        Cycle cycle = cycleRepository.findByIdForUpdate(cycleId)
                .orElseThrow(() -> new NotFoundException("Cycle not found"));
        findGroup(ownerId, cycle.getGroupId());
        return cycle;
    }

    private Group findGroup(Long ownerId, Long groupId) {
        return groupRepository.findByOwnerIdAndId(ownerId, groupId)
                .orElseThrow(() -> new NotFoundException("Group not found"));
    }
}
