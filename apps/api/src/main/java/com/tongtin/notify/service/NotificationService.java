package com.tongtin.notify.service;

import com.tongtin.common.errors.NotFoundException;
import com.tongtin.groups.shares.repository.GroupShareRepository;
import com.tongtin.identity.entity.User;
import com.tongtin.identity.repository.UserRepository;
import com.tongtin.members.entity.MemberProfile;
import com.tongtin.members.repository.MemberProfileRepository;
import com.tongtin.notify.entity.Notification;
import com.tongtin.notify.dto.NotificationListResponse;
import com.tongtin.notify.dto.NotificationResponse;
import com.tongtin.notify.repository.NotificationRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Step 14 in-app notifications (01-DOMAIN §13).
 *
 * Invariant: notification failures must NEVER mutate financial state. Every write
 * is wrapped in try/catch, so a notification insert can never roll back the
 * surrounding business transaction.
 *
 * Recipients are users with logins. Paper member profiles without a `users` row
 * are skipped silently. Notifications are created inside the SAME transaction as
 * the business operation so they commit (or roll back) together.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final MemberProfileRepository memberProfileRepository;
    private final GroupShareRepository shareRepository;

    public NotificationService(NotificationRepository notificationRepository,
                               UserRepository userRepository,
                               MemberProfileRepository memberProfileRepository,
                               GroupShareRepository shareRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.memberProfileRepository = memberProfileRepository;
        this.shareRepository = shareRepository;
    }

    /** Best-effort single notification by user id. Never throws. */
    public void notifyUser(Long userId, String type, String title, String body) {
        if (userId == null) {
            return;
        }
        try {
            notificationRepository.save(new Notification(
                    userId, truncate(type, 32), truncate(title, 120), truncate(body, 500)));
        } catch (RuntimeException ex) {
            log.warn("Notification write failed for user {} (type {}): {}", userId, type, ex.getMessage());
        }
    }

    /** Notify every member (with a login) of a group. */
    public void notifyGroupMembers(Long groupId, String type, String title, String body) {
        List<Long> profileIds = shareRepository.findByGroupIdOrderByShareNo(groupId)
                .stream()
                .map(s -> s.getMemberProfileId())
                .distinct()
                .toList();
        notifyMemberProfiles(profileIds, type, title, body);
    }

    /** Notify members holding the given share ids (payment context lives on shares). */
    public void notifyShareOwners(Collection<Long> shareIds, String type, String title, String body) {
        notifyMemberProfiles(shareRepository.findAllById(shareIds)
                .stream()
                .map(s -> s.getMemberProfileId())
                .distinct()
                .toList(), type, title, body);
    }

    public void notifyMemberProfile(Long memberProfileId, String type, String title, String body) {
        memberProfileRepository.findById(memberProfileId)
                .ifPresent(profile -> createUsersByPhone(profile.getPhone(), type, title, body));
    }

    public void notifyMemberProfiles(Collection<Long> profileIds, String type, String title, String body) {
        if (profileIds.isEmpty()) {
            return;
        }
        List<String> phones = memberProfileRepository.findAllById(profileIds)
                .stream()
                .map(MemberProfile::getPhone)
                .distinct()
                .toList();
        createUsersByPhone(phones, type, title, body);
    }

    private void createUsersByPhone(String phone, String type, String title, String body) {
        createUsersByPhone(List.of(phone), type, title, body);
    }

    private void createUsersByPhone(Collection<String> phones, String type, String title, String body) {
        Set<Long> userIds = new LinkedHashSet<>();
        for (String phone : phones) {
            userRepository.findByPhone(phone).map(User::getId).ifPresent(userIds::add);
        }
        for (Long userId : userIds) {
            notifyUser(userId, type, title, body);
        }
    }

    @Transactional(readOnly = true)
    public NotificationListResponse list(Long userId) {
        List<NotificationResponse> feed = notificationRepository
                .findTop100ByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(NotificationResponse::from)
                .toList();
        return new NotificationListResponse(feed, unreadCount(userId));
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return notificationRepository.countByUserIdAndReadAtIsNull(userId);
    }

    @Transactional
    public NotificationResponse markRead(Long userId, Long notificationId) {
        Notification n = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new NotFoundException("notification not found"));
        if (n.getReadAt() == null) {
            n.setReadAt(Instant.now());
            notificationRepository.save(n);
        }
        return NotificationResponse.from(n);
    }

    @Transactional
    public int markAllRead(Long userId) {
        return notificationRepository.markAllRead(userId, Instant.now());
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}