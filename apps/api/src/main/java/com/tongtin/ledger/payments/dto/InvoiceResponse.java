package com.tongtin.ledger.payments.dto;

import java.time.Instant;
import java.util.List;

/**
 * Step 38: Payment invoice data transfer object.
 * Read-only view of a payment with full host and payer context.
 * All money is long minor units paired with currency string (07-MULTI-CURRENCY invariant).
 */
public record InvoiceResponse(
        String invoiceNo,
        Instant issuedAt,
        GroupInfo group,
        HostInfo host,
        PayerInfo payer,
        PaymentInfo payment,
        List<InvoiceLine> lines,
        long totalAllocatedMinor,
        String currency) {

    public record GroupInfo(Long id, String code, String name, String currency) {}

    public record HostInfo(String displayName, String bankName, String bankAccount,
                           String accountHolder, String phone) {}

    public record PayerInfo(Long memberProfileId, String memberName, String phone) {}

    public record PaymentInfo(long amountMinor, String currency, String method,
                              Instant paidAt, String note) {}

    public record InvoiceLine(String description, Integer cycleNo, String type,
                              String direction, long amountMinor, long allocatedMinor) {}
}
