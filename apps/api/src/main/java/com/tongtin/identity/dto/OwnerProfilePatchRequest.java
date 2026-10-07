package com.tongtin.identity.dto;

import jakarta.validation.constraints.Size;

public record OwnerProfilePatchRequest(
        @Size(max = 120) String displayName,
        @Size(max = 20) String cccd,
        @Size(max = 120) String bankName,
        @Size(max = 50) String bankAccount,
        @Size(max = 120) String accountHolder,
        @Size(max = 50) String zalo,
        @Size(max = 120) String city) {
}
