package com.tongtin.ledger.payments.dto;

import com.tongtin.ledger.payments.entity.Payment;
import com.tongtin.ledger.payments.entity.PaymentAllocation;
import java.time.Instant;
import java.util.List;

public record PaymentResponse(
        Long id,
        Long groupId,
        long amountMinor,
        String currency,
        String method,
        Instant paidAt,
        String note,
        Instant createdAt,
        List<Allocation> allocations) {

    public record Allocation(Long ledgerEntryId, long amountMinor) {
    }

    public static PaymentResponse from(Payment payment, List<PaymentAllocation> allocations) {
        return new PaymentResponse(
                payment.getId(),
                payment.getGroupId(),
                payment.getAmountMinor(),
                payment.getCurrency(),
                payment.getMethod(),
                payment.getPaidAt(),
                payment.getNote(),
                payment.getCreatedAt(),
                allocations.stream()
                        .map(a -> new Allocation(a.getLedgerEntryId(), a.getAmountMinor()))
                        .toList());
    }
}
