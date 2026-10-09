package com.tongtin.khqr;

import com.tongtin.common.money.Money;
import com.tongtin.common.money.MoneyLookup;
import com.tongtin.groups.entity.Group;
import com.tongtin.groups.repository.GroupRepository;
import com.tongtin.groups.shares.repository.GroupShareRepository;
import com.tongtin.identity.entity.User;
import com.tongtin.identity.repository.UserRepository;
import com.tongtin.ledger.entity.LedgerEntry;
import com.tongtin.ledger.payments.repository.PaymentAllocationRepository;
import com.tongtin.ledger.repository.LedgerEntryRepository;
import com.tongtin.members.entity.MemberProfile;
import com.tongtin.members.repository.MemberProfileRepository;
import com.tongtin.settings.service.SettingsService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Step 34: obligation KHQR payloads for debts rows, statement entries and the
 * PNG render endpoint. Contents are display convenience only — nothing here is
 * ever read back as money. Scope: a HOST may QR their own group's obligations,
 * a MEMBER only their own share's; disabled → payload absent / render 404.
 */
@Service
public class ObligationQrService {

    static final String SETTING_ENABLED = "payments_khqr_enabled";
    static final boolean DEFAULT_ENABLED = true;
    static final String CITY = "PHNOM PENH";

    private static final Logger log = LoggerFactory.getLogger(ObligationQrService.class);

    private final SettingsService settingsService;
    private final MoneyLookup moneyLookup;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final GroupRepository groupRepository;
    private final PaymentAllocationRepository allocationRepository;
    private final UserRepository userRepository;
    private final MemberProfileRepository memberProfileRepository;
    private final GroupShareRepository shareRepository;

    public ObligationQrService(SettingsService settingsService,
                               MoneyLookup moneyLookup,
                               LedgerEntryRepository ledgerEntryRepository,
                               GroupRepository groupRepository,
                               PaymentAllocationRepository allocationRepository,
                               UserRepository userRepository,
                               MemberProfileRepository memberProfileRepository,
                               GroupShareRepository shareRepository) {
        this.settingsService = settingsService;
        this.moneyLookup = moneyLookup;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.groupRepository = groupRepository;
        this.allocationRepository = allocationRepository;
        this.userRepository = userRepository;
        this.memberProfileRepository = memberProfileRepository;
        this.shareRepository = shareRepository;
    }

    public boolean enabled() {
        return settingsService.getBoolean(SETTING_ENABLED, DEFAULT_ENABLED);
    }

    /**
     * Core payload builder shared by debts, statement and render. Returns null
     * when QR is disabled or the currency has no ISO 4217 numeric code.
     */
    public String payload(String groupCode, Long entryId, String merchantName,
                          long remainingMinor, String currency) {
        if (!enabled()) {
            return null;
        }
        try {
            Money money = moneyLookup.money(currency, remainingMinor);
            String reference = "TONGTIN " + groupCode + "-O" + entryId;
            return KhqrGenerator.generate(merchantName, CITY, remainingMinor,
                    currency, money.exponent(), reference);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            log.warn("KHQR payload failed for group {} entry {}", groupCode, entryId, ex);
            return null;
        }
    }

    /**
     * Scope-checked payload for the render endpoint: entry must exist and belong
     * to the caller (hosts: own group; members: own share). Returns null for
     * unknown/cross-scope entries and when QR is disabled.
     */
    @Transactional(readOnly = true)
    public String renderable(String role, Long userId, Long ownerId, Long entryId) {
        if (!enabled()) {
            return null;
        }
        LedgerEntry entry = ledgerEntryRepository.findById(entryId).orElse(null);
        if (entry == null || entry.getShareId() == null) {
            return null;
        }
        Group group = groupRepository.findById(entry.getGroupId()).orElse(null);
        if (group == null) {
            return null;
        }
        if ("HOST".equals(role)) {
            if (ownerId == null || !ownerId.equals(group.getOwnerId())) {
                return null;
            }
        } else if (!ownsShare(userId, group.getId(), entry.getShareId())) {
            return null;
        }
        long remaining = Math.max(0, entry.getAmountMinor() - allocated(entry.getId()));
        if (!"IN".equals(entry.getDirection()) || remaining <= 0) {
            return null;
        }
        return payload(group.getCode(), entry.getId(), group.getName(), remaining, entry.getCurrency());
    }

    private boolean ownsShare(Long userId, Long groupId, Long shareId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return false;
        }
        List<Long> profileIds = memberProfileRepository.findByPhone(user.getPhone()).stream()
                .map(MemberProfile::getId)
                .toList();
        if (profileIds.isEmpty()) {
            return false;
        }
        List<Long> shareIds = shareRepository.findByGroupIdAndMemberProfileIdIn(groupId, profileIds).stream()
                .map(share -> share.getId())
                .toList();
        return shareIds.contains(shareId);
    }

    private long allocated(Long entryId) {
        List<Object[]> rows = allocationRepository.sumAllocatedByEntryIds(List.of(entryId));
        if (rows.isEmpty()) {
            return 0L;
        }
        return ((Number) rows.get(0)[1]).longValue();
    }
}