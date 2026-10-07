package com.tongtin.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterOwnerRequest(
        @NotBlank @Size(min = 2, max = 120) String fullName,
        @NotBlank @Pattern(regexp = "^(0\\d{9}|\\+84\\d{9})$", message = "phone must be 0xxxxxxxxx or +84xxxxxxxxx")
                String phone,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank String confirmPassword,
        @Email @Size(max = 254) String email,
        @Size(max = 120) String displayName,
        boolean acceptTerms) {
}
