package com.tongtin.members.blacklist.service;

import com.tongtin.common.errors.ConflictException;
import com.tongtin.common.errors.NotFoundException;
import com.tongtin.common.util.PhoneUtil;
import com.tongtin.identity.entity.AuditEvent;
import com.tongtin.identity.repository.AuditEventRepository;
import com.tongtin.members.blacklist.dto.BlacklistAddRequest;
import com.tongtin.members.blacklist.dto.BlacklistResponse;
import com.tongtin.members.blacklist.entity.MemberBlacklist;
import com.tongtin.members.blacklist.repository.MemberBlacklistRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Step 33: owner-local phone blacklist (06-SETTINGS-TEMPLATES-RISK.md §4.1).
 * Scoped to one owner only; unlisting is a soft {@code active = false} so the
 * audit history is never destroyed. Adding a member or assigning a share checks
 * {@link #activeReason(Long, String)} and rejects with 409 when the phone is listed.
 */
@Service
public class BlacklistService {

    private final MemberBlacklistRepository blacklistRepository;
    private final AuditEventRepository auditEventRepository;

    public BlacklistService(MemberBlacklistRepository blacklistRepository,
                            AuditEventRepository auditEventRepository) {
        this.blacklistRepository = blacklistRepository;
        this.auditEventRepository = auditEventRepository;
    }

    @Transactional(readOnly = true)
    public List<BlacklistResponse> list(Long ownerId) {
        return blacklistRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId)
                .stream().map(BlacklistResponse::from).toList();
    }

    @Transactional
    public BlacklistResponse add(Long ownerId, Long actorUserId, BlacklistAddRequest request) {
        String phone = PhoneUtil.normalize(request.phone());
        String reason = blankToNull(request.reason());

        MemberBlacklist entry = blacklistRepository.findByOwnerIdAndPhone(ownerId, phone).orElse(null);
        if (entry != null && entry.isActive()) {
            throw new ConflictException("phone is already blacklisted" + reasonSuffix(entry.getReason()));
        }
        if (entry == null) {
            entry = new MemberBlacklist();
            entry.setOwnerId(ownerId);
            entry.setPhone(phone);
        }
        entry.setReason(reason);
        entry.setActive(true);
        entry.setCreatedByUserId(actorUserId);
        entry.setUpdatedAt(Instant.now());
        MemberBlacklist saved = blacklistRepository.saveAndFlush(entry);

        audit(actorUserId, saved, "BLACKLIST_ADDED");
        return BlacklistResponse.from(saved);
    }

    @Transactional
    public BlacklistResponse unlist(Long ownerId, Long actorUserId, Long id) {
        MemberBlacklist entry = blacklistRepository.findByOwnerIdAndId(ownerId, id)
                .orElseThrow(() -> new NotFoundException("blacklist entry not found"));
        if (entry.isActive()) {
            entry.setActive(false);
            entry.setUpdatedAt(Instant.now());
            blacklistRepository.save(entry);
            audit(actorUserId, entry, "BLACKLIST_REMOVED");
        }
        return BlacklistResponse.from(entry);
    }

    /**
     * Active reason for a phone on this owner's list, or empty when not listed.
     * Callers reject onboarding (member create / share assign) when present.
     */
    @Transactional(readOnly = true)
    public Optional<String> activeReason(Long ownerId, String rawPhone) {
        String phone = PhoneUtil.normalize(rawPhone);
        return blacklistRepository.findByOwnerIdAndPhone(ownerId, phone)
                .filter(MemberBlacklist::isActive)
                .map(entry -> entry.getReason() == null ? "blacklisted" : entry.getReason());
    }

    private void audit(Long actorUserId, MemberBlacklist entry, String action) {
        String payload = "{\"phone\":\"" + entry.getPhone() + "\""
                + (entry.getReason() == null ? "" : ",\"reason\":\"" + escape(entry.getReason()) + "\"")
                + "}";
        auditEventRepository.save(AuditEvent.of(
                actorUserId, "MemberBlacklist", String.valueOf(entry.getId()), action, payload));
    }

    private static String reasonSuffix(String reason) {
        return reason == null || reason.isBlank() ? "" : ": " + reason;
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
