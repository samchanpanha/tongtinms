package com.tongtin.members.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record MemberCreateRequest(
        @NotBlank @Size(min = 2, max = 120) String fullName,
        @NotBlank @Pattern(regexp = "^(0\\d{9}|\\+84\\d{9})$") String phone,
        @Size(max = 500) String note) {
}
