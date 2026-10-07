package com.tongtin.ledger.payments.service;

import com.tongtin.common.errors.BadRequestException;
import com.tongtin.common.errors.ConflictException;
import com.tongtin.common.errors.NotFoundException;
import com.tongtin.cycles.entity.Cycle;
import com.tongtin.cycles.repository.CycleRepository;
import com.tongtin.groups.entity.Group;
import com.tongtin.groups.repository.GroupRepository;
import com.tongtin.identity.service.AuditService;
import com.tongtin.ledger.entity.LedgerEntry;
import com.tongtin.ledger.payments.dto.DebtResponse;
import com.tongtin.ledger.payments.dto.PaymentCreateRequest;
import com.tongtin.ledger.payments.dto.PaymentResponse;
import com.tongtin.ledger.payments.entity.Payment;
import com.tongtin.ledger.payments.entity.PaymentAllocation;
import com.tongtin.ledger.payments.repository.PaymentAllocationRepository;
import com.tongtin.ledger.payments.repository.PaymentRepository;
import com.tongtin.ledger.repository.LedgerEntryRepository;
import com.tongtin.notify.service.NotificationService;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Step 11: host records a real-world payment and allocates it to obligations.
 *
 * Rules (01-DOMAIN §10, 07-MULTI-CURRENCY §4.5/§8):
 *   - payment currency must equal group currency (else 409)
 *   - allocations must sum exactly to the payment amount (user decision 2026-10-06)
 *   - an obligation can never be over-paid: allocated + new <= amount_minor
 *   - partial coverage -> PARTIAL, full coverage -> PAID
 *   - ledger rows are never rewritten except status; late fees deferred past Step 11
 */
@Service
public class PaymentService {

    private final GroupRepository groupRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentAllocationRepository allocationRepository;
    private final CycleRepository cycleRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;

    public PaymentService(GroupRepository groupRepository,
                          LedgerEntryRepository ledgerEntryRepository,
                          PaymentRepository paymentRepository,
                          PaymentAllocationRepository allocationRepository,
                          CycleRepository cycleRepository,
                          NotificationService notificationService,
                          AuditService auditService) {
        this.groupRepository = groupRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.paymentRepository = paymentRepository;
        this.allocationRepository = allocationRepository;
        this.cycleRepository = cycleRepository;
        this.notificationService = notificationService;
        this.auditService = auditService;
    }

