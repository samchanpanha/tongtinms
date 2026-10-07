package com.tongtin.groups.shares.dto;

import com.tongtin.groups.shares.entity.GroupShare;
import java.time.Instant;

public record ShareResponse(
        Long id,
        Long groupId,
        Long memberProfileId,
        int shareNo,
        String status,
        Long wonCycleId,
        Instant createdAt) {

    public static ShareResponse from(GroupShare share) {
        return new ShareResponse(
                share.getId(),
                share.getGroupId(),
                share.getMemberProfileId(),
                share.getShareNo(),
                share.getStatus(),
                share.getWonCycleId(),
                share.getCreatedAt());
    }
}
