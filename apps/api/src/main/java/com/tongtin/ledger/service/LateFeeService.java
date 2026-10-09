package com.tongtin.ledger.service;

import com.tongtin.common.errors.NotFoundException;
import com.tongtin.groups.entity.Group;
import com.tongtin.groups.repository.GroupRepository;
import com.tongtin.identity.service.AuditService;
import com.tongtin.ledger.dto.LateFeeAssessResponse;
import com.tongtin.ledger.engine.FormulaEngine;
import com.tongtin.ledger.entity.LedgerEntry;
import com.tongtin.ledger.repository.LedgerEntryRepository;
import com.tongtin.notify.service.NotificationService;
import com.tongtin.telegram.TelegramMessages;
import com.tongtin.telegram.TelegramNotifier;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Step 29: host-triggered late-fee assessment (01-DOMAIN 9.6).
 *
 * Rules:
 *   - CONTRIBUTION obligations only (UNPAID/PARTIAL, dueAt passed); payout and
 *     host-fee rows are never late-fee'd, LATE_FEE rows are never re-fee'd
 *   - cumulative + idempotent: target = lateFee(...); rows already written for
 *     (cycle, share) are summed and only the positive delta is inserted
 *   - new row: type LATE_FEE, direction IN, status UNPAID, dueAt copied from
 *     the source obligation so it surfaces in the overdue debts list
 *   - no scheduled assessment: explicit POST only; lateFeeType NONE is a no-op
 */
@Service
public class LateFeeService {

    private final GroupRepository groupRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final TelegramNotifier telegramNotifier;

    public LateFeeService(GroupRepository groupRepository,
                          LedgerEntryRepository ledgerEntryRepository,
                          AuditService auditService,
                          NotificationService notificationService,
                          TelegramNotifier telegramNotifier) {
        this.groupRepository = groupRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.auditService = auditService;
        this.notificationService = notificationService;
        this.telegramNotifier = telegramNotifier;
    }

    @Transactional
    public LateFeeAssessResponse assess(Long userId, Long ownerId, Long groupId) {
        Group group = groupRepository.findByOwnerIdAndId(ownerId, groupId)
                .orElseThrow(() -> new NotFoundException("Group not found"));
        if ("NONE".equals(group.getLateFeeType())) {
            return LateFeeAssessResponse.empty();
        }

        Instant now = Instant.now();
        List<LedgerEntry> targets = ledgerEntryRepository.findOverdueByGroup(group.getId(), now).stream()
                .filter(e -> "CONTRIBUTION".equals(e.getType()))
                .sorted(Comparator.comparingLong(LedgerEntry::getId))
                .toList();
        if (targets.isEmpty()) {
            return LateFeeAssessResponse.empty();
        }

        List<LedgerEntry> locked = lockInOrder(targets);
        List<LateFeeAssessResponse.Entry> created = new ArrayList<>();
        TreeSet<Long> notifiedProfiles = new TreeSet<>();
        int assessed = 0;

        for (LedgerEntry source : locked) {
            // status may have changed between the overdue scan and the lock
            String status = ledgerEntryRepository.findStatusById(source.getId()).orElse(null);
            if (!"UNPAID".equals(status) && !"PARTIAL".equals(status)) {
                continue;
            }
            assessed++;
            long overdueDays = Duration.between(source.getDueAt(), now).toDays();
            long target = FormulaEngine.lateFee(
                    group.getLateFeeType(), group.getLateFeeValue(),
                    source.getAmountMinor(), overdueDays);
            long charged = ledgerEntryRepository.sumLateFeeByCycleAndShare(
                    source.getCycleId(), source.getShareId());
            long delta = target - charged;
            if (delta <= 0) {
                continue;
            }
            created.add(saveFeeRow(group, source, delta));
            if (source.getMemberProfileId() != null) {
                notifiedProfiles.add(source.getMemberProfileId());
            }
        }

        if (!created.isEmpty()) {
            long total = created.stream().mapToLong(LateFeeAssessResponse.Entry::amountMinor).sum();
            auditService.record(userId, "Group", group.getId(), "LATE_FEES_ASSESSED",
                    Map.of("groupId", group.getId(),
                            "assessed", assessed,
                            "created", created.size(),
                            "amountMinor", total,
                            "currency", group.getCurrency()));
            notificationService.notifyMemberProfiles(notifiedProfiles, "LATE_FEE_ASSESSED",
                    "Phi le tre han",
                    String.format("Hoi \"%s\" da tinh phi tre %d d cho ban.",
                            group.getName(), total));
            telegramNotifier.notifyGroupOwner(group, "LATE_FEE_ASSESSED",
                    TelegramMessages.lateFeesAssessed(group, total, notifiedProfiles.size()));
        }
        return new LateFeeAssessResponse(assessed, created.size(), List.copyOf(created));
    }

    private List<LedgerEntry> lockInOrder(List<LedgerEntry> targets) {
        List<LedgerEntry> locked = new ArrayList<>(targets.size());
        for (LedgerEntry target : targets) {
            locked.add(ledgerEntryRepository.findByIdForUpdate(target.getId())
                    .orElseThrow(() -> new NotFoundException("Ledger entry not found")));
        }
        return locked;
    }

    private LateFeeAssessResponse.Entry saveFeeRow(Group group, LedgerEntry source, long amount) {
        LedgerEntry fee = new LedgerEntry();
        fee.setGroupId(source.getGroupId());
        fee.setCycleId(source.getCycleId());
        fee.setShareId(source.getShareId());
        fee.setMemberProfileId(source.getMemberProfileId());
        fee.setType("LATE_FEE");
        fee.setDirection("IN");
        fee.setAmountMinor(amount);
        fee.setCurrency(group.getCurrency());
        fee.setStatus("UNPAID");
        fee.setDueAt(source.getDueAt());
        ledgerEntryRepository.save(fee);
        return new LateFeeAssessResponse.Entry(fee.getId(), fee.getCycleId(), fee.getShareId(),
                fee.getAmountMinor(), fee.getCurrency(), fee.getDueAt());
    }
}
