package com.tongtin.members.service;

import com.tongtin.common.errors.BadRequestException;
import com.tongtin.common.errors.ConflictException;
import com.tongtin.common.errors.NotFoundException;
import com.tongtin.common.util.PhoneUtil;
import com.tongtin.identity.entity.AuditEvent;
import com.tongtin.identity.entity.OwnerAccount;
import com.tongtin.identity.entity.User;
import com.tongtin.identity.entity.UserRole;
import com.tongtin.identity.repository.AuditEventRepository;
import com.tongtin.identity.repository.OwnerAccountRepository;
import com.tongtin.identity.repository.UserRepository;
import com.tongtin.identity.repository.UserRoleRepository;
import com.tongtin.members.dto.MemberCreateRequest;
import com.tongtin.members.dto.MemberPatchRequest;
import com.tongtin.members.dto.MemberResponse;
import com.tongtin.members.entity.MemberProfile;
import com.tongtin.members.repository.MemberProfileRepository;
import com.tongtin.notify.service.NotificationService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberService {

    @PersistenceContext
    private EntityManager entityManager;

    private final MemberProfileRepository memberProfileRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final OwnerAccountRepository ownerAccountRepository;
    private final AuditEventRepository auditEventRepository;
    private final PasswordEncoder passwordEncoder;
    private final NotificationService notificationService;

    public MemberService(MemberProfileRepository memberProfileRepository,
                         UserRepository userRepository,
                         UserRoleRepository userRoleRepository,
                         OwnerAccountRepository ownerAccountRepository,
                         AuditEventRepository auditEventRepository,
                         PasswordEncoder passwordEncoder,
                         NotificationService notificationService) {
        this.memberProfileRepository = memberProfileRepository;
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.ownerAccountRepository = ownerAccountRepository;
        this.auditEventRepository = auditEventRepository;
        this.passwordEncoder = passwordEncoder;
        this.notificationService = notificationService;
    }

    @Transactional
    public MemberResponse create(Long ownerId, MemberCreateRequest request) {
        String phone = PhoneUtil.normalize(request.phone());
        if (memberProfileRepository.existsByOwnerIdAndPhone(ownerId, phone)) {
            throw new ConflictException("member phone already exists for this owner");
        }

        MemberProfile member = new MemberProfile();
        member.setOwnerId(ownerId);
        member.setFullName(request.fullName().trim());
        member.setPhone(phone);
        member.setNote(blankToNull(request.note()));
        member.setStatus("ACTIVE");

        try {
            memberProfileRepository.saveAndFlush(member);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("member phone already exists for this owner");
        }
        entityManager.refresh(member);
        return MemberResponse.from(member);
    }

    @Transactional(readOnly = true)
    public List<MemberResponse> list(Long ownerId, String q) {
        List<MemberProfile> members = (q == null || q.isBlank())
                ? memberProfileRepository.findByOwnerIdOrderByFullNameAsc(ownerId)
                : memberProfileRepository.searchByOwner(ownerId, q.trim());
        return members.stream().map(MemberResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public MemberResponse get(Long ownerId, Long memberId) {
        return MemberResponse.from(requireOwned(ownerId, memberId));
    }

    @Transactional
    public MemberResponse update(Long ownerId, Long memberId, MemberPatchRequest request) {
        MemberProfile member = requireOwned(ownerId, memberId);

        if (request.fullName() != null && !request.fullName().isBlank()) {
            member.setFullName(request.fullName().trim());
        }
        if (request.phone() != null && !request.phone().isBlank()) {
            String phone = PhoneUtil.normalize(request.phone());
            if (!phone.equals(member.getPhone())
                    && memberProfileRepository.existsByOwnerIdAndPhone(ownerId, phone)) {
                throw new ConflictException("member phone already exists for this owner");
            }
            member.setPhone(phone);
        }
        if (request.note() != null) {
            member.setNote(blankToNull(request.note()));
        }
        if (request.status() != null) {
            member.setStatus(request.status());
        }
        try {
            memberProfileRepository.saveAndFlush(member);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("member phone already exists for this owner");
        }
        return MemberResponse.from(member);
    }

    @Transactional
    public MemberResponse deactivate(Long ownerId, Long memberId) {
        MemberProfile member = requireOwned(ownerId, memberId);
        member.setStatus("INACTIVE");
        return MemberResponse.from(memberProfileRepository.save(member));
    }

    /**
     * Step 13 (decision 2026-10-07): member login uses host-set passwords.
     * Creates the member's `users` row (phone from profile, MEMBER role) or
     * resets its password if it already exists. Guards: a phone claimed by a HOST
     * account can never be turned into a member login (409).
     */
    @Transactional
    public Map<String, Object> setLogin(Long ownerId, Long memberId, String password) {
        MemberProfile member = requireOwned(ownerId, memberId);
        if (password.equals(member.getPhone())) {
            throw new BadRequestException("password must not equal phone");
        }

        User user = userRepository.findByPhone(member.getPhone()).orElse(null);
        if (user != null) {
            List<String> roles = userRoleRepository.findRolesByUserId(user.getId());
            if (roles.contains("HOST")) {
                throw new ConflictException("This phone belongs to a host account; a member login cannot be set");
            }
            user.setPasswordHash(passwordEncoder.encode(password));
            user.setFullName(member.getFullName());
            user.setStatus("ACTIVE");
            userRepository.save(user);
            if (!roles.contains("MEMBER")) {
                userRoleRepository.save(new UserRole(user.getId(), "MEMBER"));
            }
            auditLogin(ownerId, member, "MEMBER_LOGIN_RESET");
            notificationService.create(user.getId(), "MEMBER_LOGIN_NOTICE",
                    "Cap quyen dang nhap",
                    "Host da dat lai mat khau dang nhap cua ban.");
            return Map.of("memberId", member.getId(), "phone", member.getPhone(), "loginEnabled", true, "reset", true);
        }

        user = new User();
        user.setPhone(member.getPhone());
        user.setFullName(member.getFullName());
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setStatus("ACTIVE");
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("phone already has an account");
        }
        userRoleRepository.save(new UserRole(user.getId(), "MEMBER"));
        auditLogin(ownerId, member, "MEMBER_LOGIN_CREATED");
        notificationService.create(user.getId(), "MEMBER_LOGIN_NOTICE",
                "Cap quyen dang nhap",
                "Host da tao tai khoan dang nhap cho ban; hay dat mat khau moi.");
        return Map.of("memberId", member.getId(), "phone", member.getPhone(), "loginEnabled", true, "reset", false);
    }

    private void auditLogin(Long ownerId, MemberProfile member, String action) {
        Long actorUserId = ownerAccountRepository.findById(ownerId)
                .map(OwnerAccount::getUserId)
                .orElse(null);
        auditEventRepository.save(AuditEvent.of(
                actorUserId, "MemberProfile", String.valueOf(member.getId()), action,
                "{\"phone\":\"" + member.getPhone() + "\"}"));
    }

    private MemberProfile requireOwned(Long ownerId, Long memberId) {
        return memberProfileRepository.findByOwnerIdAndId(ownerId, memberId)
                .orElseThrow(() -> new NotFoundException("member not found"));
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
