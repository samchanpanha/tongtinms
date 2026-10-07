package com.tongtin.memberportal.dto;

import java.util.List;

/**
 * A group the member belongs to (member may hold shares in several groups/hosts).
 * Money is long minor units; the group currency rides on the same row.
 */
public record MemberGroupResponse(
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
        int myShareCount,
        List<MyShare> myShares) {

    public record MyShare(Long id, int shareNo, String status) {
    }
}