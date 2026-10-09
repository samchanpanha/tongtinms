package com.tongtin.members.blacklist.dto;

import com.tongtin.members.blacklist.entity.MemberBlacklist;
import java.time.Instant;

public record BlacklistResponse(
        Long id,
        String phone,
        String reason,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {

    public static BlacklistResponse from(MemberBlacklist entry) {
        return new BlacklistResponse(
                entry.getId(),
                entry.getPhone(),
                entry.getReason(),
                entry.isActive(),
                entry.getCreatedAt(),
                entry.getUpdatedAt());
    }
}
