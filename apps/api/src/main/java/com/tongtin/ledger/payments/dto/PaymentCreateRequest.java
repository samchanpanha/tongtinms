package com.tongtin.ledger.payments.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record PaymentCreateRequest(
        @NotNull @Min(1) Long amountMinor,
        @NotBlank @Size(min = 3, max = 3) String currency,
        @Pattern(regexp = "^(CASH|BANK_TRANSFER|OTHER)$") String method,
        String paidAt,
        @Size(max = 250) String note,
        @NotEmpty @Valid List<Allocation> allocations) {

    public record Allocation(
            @NotNull Long ledgerEntryId,
            @NotNull @Min(1) Long amountMinor) {
    }
}
