package com.tongtin.ledger.payments.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Step 36: host types a total for one member and the backend auto-allocates it
 * across the member's outstanding obligations (oldest due first, then entry id).
 */
public record QuickPayRequest(
        @NotNull Long memberProfileId,
        @NotNull @Min(1) Long amountMinor,
        @NotBlank @Size(min = 3, max = 3) String currency,
        @Pattern(regexp = "^(CASH|BANK_TRANSFER|OTHER)$") String method,
        String paidAt,
        @Size(max = 250) String note,
        Boolean allocateLateFees) {
}