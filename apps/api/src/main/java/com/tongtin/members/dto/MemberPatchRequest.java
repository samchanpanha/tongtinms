package com.tongtin.members.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record MemberPatchRequest(
        @Size(min = 2, max = 120) String fullName,
        @Pattern(regexp = "^(0\\d{9}|\\+84\\d{9})$") String phone,
        @Size(max = 500) String note,
        @Pattern(regexp = "^(ACTIVE|INACTIVE|BLOCKED)$") String status) {
}
