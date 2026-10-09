package com.tongtin.cycles.bids.service;

import com.tongtin.common.errors.BadRequestException;
import com.tongtin.common.errors.ForbiddenException;
import com.tongtin.common.errors.NotFoundException;
import com.tongtin.cycles.bids.dto.BidResponse;
import com.tongtin.cycles.bids.dto.BidSubmitRequest;
import com.tongtin.cycles.bids.dto.CycleBidSummaryResponse;
import com.tongtin.cycles.bids.entity.Bid;
import com.tongtin.cycles.bids.repository.BidRepository;
import com.tongtin.cycles.entity.Cycle;
import com.tongtin.cycles.repository.CycleRepository;
import com.tongtin.groups.entity.Group;
import com.tongtin.groups.repository.GroupRepository;
import com.tongtin.groups.shares.entity.GroupShare;
import com.tongtin.groups.shares.repository.GroupShareRepository;
import com.tongtin.identity.service.AuditService;
import com.tongtin.members.entity.MemberProfile;
import com.tongtin.members.repository.MemberProfileRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BidService {

    private final BidRepository bidRepository;
    private final CycleRepository cycleRepository;
    private final GroupRepository groupRepository;
    private final GroupShareRepository shareRepository;
    private final AuditService auditService;
    private final MemberProfileRepository memberProfileRepository;

    public BidService(BidRepository bidRepository,
                      CycleRepository cycleRepository,
                      GroupRepository groupRepository,
                      GroupShareRepository shareRepository,
                      AuditService auditService,
                      MemberProfileRepository memberProfileRepository) {
        this.bidRepository = bidRepository;
        this.cycleRepository = cycleRepository;
        this.groupRepository = groupRepository;
        this.shareRepository = shareRepository;
        this.auditService = auditService;
        this.memberProfileRepository = memberProfileRepository;
    }

    @Transactional
    public BidResponse submit(Long userId, Long ownerId, Long cycleId, BidSubmitRequest request) {
        Cycle cycle = findOwnedCycle(ownerId, cycleId);
        Group group = findGroup(ownerId, cycle.getGroupId());
        GroupShare share = findOwnedShare(group.getId(), request.shareId());
        return placeBid(userId, cycle, group, share, request);
    }

    /**
     * Shared bid placement core, also used by the member portal after the caller
     * has verified the share belongs to the member (01-DOMAIN: member may only bid
     * own shares).
     */
    @Transactional
    public BidResponse placeBid(Long actorUserId, Cycle cycle, Group group, GroupShare share,
                                BidSubmitRequest request) {
        if (!"BIDDING".equals(cycle.getStatus())) {
            throw new BadRequestException("Cycle is not in BIDDING status: " + cycle.getStatus());
        }
        if (cycle.getBidCloseAt() != null && Instant.now().isAfter(cycle.getBidCloseAt())) {
            throw new BadRequestException("Bidding window is closed");
        }

        if (!"ALIVE".equals(share.getStatus())) {
            throw new BadRequestException("Only ALIVE shares may bid, share is " + share.getStatus());
        }

        MemberProfile member = memberProfileRepository.findById(share.getMemberProfileId())
                .orElse(null);
        if (member != null && !"ACTIVE".equals(member.getStatus())) {
            throw new BadRequestException("Member is not active: " + member.getStatus());
        }

        long bid = request.amountMinor();
        long minBid = group.getMinBid();
        long maxBid = group.getMaxBid();
        long bidStep = group.getBidStep();
        if (bid < minBid || bid > maxBid) {
            throw new BadRequestException("Bid must be between " + minBid + " and " + maxBid);
        }
        if (bid % bidStep != 0) {
            throw new BadRequestException("Bid must be a multiple of bidStep " + bidStep);
        }

        bidRepository.markNonLatest(cycle.getId(), share.getId());

        Bid bidEntity = new Bid();
        bidEntity.setCycleId(cycle.getId());
        bidEntity.setShareId(share.getId());
        bidEntity.setAmountMinor(bid);
        bidEntity.setCurrency(group.getCurrency());
        bidEntity.setLatest(true);
        bidRepository.save(bidEntity);
        auditService.record(actorUserId, "Bid", bidEntity.getId(), "BID_SUBMITTED",
                Map.of("cycleId", cycle.getId(),
                        "shareId", share.getId(),
                        "amountMinor", bid,
                        "currency", group.getCurrency()));
        return BidResponse.from(bidEntity);
    }

    @Transactional(readOnly = true)
    public BidResponse myBid(Long ownerId, Long cycleId, Long shareId) {
        Cycle cycle = findOwnedCycle(ownerId, cycleId);
        findOwnedShare(cycle.getGroupId(), shareId);
        return bidRepository.findByCycleIdAndShareIdAndLatestTrue(cycleId, shareId)
                .map(BidResponse::from)
                .orElseThrow(() -> new NotFoundException("No bid submitted for this share"));
    }

    @Transactional(readOnly = true)
    public CycleBidSummaryResponse summary(Long ownerId, Long cycleId) {
        Cycle cycle = findOwnedCycle(ownerId, cycleId);
        Group group = findGroup(ownerId, cycle.getGroupId());

        boolean sealed = "BIDDING".equals(cycle.getStatus())
                && cycle.getBidCloseAt() != null
                && Instant.now().isBefore(cycle.getBidCloseAt());

        Map<Long, Bid> latestByShare = bidRepository.findByCycleIdAndLatestTrue(cycleId)
                .stream()
                .collect(Collectors.toMap(Bid::getShareId, Function.identity()));

        List<CycleBidSummaryResponse.Entry> entries = shareRepository.findByGroupIdOrderByShareNo(group.getId())
                .stream()
                .map(share -> toEntry(share, latestByShare.get(share.getId()), sealed))
                .toList();

        return new CycleBidSummaryResponse(
                cycle.getId(),
                cycle.getCycleNo(),
                cycle.getStatus(),
                cycle.getCurrency(),
                cycle.getBidCloseAt(),
                sealed,
                cycle.getWinnerShareId(),
                cycle.getWinningBid(),
                cycle.getGrossPot(),
                cycle.getHostFee(),
                cycle.getNetPayout(),
                cycle.getCalculatedAt(),
                entries);
    }

    private CycleBidSummaryResponse.Entry toEntry(GroupShare share, Bid bid, boolean sealed) {
        boolean submitted = bid != null;
        return new CycleBidSummaryResponse.Entry(
                share.getId(),
                share.getShareNo(),
                share.getMemberProfileId(),
                share.getStatus(),
                submitted,
                submitted && !sealed ? bid.getAmountMinor() : null,
                submitted && !sealed ? bid.getSubmittedAt() : null);
    }

    private Cycle findOwnedCycle(Long ownerId, Long cycleId) {
        Cycle cycle = cycleRepository.findById(cycleId)
                .orElseThrow(() -> new NotFoundException("Cycle not found"));
        findGroup(ownerId, cycle.getGroupId());
        return cycle;
    }

    private Group findGroup(Long ownerId, Long groupId) {
        return groupRepository.findByOwnerIdAndId(ownerId, groupId)
                .orElseThrow(() -> new NotFoundException("Group not found"));
    }

    private GroupShare findOwnedShare(Long groupId, Long shareId) {
        return shareRepository.findByGroupIdAndId(groupId, shareId)
                .orElseThrow(() -> new ForbiddenException("Bid not allowed for a share outside this cycle's group"));
    }
}