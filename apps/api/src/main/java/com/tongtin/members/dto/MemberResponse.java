package com.tongtin.members.dto;

import com.tongtin.attachments.dto.AttachmentResponse;
import com.tongtin.members.entity.MemberProfile;
import java.time.Instant;
import java.util.List;

public record MemberResponse(
        Long id,
        Long ownerId,
        String fullName,
        String phone,
        String note,
        String status,
        Instant createdAt,
        List<AttachmentResponse> attachments) {

    public static MemberResponse from(MemberProfile member) {
        return from(member, List.of());
    }

    public static MemberResponse from(MemberProfile member, List<AttachmentResponse> attachments) {
        return new MemberResponse(
                member.getId(),
                member.getOwnerId(),
                member.getFullName(),
                member.getPhone(),
                member.getNote(),
                member.getStatus(),
                member.getCreatedAt(),
                attachments == null ? List.of() : attachments);
    }
}
