package com.tongtin.memberportal.service;

import com.tongtin.common.errors.ForbiddenException;
import com.tongtin.common.errors.NotFoundException;
import com.tongtin.cycles.bids.dto.BidResponse;
import com.tongtin.cycles.bids.dto.BidSubmitRequest;
import com.tongtin.cycles.bids.service.BidService;
import com.tongtin.cycles.entity.Cycle;
import com.tongtin.cycles.repository.CycleRepository;
import com.tongtin.groups.entity.Group;
import com.tongtin.groups.repository.GroupRepository;
import com.tongtin.groups.shares.entity.GroupShare;
import com.tongtin.groups.shares.repository.GroupShareRepository;
import com.tongtin.identity.entity.User;
import com.tongtin.identity.repository.UserRepository;
import com.tongtin.ledger.payments.repository.PaymentAllocationRepository;
import com.tongtin.ledger.repository.LedgerEntryRepository;
import com.tongtin.memberportal.dto.BalanceResponse;
import com.tongtin.memberportal.dto.MemberGroupResponse;
import com.tongtin.members.entity.MemberProfile;
import com.tongtin.members.repository.MemberProfileRepository;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Step 13 member portal. A member's identity is their phone: the logged-in user
 * resolves to every member_profiles row with that phone across all hosts, and sees
 * only groups/shares assigned to those profiles (01-DOMAIN: member may only bid and
 * view own shares).
 */
@Service
public class MemberPortalService {

    private static final Collection<String> CONTRIBUTION_TYPES = List.of("CONTRIBUTION");
    private static final Collection<String> FEE_TYPES = List.of("LATE_FEE", "OTHER_FEE");
    private static final Collection<String> PAYOUT_TYPES = List.of("PAYOUT");

    private final UserRepository userRepository;
    private final MemberProfileRepository memberProfileRepository;
    private final GroupShareRepository shareRepository;
    private final GroupRepository groupRepository;
    private final CycleRepository cycleRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final PaymentAllocationRepository allocationRepository;
    private final BidService bidService;

    public MemberPortalService(UserRepository userRepository,
                               MemberProfileRepository memberProfileRepository,
                               GroupShareRepository shareRepository,
                               GroupRepository groupRepository,
                               CycleRepository cycleRepository,
                               LedgerEntryRepository ledgerEntryRepository,
                               PaymentAllocationRepository allocationRepository,
                               BidService bidService) {
        this.userRepository = userRepository;
        this.memberProfileRepository = memberProfileRepository;
        this.shareRepository = shareRepository;
        this.groupRepository = groupRepository;
        this.cycleRepository = cycleRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.allocationRepository = allocationRepository;
        this.bidService = bidService;
    }

    @Transactional(readOnly = true)
    public List<MemberGroupResponse> myGroups(Long userId) {
        List<GroupShare> shares = sharesOfUser(userId);
        if (shares.isEmpty()) {
            return List.of();
        }
        List<Long> groupIds = shares.stream().map(GroupShare::getGroupId).distinct().toList();
        Map<Long, List<GroupShare>> byGroup = shares.stream()
                .collect(Collectors.groupingBy(GroupShare::getGroupId));

        return groupRepository.findAllById(groupIds).stream()
                .sorted(Comparator.comparing(Group::getCreatedAt).reversed())
                .map(group -> toGroupResponse(group, byGroup.getOrDefault(group.getId(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public MemberGroupResponse myGroup(Long userId, Long groupId) {
        List<GroupShare> shares = sharesOfUserInGroup(userId, groupId);
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new NotFoundException("group not found"));
        return toGroupResponse(group, shares);
    }

    @Transactional(readOnly = true)
    public BalanceResponse balance(Long userId, Long groupId) {
        List<GroupShare> shares = sharesOfUserInGroup(userId, groupId);
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new NotFoundException("group not found"));
        List<Long> shareIds = shares.stream().map(GroupShare::getId).toList();

        long contributed = allocationRepository
                .sumAllocatedByGroupAndShareInAndTypeIn(groupId, shareIds, CONTRIBUTION_TYPES);
        long feesPaid = allocationRepository
                .sumAllocatedByGroupAndShareInAndTypeIn(groupId, shareIds, FEE_TYPES);
        long received = ledgerEntryRepository
                .sumAmountByGroupAndShareInAndTypeInAndStatus(groupId, shareIds, PAYOUT_TYPES, "PAID");

        List<BalanceResponse.ShareBalance> shareBalances = shares.stream()
                .map(share -> {
                    List<Long> oneShare = List.of(share.getId());
                    long shareContributed = allocationRepository
                            .sumAllocatedByGroupAndShareInAndTypeIn(groupId, oneShare, CONTRIBUTION_TYPES);
                    long shareFees = allocationRepository
                            .sumAllocatedByGroupAndShareInAndTypeIn(groupId, oneShare, FEE_TYPES);
                    long shareReceived = ledgerEntryRepository
                            .sumAmountByGroupAndShareInAndTypeInAndStatus(groupId, oneShare, PAYOUT_TYPES, "PAID");
                    return new BalanceResponse.ShareBalance(
                            share.getId(),
                            share.getShareNo(),
                            share.getStatus(),
                            share.getWonCycleId(),
                            shareContributed,
                            shareReceived,
                            shareFees,
                            shareReceived - shareContributed - shareFees);
                })
                .toList();

        return new BalanceResponse(
                group.getId(), group.getName(), group.getCurrency(),
                contributed, received, feesPaid, received - contributed - feesPaid, shareBalances);
    }

    @Transactional
    public BidResponse submitBid(Long userId, Long cycleId, BidSubmitRequest request) {
        List<MemberProfile> profiles = profilesOfUser(userId);
        if (profiles.isEmpty()) {
            throw new NotFoundException("no member profile for this account");
        }
        Cycle cycle = cycleRepository.findById(cycleId)
                .orElseThrow(() -> new NotFoundException("cycle not found"));
        Group group = groupRepository.findById(cycle.getGroupId())
                .orElseThrow(() -> new NotFoundException("group not found"));
        GroupShare share = shareRepository.findByGroupIdAndId(group.getId(), request.shareId())
                .orElseThrow(() -> new NotFoundException("share not found in this group"));

        boolean ownsShare = profiles.stream().anyMatch(p -> p.getId().equals(share.getMemberProfileId()));
        if (!ownsShare) {
            throw new ForbiddenException("This is not your share");
        }
        return bidService.placeBid(userId, cycle, group, share, request);
    }

    private MemberGroupResponse toGroupResponse(Group group, List<GroupShare> shares) {
        List<MemberGroupResponse.MyShare> myShares = shares.stream()
                .sorted(Comparator.comparing(GroupShare::getShareNo))
                .map(share -> new MemberGroupResponse.MyShare(share.getId(), share.getShareNo(), share.getStatus()))
                .toList();
        Cycle latest = cycleRepository.findTopByGroupIdOrderByCycleNoDesc(group.getId()).orElse(null);
        return new MemberGroupResponse(
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
                myShares.size(),
                myShares);
    }

    private List<GroupShare> sharesOfUser(Long userId) {
        return shareRepository.findByMemberProfileIdIn(profileIdsOfUser(userId));
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
}