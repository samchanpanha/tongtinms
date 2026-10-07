package com.tongtin.members.dto;

import com.tongtin.members.entity.MemberProfile;
import java.time.Instant;

public record MemberResponse(
        Long id,
        Long ownerId,
        String fullName,
        String phone,
        String note,
        String status,
        Instant createdAt) {

    public static MemberResponse from(MemberProfile member) {
        return new MemberResponse(
                member.getId(),
                member.getOwnerId(),
                member.getFullName(),
                member.getPhone(),
                member.getNote(),
                member.getStatus(),
                member.getCreatedAt());
    }
}
