package com.tongtin.groups.dto;

import com.tongtin.groups.entity.Group;
import java.time.Instant;
import java.time.LocalDate;

public record GroupResponse(
        Long id,
        Long ownerId,
        String code,
        String name,
        String type,
        long baseAmount,
        int shareCount,
        String cycleUnit,
        int cycleCount,
        LocalDate startAt,
        String status,
        String hostFeeType,
        long hostFeeMinor,
        int hostFeeBps,
        long minBid,
        long maxBid,
        long bidStep,
        String tieBreak,
        String lateFeeType,
        long lateFeeValue,
        int bidOpenOffset,
        int bidCloseOffset,
        boolean allowMultiShare,
        String currency,
        boolean rulesFrozen,
        Instant createdAt) {

    public static GroupResponse from(Group group) {
        return new GroupResponse(
                group.getId(),
                group.getOwnerId(),
                group.getCode(),
                group.getName(),
                group.getType(),
                group.getBaseAmount(),
                group.getShareCount(),
                group.getCycleUnit(),
                group.getCycleCount(),
                group.getStartAt(),
                group.getStatus(),
                group.getHostFeeType(),
                group.getHostFeeMinor(),
                group.getHostFeeBps(),
                group.getMinBid(),
                group.getMaxBid(),
                group.getBidStep(),
                group.getTieBreak(),
                group.getLateFeeType(),
                group.getLateFeeValue(),
                group.getBidOpenOffset(),
                group.getBidCloseOffset(),
                group.isAllowMultiShare(),
                group.getCurrency(),
                group.isRulesFrozen(),
                group.getCreatedAt());
    }
}
