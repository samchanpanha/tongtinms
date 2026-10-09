package com.tongtin.members.blacklist.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record BlacklistAddRequest(
        @Pattern(regexp = "^(0\\d{9}|\\+84\\d{9})$", message = "invalid phone") String phone,
        @Size(max = 500) String reason) {
}