    @Transactional
    public PaymentResponse record(Long userId, Long ownerId, Long groupId,
                                  PaymentCreateRequest request, String idempotencyKey) {
        Group group = findGroup(ownerId, groupId);
        normalizeKey(idempotencyKey);
        idempotencyKey = blankToNull(idempotencyKey);

        Payment replay = idempotencyKey == null ? null
                : paymentRepository.findByGroupIdAndIdempotencyKey(groupId, idempotencyKey).orElse(null);
        if (replay != null) {
            return replayOrConflict(replay, request);
        }

        if (!group.getCurrency().equalsIgnoreCase(request.currency())) {
            throw new ConflictException("Payment currency must equal group currency " + group.getCurrency());
        }

        long sum = request.allocations().stream().mapToLong(PaymentCreateRequest.Allocation::amountMinor).sum();
        if (sum != request.amountMinor()) {
            throw new BadRequestException("Allocation amounts must sum exactly to the payment amount");
        }
        long distinctEntries = request.allocations().stream()
                .map(PaymentCreateRequest.Allocation::ledgerEntryId).distinct().count();
        if (distinctEntries != request.allocations().size()) {
            throw new BadRequestException("Duplicate ledger entry in allocations");
        }

        List<Long> entryIds = request.allocations().stream()
                .map(PaymentCreateRequest.Allocation::ledgerEntryId)
                .sorted()
                .toList();
        List<LedgerEntry> owned = ledgerEntryRepository.findByGroupIdAndIdIn(groupId, entryIds);
        if (owned.size() != entryIds.size()) {
            throw new NotFoundException("Ledger entry not found in this group");
        }

        Map<Long, LedgerEntry> locked = lockInOrder(entryIds);
        Map<Long, Long> allocatedSoFar = allocatedByEntry(entryIds);

        for (Long entryId : entryIds) {
            LedgerEntry entry = locked.get(entryId);
            if (!"UNPAID".equals(entry.getStatus()) && !"PARTIAL".equals(entry.getStatus())) {
                throw new BadRequestException("Obligation " + entryId + " is " + entry.getStatus() + " and cannot receive payment");
            }
            long remaining = entry.getAmountMinor() - allocatedSoFar.getOrDefault(entryId, 0L);
            long wanted = request.allocations().stream()
                    .filter(a -> a.ledgerEntryId().equals(entryId))
                    .mapToLong(PaymentCreateRequest.Allocation::amountMinor)
                    .sum();
            if (wanted > remaining) {
                throw new BadRequestException("Allocation over-pays obligation " + entryId
                        + ": remaining is " + remaining);
            }
        }

        Payment payment = new Payment();
        payment.setGroupId(group.getId());
        payment.setReceivedByHostId(ownerId);
        payment.setAmountMinor(request.amountMinor());
        payment.setCurrency(group.getCurrency());
        payment.setMethod(request.method() != null && !request.method().isBlank() ? request.method() : "CASH");
        payment.setPaidAt(parsePaidAt(request.paidAt()));
        payment.setNote(request.note());
        payment.setIdempotencyKey(idempotencyKey);
        try {
            paymentRepository.save(payment);
            paymentRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("Idempotency key already used for this group");
        }

        List<PaymentAllocation> saved = new ArrayList<>();
        for (PaymentCreateRequest.Allocation allocation : request.allocations()) {
            PaymentAllocation row = new PaymentAllocation();
            row.setPaymentId(payment.getId());
            row.setLedgerEntryId(allocation.ledgerEntryId());
            row.setAmountMinor(allocation.amountMinor());
            saved.add(allocationRepository.save(row));

            LedgerEntry entry = locked.get(allocation.ledgerEntryId());
            long newAllocated = allocatedSoFar.getOrDefault(entry.getId(), 0L) + allocation.amountMinor();
            entry.setStatus(newAllocated >= entry.getAmountMinor() ? "PAID" : "PARTIAL");
            ledgerEntryRepository.save(entry);
        }
        notifyPayerMembers(group, request, locked);
        auditService.record(userId, "Payment", payment.getId(), "PAYMENT_RECORDED",
                Map.of("groupId", group.getId(),
                        "amountMinor", request.amountMinor(),
                        "currency", group.getCurrency(),
                        "method", payment.getMethod(),
                        "paidAt", payment.getPaidAt().toString(),
                        "allocations", allocationsView(request.allocations())));
        return PaymentResponse.from(payment, saved);
    }

    @Transactional(readOnly = true)
    public PaymentResponse findReplayOrConflict(Long ownerId, Long groupId, PaymentCreateRequest request,
                                                String idempotencyKey) {
        if (blankToNull(idempotencyKey) == null) {
            return null;
        }
        findGroup(ownerId, groupId);
        Payment payment = paymentRepository.findByGroupIdAndIdempotencyKey(groupId, idempotencyKey.trim())
                .orElse(null);
        if (payment == null) {
            return null;
        }
        return replayOrConflict(payment, request);
    }

    private PaymentResponse replayOrConflict(Payment replay, PaymentCreateRequest request) {
        Map<Long, Long> existing = allocationRepository.findByPaymentId(replay.getId()).stream()
                .collect(Collectors.toMap(PaymentAllocation::getLedgerEntryId, PaymentAllocation::getAmountMinor));
        Map<Long, Long> incoming = request.allocations().stream()
                .collect(Collectors.toMap(PaymentCreateRequest.Allocation::ledgerEntryId,
                        PaymentCreateRequest.Allocation::amountMinor));
        boolean same = replay.getAmountMinor() == request.amountMinor()
                && replay.getCurrency().equalsIgnoreCase(request.currency())
                && replay.getMethod().equalsIgnoreCase(
                        request.method() != null && !request.method().isBlank() ? request.method() : "CASH")
                && existing.equals(incoming);
        if (!same) {
            throw new ConflictException("Idempotency key already used with a different payload");
        }
        return PaymentResponse.from(replay, allocationRepository.findByPaymentId(replay.getId()));
    }

