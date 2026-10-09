package com.tongtin.groups.shares;

import com.tongtin.common.errors.BadRequestException;
import com.tongtin.common.errors.ConflictException;
import com.tongtin.common.errors.NotFoundException;
import com.tongtin.groups.dto.GroupResponse;
import com.tongtin.groups.entity.Group;
import com.tongtin.groups.repository.GroupRepository;
import com.tongtin.groups.shares.dto.ShareAssignRequest;
import com.tongtin.groups.shares.dto.ShareResponse;
import com.tongtin.groups.shares.entity.GroupShare;
import com.tongtin.groups.shares.repository.GroupShareRepository;
import com.tongtin.members.blacklist.service.BlacklistService;
import com.tongtin.members.entity.MemberProfile;
import com.tongtin.members.repository.MemberProfileRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShareService {

    private final GroupRepository groupRepository;
    private final GroupShareRepository shareRepository;
    private final MemberProfileRepository memberProfileRepository;
    private final BlacklistService blacklistService;

    public ShareService(GroupRepository groupRepository,
                        GroupShareRepository shareRepository,
                        MemberProfileRepository memberProfileRepository,
                        BlacklistService blacklistService) {
        this.groupRepository = groupRepository;
        this.shareRepository = shareRepository;
        this.memberProfileRepository = memberProfileRepository;
        this.blacklistService = blacklistService;
    }

    @Transactional
    public List<ShareResponse> assign(Long ownerId, Long groupId, ShareAssignRequest request) {
        Group group = findGroup(ownerId, groupId);
        checkEditableShares(group);

        MemberProfile member = memberProfileRepository.findByOwnerIdAndId(ownerId, request.memberProfileId())
                .orElseThrow(() -> new NotFoundException("Member not found"));
        blacklistService.activeReason(ownerId, member.getPhone()).ifPresent(reason -> {
            throw new ConflictException("This phone is blacklisted: " + reason);
        });
        if (!"ACTIVE".equals(member.getStatus())) {
            throw new BadRequestException("Member is not active");
        }
        if (!group.isAllowMultiShare() && (request.count() > 1
                || shareRepository.countByGroupIdAndMemberProfileId(groupId, member.getId()) > 0)) {
            throw new BadRequestException("Multi-share is not allowed for this group");
        }
        long totalAfter = shareRepository.countByGroupId(groupId) + request.count();
        if (totalAfter > group.getShareCount()) {
            throw new BadRequestException("Total shares would exceed shareCount");
        }

        int nextNo = shareRepository.findMaxShareNo(groupId) + 1;
        for (int i = 0; i < request.count(); i++) {
            GroupShare share = new GroupShare();
            share.setGroupId(groupId);
            share.setMemberProfileId(member.getId());
            share.setShareNo(nextNo++);
            share.setStatus("ALIVE");
            shareRepository.save(share);
        }

        if ("DRAFT".equals(group.getStatus())) {
            group.setStatus("RECRUITING");
            groupRepository.save(group);
        }
        return list(ownerId, groupId);
    }

    @Transactional
    public List<ShareResponse> list(Long ownerId, Long groupId) {
        Group group = findGroup(ownerId, groupId);
        return shareRepository.findByGroupIdOrderByShareNo(group.getId())
                .stream().map(ShareResponse::from).toList();
    }

    @Transactional
    public void remove(Long ownerId, Long groupId, Long shareId) {
        Group group = findGroup(ownerId, groupId);
        checkEditableShares(group);
        GroupShare share = shareRepository.findByGroupIdAndId(groupId, shareId)
                .orElseThrow(() -> new NotFoundException("Share not found"));
        shareRepository.delete(share);
        if (shareRepository.countByGroupId(groupId) == 0) {
            group.setStatus("DRAFT");
            groupRepository.save(group);
        }
    }

    @Transactional
    public GroupResponse start(Long ownerId, Long groupId) {
        Group group = findGroup(ownerId, groupId);
        if ("READY".equals(group.getStatus()) || "RUNNING".equals(group.getStatus())) {
            return GroupResponse.from(group);
        }
        if (!"DRAFT".equals(group.getStatus()) && !"RECRUITING".equals(group.getStatus())) {
            throw new BadRequestException("Group cannot start from state " + group.getStatus());
        }
        long assigned = shareRepository.countByGroupId(groupId);
        if (assigned != group.getShareCount()) {
            throw new BadRequestException("Cannot start: assigned shares " + assigned + " != shareCount " + group.getShareCount());
        }
        if (!"BIDDING".equals(group.getType()) && !"FIXED".equals(group.getType())) {
            throw new BadRequestException("Invalid group type " + group.getType());
        }
        group.setStatus("READY");
        group.setRulesFrozen(true);
        groupRepository.save(group);
        return GroupResponse.from(group);
    }

    private Group findGroup(Long ownerId, Long groupId) {
        return groupRepository.findByOwnerIdAndId(ownerId, groupId)
                .orElseThrow(() -> new NotFoundException("Group not found"));
    }

    private void checkEditableShares(Group group) {
        if (!"DRAFT".equals(group.getStatus()) && !"RECRUITING".equals(group.getStatus())) {
            throw new BadRequestException("Shares can only be assigned before the group starts");
        }
        if (group.isRulesFrozen()) {
            throw new BadRequestException("Rules are frozen; shares can no longer change");
        }
    }
}