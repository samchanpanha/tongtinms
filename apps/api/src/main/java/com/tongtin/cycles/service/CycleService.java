package com.tongtin.cycles.service;

import com.tongtin.cycles.dto.CycleResponse;
import com.tongtin.cycles.entity.Cycle;
import com.tongtin.cycles.repository.CycleRepository;
import com.tongtin.common.errors.BadRequestException;
import com.tongtin.common.errors.NotFoundException;
import com.tongtin.groups.entity.Group;
import com.tongtin.groups.repository.GroupRepository;
import com.tongtin.identity.service.AuditService;
import com.tongtin.notify.service.NotificationService;
import com.tongtin.telegram.TelegramMessages;
import com.tongtin.telegram.TelegramNotifier;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CycleService {

    private final CycleRepository cycleRepository;
    private final GroupRepository groupRepository;
    private final NotificationService notificationService;
    private final TelegramNotifier telegramNotifier;
    private final AuditService auditService;

    public CycleService(CycleRepository cycleRepository,
                        GroupRepository groupRepository,
                        NotificationService notificationService,
                        TelegramNotifier telegramNotifier,
                        AuditService auditService) {
        this.cycleRepository = cycleRepository;
        this.groupRepository = groupRepository;
        this.notificationService = notificationService;
        this.telegramNotifier = telegramNotifier;
        this.auditService = auditService;
    }

    @Transactional
    public CycleResponse open(Long userId, Long ownerId, Long groupId) {
        Group group = findGroup(ownerId, groupId);

        int nextNo = cycleRepository.findTopByGroupIdOrderByCycleNoDesc(groupId)
                .map(Cycle::getCycleNo)
                .map(n -> n + 1)
                .orElse(1);

        if (nextNo > group.getCycleCount()) {
            throw new BadRequestException("All cycles are complete");
        }

        if (nextNo == 1) {
            if (!"READY".equals(group.getStatus())) {
                throw new BadRequestException("Cannot open first cycle: group is " + group.getStatus());
            }
        } else {
            if (!"RUNNING".equals(group.getStatus())) {
                throw new BadRequestException("Cannot open cycle " + nextNo + ": group is " + group.getStatus());
            }
            Cycle previous = cycleRepository.findByGroupIdAndCycleNo(groupId, nextNo - 1)
                    .orElseThrow(() -> new IllegalStateException("Missing previous cycle"));
            if (!"SETTLED".equals(previous.getStatus())) {
                throw new BadRequestException("Previous cycle must be SETTLED before opening the next");
            }
        }

        Cycle cycle = new Cycle();
        cycle.setGroupId(groupId);
        cycle.setCycleNo(nextNo);
        cycle.setCurrency(group.getCurrency());
        if ("FIXED".equals(group.getType()) || nextNo == group.getCycleCount()) {
            cycle.setStatus("OPEN");
        } else {
            cycle.setStatus("BIDDING");
        }
        Instant openAt = Instant.now();
        cycle.setBidCloseAt(openAt.plus(Duration.ofDays(group.getBidCloseOffset())));
        cycle.setDueAt(openAt.plus(cycleDuration(group.getCycleUnit())));
        cycleRepository.save(cycle);

        if (!"RUNNING".equals(group.getStatus())) {
            group.setStatus("RUNNING");
            groupRepository.save(group);
        }
        notificationService.notifyGroupMembers(groupId, "CYCLE_OPENED",
                "Ky " + nextNo + " mo",
                of("Ky %d/%d cua hoi \"%s\" da mo. Han chot dau gia: %s.",
                        nextNo, group.getCycleCount(), group.getName(), cycle.getBidCloseAt()));
        telegramNotifier.notifyGroupOwner(group, "CYCLE_OPENED",
                TelegramMessages.cycleOpened(group, nextNo, group.getCycleCount(), cycle.getBidCloseAt()));
        auditService.record(userId, "Cycle", cycle.getId(), "CYCLE_OPENED",
                Map.of("cycleNo", cycle.getCycleNo(),
                        "status", cycle.getStatus(),
                        "bidCloseAt", cycle.getBidCloseAt().toString(),
                        "dueAt", cycle.getDueAt().toString()));
        return CycleResponse.from(cycle);
    }

    private static String of(String pattern, Object... args) {
        return String.format(pattern, args);
    }

    @Transactional(readOnly = true)
    public List<CycleResponse> listForGroup(Long ownerId, Long groupId) {
        findGroup(ownerId, groupId);
        return cycleRepository.findByGroupIdOrderByCycleNo(groupId)
                .stream().map(CycleResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public CycleResponse get(Long ownerId, Long cycleId) {
        Cycle cycle = cycleRepository.findById(cycleId)
                .orElseThrow(() -> new NotFoundException("Cycle not found"));
        Group group = findGroup(ownerId, cycle.getGroupId());
        return CycleResponse.from(cycle);
    }

    private Group findGroup(Long ownerId, Long groupId) {
        return groupRepository.findByOwnerIdAndId(ownerId, groupId)
                .orElseThrow(() -> new NotFoundException("Group not found"));
    }

    private Duration cycleDuration(String cycleUnit) {
        return switch (cycleUnit) {
            case "DAY" -> Duration.ofDays(1);
            case "WEEK" -> Duration.ofDays(7);
            case "MONTH" -> Duration.ofDays(30);
            default -> throw new IllegalArgumentException("Unknown cycle unit: " + cycleUnit);
        };
    }
}