package com.tongtin.identity.service;

import com.tongtin.common.errors.BadRequestException;
import com.tongtin.common.errors.ConflictException;
import com.tongtin.common.security.JwtService;
import com.tongtin.identity.dto.AuthResponse;
import com.tongtin.identity.dto.LoginRequest;
import com.tongtin.identity.dto.OwnerProfilePatchRequest;
import com.tongtin.identity.dto.RegisterOwnerRequest;
import com.tongtin.identity.entity.AuditEvent;
import com.tongtin.identity.entity.OwnerAccount;
import com.tongtin.identity.entity.User;
import com.tongtin.identity.entity.UserRole;
import com.tongtin.identity.repository.AuditEventRepository;
import com.tongtin.identity.repository.OwnerAccountRepository;
import com.tongtin.identity.repository.UserRepository;
import com.tongtin.identity.repository.UserRoleRepository;
import com.tongtin.settings.service.SettingsService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final List<String> HOST_ROLE = List.of("HOST");

    private final UserRepository userRepository;
    private final OwnerAccountRepository ownerAccountRepository;
    private final UserRoleRepository userRoleRepository;
    private final AuditEventRepository auditEventRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final SettingsService settingsService;

    public AuthService(
            UserRepository userRepository,
            OwnerAccountRepository ownerAccountRepository,
            UserRoleRepository userRoleRepository,
            AuditEventRepository auditEventRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            SettingsService settingsService) {
        this.userRepository = userRepository;
        this.ownerAccountRepository = ownerAccountRepository;
        this.userRoleRepository = userRoleRepository;
        this.auditEventRepository = auditEventRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.settingsService = settingsService;
    }

    @Transactional
    public AuthResponse registerOwner(RegisterOwnerRequest request) {
        if (!request.acceptTerms()) {
            throw new BadRequestException("acceptTerms must be true");
        }
        if (!request.password().equals(request.confirmPassword())) {
            throw new BadRequestException("password and confirmPassword do not match");
        }
        if (request.password().equals(request.phone())) {
            throw new BadRequestException("password must not equal phone");
        }

        String phone = normalizePhone(request.phone());
        if (userRepository.existsByPhone(phone)) {
            throw new ConflictException("phone already registered");
        }
        if (request.email() != null && !request.email().isBlank() && userRepository.existsByEmail(request.email())) {
            throw new ConflictException("email already registered");
        }

        User user = new User();
        user.setPhone(phone);
        user.setFullName(request.fullName().trim());
        user.setEmail(request.email() == null || request.email().isBlank() ? null : request.email().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setStatus("ACTIVE");

        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("phone already registered");
        }

        userRoleRepository.save(new UserRole(user.getId(), "HOST"));

        OwnerAccount owner = new OwnerAccount();
        owner.setUserId(user.getId());
        String displayName = request.displayName() == null || request.displayName().isBlank()
                ? user.getFullName()
                : request.displayName().trim();
        owner.setDisplayName(displayName);
        owner.setStatus("ACTIVE");

        // Free host trial; length is admin-configurable via the settings module.
        int trialDays = settingsService.getInt("free_trial_days", 30);
        java.time.Instant now = java.time.Instant.now();
        java.time.Instant trialEnd = now.plus(java.time.Duration.ofDays(trialDays));
        owner.setSubscriptionStatus("TRIAL");
        owner.setTrialEndsAt(trialEnd);
        owner.setSubscriptionEndsAt(trialEnd);

        ownerAccountRepository.save(owner);

        auditEventRepository.save(AuditEvent.of(
                user.getId(), "OwnerAccount", String.valueOf(owner.getId()), "OWNER_REGISTERED",
                "{\"phone\":\"" + phone + "\"}"));

        return AuthResponse.of(userView(user), ownerView(owner), HOST_ROLE, tokens(user.getId(), owner.getId(), HOST_ROLE));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String phone = normalizePhone(request.phone());
        User user = userRepository.findByPhone(phone)
                .orElseThrow(() -> new BadCredentialsException("invalid phone or password"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("invalid phone or password");
        }
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new BadCredentialsException("account is not active");
        }

        List<String> roles = userRoleRepository.findRolesByUserId(user.getId());
        if (roles.isEmpty()) {
            throw new BadCredentialsException("invalid phone or password");
        }
        OwnerAccount owner = ownerAccountRepository.findByUserId(user.getId()).orElse(null);
        return AuthResponse.of(userView(user), owner != null ? ownerView(owner) : null, roles,
                tokens(user.getId(), owner != null ? owner.getId() : null, roles));
    }

    @Transactional(readOnly = true)
    public AuthResponse refresh(String refreshToken) {
        com.tongtin.common.security.AuthPrincipal principal;
        try {
            principal = jwtService.parseRefresh(refreshToken);
        } catch (Exception ex) {
            throw new BadCredentialsException("invalid refresh token");
        }
        User user = userRepository.findById(principal.userId())
                .orElseThrow(() -> new BadCredentialsException("invalid refresh token"));
        List<String> roles = userRoleRepository.findRolesByUserId(user.getId());
        if (roles.isEmpty()) {
            throw new BadCredentialsException("invalid refresh token");
        }
        OwnerAccount owner = ownerAccountRepository.findByUserId(user.getId()).orElse(null);
        return AuthResponse.of(userView(user), owner != null ? ownerView(owner) : null, roles,
                tokens(user.getId(), owner != null ? owner.getId() : null, roles));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> me(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("user not found"));
        List<String> roles = userRoleRepository.findRolesByUserId(userId);

        Map<String, Object> response = new HashMap<>();
        response.put("user", userView(user));
        response.put("roles", roles);

        OwnerAccount owner = ownerAccountRepository.findByUserId(userId).orElse(null);
        if (owner != null) {
            response.put("owner", ownerView(owner));

            Map<String, Object> onboarding = new HashMap<>();
            onboarding.put("cccd", owner.getCccd() != null && !owner.getCccd().isBlank());
            onboarding.put("bank", owner.getBankName() != null && !owner.getBankName().isBlank()
                    && owner.getBankAccount() != null && !owner.getBankAccount().isBlank());
            onboarding.put("firstGroup", false);
            response.put("onboarding", onboarding);
        }
        return response;
    }

    @Transactional
    public Map<String, Object> patchOwnerProfile(Long userId, OwnerProfilePatchRequest request) {
        OwnerAccount owner = ownerAccountRepository.findByUserId(userId)
                .orElseThrow(() -> new BadRequestException("owner account not found"));

        if (request.displayName() != null && !request.displayName().isBlank()) {
            owner.setDisplayName(request.displayName().trim());
        }
        if (request.cccd() != null) {
            owner.setCccd(blankToNull(request.cccd()));
        }
        if (request.bankName() != null) {
            owner.setBankName(blankToNull(request.bankName()));
        }
        if (request.bankAccount() != null) {
            owner.setBankAccount(blankToNull(request.bankAccount()));
        }
        if (request.accountHolder() != null) {
            owner.setAccountHolder(blankToNull(request.accountHolder()));
        }
        if (request.zalo() != null) {
            owner.setZalo(blankToNull(request.zalo()));
        }
        if (request.city() != null) {
            owner.setCity(blankToNull(request.city()));
        }
        ownerAccountRepository.save(owner);
        return ownerView(owner);
    }

    private Map<String, Object> tokens(Long userId, Long ownerId, List<String> roles) {
        Map<String, Object> tokens = new HashMap<>();
        tokens.put("accessToken", jwtService.accessToken(userId, ownerId, roles));
        tokens.put("refreshToken", jwtService.refreshToken(userId, ownerId, roles));
        tokens.put("tokenType", "Bearer");
        tokens.put("expiresIn", jwtService.accessTtlSeconds());
        return tokens;
    }

    private Map<String, Object> userView(User user) {
        Map<String, Object> view = new HashMap<>();
        view.put("id", user.getId());
        view.put("phone", user.getPhone());
        view.put("fullName", user.getFullName());
        view.put("email", user.getEmail());
        view.put("status", user.getStatus());
        return view;
    }

    private Map<String, Object> ownerView(OwnerAccount owner) {
        Map<String, Object> view = new HashMap<>();
        view.put("id", owner.getId());
        view.put("userId", owner.getUserId());
        view.put("displayName", owner.getDisplayName());
        view.put("status", owner.getStatus());
        view.put("cccd", owner.getCccd());
        view.put("bankName", owner.getBankName());
        view.put("bankAccount", owner.getBankAccount());
        view.put("accountHolder", owner.getAccountHolder());
        view.put("zalo", owner.getZalo());
        view.put("city", owner.getCity());
        view.put("defaultCurrency", owner.getDefaultCurrency());
        view.put("subscriptionStatus", owner.getSubscriptionStatus());
        view.put("trialEndsAt", owner.getTrialEndsAt() != null ? owner.getTrialEndsAt().toString() : null);
        view.put("subscriptionEndsAt", owner.getSubscriptionEndsAt() != null ? owner.getSubscriptionEndsAt().toString() : null);
        view.put("currentPlanId", owner.getCurrentPlanId());
        return view;
    }

    static String normalizePhone(String phone) {
        return com.tongtin.common.util.PhoneUtil.normalize(phone);
    }

    private static String blankToNull(String value) {
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
