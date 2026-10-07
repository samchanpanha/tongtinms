package com.tongtin.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record LoginRequest(
        @NotBlank @Pattern(regexp = "^(0\\d{9}|\\+84\\d{9})$") String phone,
        @NotBlank String password) {
}
