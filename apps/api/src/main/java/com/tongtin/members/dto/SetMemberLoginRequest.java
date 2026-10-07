package com.tongtin.members.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SetMemberLoginRequest(
        @NotBlank @Size(min = 8, max = 72, message = "password must be 8-72 characters") String password) {
}