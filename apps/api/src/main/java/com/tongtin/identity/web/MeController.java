package com.tongtin.identity.web;

import com.tongtin.common.security.AuthPrincipal;
import com.tongtin.identity.dto.OwnerProfilePatchRequest;
import com.tongtin.identity.service.AuthService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class MeController {

    private final AuthService authService;

    public MeController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(@AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(authService.me(principal.userId()));
    }

    @PatchMapping("/me/owner-profile")
    public ResponseEntity<Map<String, Object>> patchOwnerProfile(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody OwnerProfilePatchRequest request) {
        return ResponseEntity.ok(authService.patchOwnerProfile(principal.userId(), request));
    }
}
