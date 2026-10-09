package com.tongtin.ledger.payments.service;

import com.tongtin.common.errors.NotFoundException;
import com.tongtin.groups.entity.Group;
import com.tongtin.groups.repository.GroupRepository;
import com.tongtin.identity.entity.OwnerAccount;
import com.tongtin.identity.entity.User;
import com.tongtin.identity.repository.OwnerAccountRepository;
import com.tongtin.identity.repository.UserRepository;
import com.tongtin.ledger.entity.LedgerEntry;
import com.tongtin.ledger.payments.dto.InvoiceResponse;
import com.tongtin.ledger.payments.entity.Payment;
import com.tongtin.ledger.payments.entity.PaymentAllocation;
import com.tongtin.ledger.payments.repository.PaymentAllocationRepository;
import com.tongtin.ledger.payments.repository.PaymentRepository;
import com.tongtin.ledger.repository.LedgerEntryRepository;
import com.tongtin.members.entity.MemberProfile;
import com.tongtin.members.repository.MemberProfileRepository;
import com.tongtin.cycles.repository.CycleRepository;
import com.tongtin.cycles.entity.Cycle;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Step 38: Builds payment invoice data for a given payment.
 * Pure read service — no writes, no DB migrations needed.
 * Money is always long minor units; purely integer math.
 */
@Service
public class InvoiceService {

    private final PaymentRepository paymentRepository;
    private final PaymentAllocationRepository allocationRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final GroupRepository groupRepository;
    private final MemberProfileRepository memberProfileRepository;
    private final OwnerAccountRepository ownerAccountRepository;
    private final UserRepository userRepository;
    private final CycleRepository cycleRepository;

    public InvoiceService(PaymentRepository paymentRepository,
                          PaymentAllocationRepository allocationRepository,
                          LedgerEntryRepository ledgerEntryRepository,
                          GroupRepository groupRepository,
                          MemberProfileRepository memberProfileRepository,
                          OwnerAccountRepository ownerAccountRepository,
                          UserRepository userRepository,
                          CycleRepository cycleRepository) {
        this.paymentRepository = paymentRepository;
        this.allocationRepository = allocationRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.groupRepository = groupRepository;
        this.memberProfileRepository = memberProfileRepository;
        this.ownerAccountRepository = ownerAccountRepository;
        this.userRepository = userRepository;
        this.cycleRepository = cycleRepository;
    }

    @Transactional(readOnly = true)
    public InvoiceResponse invoice(Long ownerId, Long groupId, Long paymentId) {
        Group group = groupRepository.findByOwnerIdAndId(ownerId, groupId)
                .orElseThrow(() -> new NotFoundException("Group not found"));

        Payment payment = paymentRepository.findByIdAndGroupId(paymentId, groupId)
                .orElseThrow(() -> new NotFoundException("Payment not found"));

        List<PaymentAllocation> allocations = allocationRepository.findByPaymentIdOrderByLedgerEntryId(paymentId);

        // Gather ledger entries to enrich lines
        Set<Long> entryIds = allocations.stream()
                .map(PaymentAllocation::getLedgerEntryId)
                .collect(Collectors.toSet());
        Map<Long, LedgerEntry> entriesById = entryIds.isEmpty()
                ? Map.of()
                : ledgerEntryRepository.findByGroupIdAndIdIn(groupId, entryIds).stream()
                        .collect(Collectors.toMap(LedgerEntry::getId, Function.identity()));

        // cycle numbers for entry descriptions
        Map<Long, Integer> cycleNoById = cycleRepository.findByGroupIdOrderByCycleNo(groupId)
                .stream()
                .collect(Collectors.toMap(Cycle::getId, Cycle::getCycleNo));

        // Find payer: derive from member on the first CONTRIBUTION allocation
        MemberProfile payer = null;
        for (PaymentAllocation alloc : allocations) {
            LedgerEntry entry = entriesById.get(alloc.getLedgerEntryId());
            if (entry != null && entry.getMemberProfileId() != null) {
                payer = memberProfileRepository.findById(entry.getMemberProfileId()).orElse(null);
                if (payer != null) break;
            }
        }

        // Host info from OwnerAccount
        OwnerAccount owner = ownerAccountRepository.findById(ownerId)
                .orElseThrow(() -> new NotFoundException("Owner not found"));
        User hostUser = userRepository.findById(owner.getUserId()).orElse(null);

        // Build invoice lines
        List<InvoiceResponse.InvoiceLine> lines = new ArrayList<>();
        long totalAllocated = 0;
        for (PaymentAllocation alloc : allocations) {
            LedgerEntry entry = entriesById.get(alloc.getLedgerEntryId());
            if (entry == null) continue;
            Integer cycleNo = cycleNoById.get(entry.getCycleId());
            String desc = buildDescription(entry, cycleNo);
            lines.add(new InvoiceResponse.InvoiceLine(
                    desc,
                    cycleNo,
                    entry.getType(),
                    entry.getDirection(),
                    entry.getAmountMinor(),
                    alloc.getAmountMinor()));
            totalAllocated += alloc.getAmountMinor();
        }

        String invoiceNo = String.format("INV-%s-%d", group.getCode(), payment.getId());

        InvoiceResponse.GroupInfo groupInfo = new InvoiceResponse.GroupInfo(
                group.getId(), group.getCode(), group.getName(), group.getCurrency());

        InvoiceResponse.HostInfo hostInfo = new InvoiceResponse.HostInfo(
                owner.getDisplayName(),
                owner.getBankName(),
                owner.getBankAccount(),
                owner.getAccountHolder(),
                hostUser != null ? hostUser.getPhone() : null);

        InvoiceResponse.PayerInfo payerInfo = payer != null
                ? new InvoiceResponse.PayerInfo(payer.getId(), payer.getFullName(), payer.getPhone())
                : new InvoiceResponse.PayerInfo(null, "N/A", null);

        InvoiceResponse.PaymentInfo paymentInfo = new InvoiceResponse.PaymentInfo(
                payment.getAmountMinor(),
                payment.getCurrency(),
                payment.getMethod(),
                payment.getPaidAt(),
                payment.getNote());

        return new InvoiceResponse(
                invoiceNo,
                Instant.now(),
                groupInfo,
                hostInfo,
                payerInfo,
                paymentInfo,
                lines,
                totalAllocated,
                group.getCurrency());
    }

    private String buildDescription(LedgerEntry entry, Integer cycleNo) {
        String cycle = cycleNo != null ? "Ky " + cycleNo : "";
        return switch (entry.getType()) {
            case "CONTRIBUTION" -> cycle + " - Gop quy";
            case "PAYOUT" -> cycle + " - Nhan tien quy";
            case "HOST_FEE" -> cycle + " - Phi chu hoi";
            case "LATE_FEE" -> cycle + " - Phi tre han";
            default -> cycle + " - " + entry.getType();
        };
    }
}