    private static List<Map<String, Object>> allocationsView(List<PaymentCreateRequest.Allocation> allocations) {
        return allocations.stream()
                .map(a -> Map.<String, Object>of("ledgerEntryId", a.ledgerEntryId(), "amountMinor", a.amountMinor()))
                .toList();
    }

    private static void normalizeKey(String idempotencyKey) {
        if (idempotencyKey != null && idempotencyKey.length() > 64) {
            throw new BadRequestException("Idempotency key must be at most 64 characters");
        }
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void notifyPayerMembers(Group group, PaymentCreateRequest request,
                                    Map<Long, LedgerEntry> locked) {
        TreeSet<Long> profileIds = new TreeSet<>();
        for (PaymentCreateRequest.Allocation allocation : request.allocations()) {
            LedgerEntry entry = locked.get(allocation.ledgerEntryId());
            if (entry.getMemberProfileId() != null) {
                profileIds.add(entry.getMemberProfileId());
            }
        }
        if (profileIds.isEmpty()) {
            return;
        }
        notificationService.notifyMemberProfiles(profileIds, "PAYMENT_CONFIRMED",
                "Da ghi nhan tien",
                String.format("Host da ghi nhan %d d cua ban vao hoi \"%s\".",
                        request.amountMinor(), group.getName()));
    }

    @Transactional(readOnly = true)
    public List<DebtResponse> debts(Long ownerId, Long groupId) {
        Group group = findGroup(ownerId, groupId);
        Instant now = Instant.now();
        List<LedgerEntry> overdue = ledgerEntryRepository.findOverdueByGroup(group.getId(), now);
        if (overdue.isEmpty()) {
            return List.of();
        }
        List<Long> entryIds = overdue.stream().map(LedgerEntry::getId).toList();
        Map<Long, Long> allocated = allocatedByEntry(entryIds);
        Map<Long, Integer> cycleNo = cycleRepository.findByGroupIdOrderByCycleNo(group.getId())
                .stream()
                .collect(Collectors.toMap(Cycle::getId, Cycle::getCycleNo));

        return overdue.stream()
                .map(entry -> {
                    long used = allocated.getOrDefault(entry.getId(), 0L);
                    return new DebtResponse(
                            entry.getId(),
                            entry.getCycleId(),
                            cycleNo.getOrDefault(entry.getCycleId(), 0),
                            entry.getShareId(),
                            entry.getMemberProfileId(),
                            entry.getType(),
                            entry.getDirection(),
                            entry.getAmountMinor(),
                            used,
                            entry.getAmountMinor() - used,
                            entry.getCurrency(),
                            entry.getStatus(),
                            entry.getDueAt(),
                            Duration.between(entry.getDueAt(), now).toDays());
                })
                .toList();
    }

    private Map<Long, LedgerEntry> lockInOrder(List<Long> sortedEntryIds) {
        Map<Long, LedgerEntry> locked = new HashMap<>();
        for (Long entryId : sortedEntryIds) {
            LedgerEntry entry = ledgerEntryRepository.findByIdForUpdate(entryId)
                    .orElseThrow(() -> new NotFoundException("Ledger entry not found"));
            locked.put(entryId, entry);
        }
        return locked;
    }

    private Map<Long, Long> allocatedByEntry(List<Long> entryIds) {
        return allocationRepository.sumAllocatedByEntryIds(entryIds)
                .stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
    }

    private Instant parsePaidAt(String paidAt) {
        if (paidAt == null || paidAt.isBlank()) {
            return Instant.now();
        }
        try {
            return Instant.parse(paidAt);
        } catch (DateTimeParseException ex) {
            throw new BadRequestException("paidAt must be an ISO-8601 instant");
        }
    }

    private Group findGroup(Long ownerId, Long groupId) {
        return groupRepository.findByOwnerIdAndId(ownerId, groupId)
                .orElseThrow(() -> new NotFoundException("Group not found"));
    }
}
