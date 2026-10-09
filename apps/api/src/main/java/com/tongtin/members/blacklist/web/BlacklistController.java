package com.tongtin.members.blacklist.web;

import com.tongtin.common.security.AuthPrincipal;
import com.tongtin.members.blacklist.dto.BlacklistAddRequest;
import com.tongtin.members.blacklist.dto.BlacklistResponse;
import com.tongtin.members.blacklist.service.BlacklistService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/members/blacklist")
@PreAuthorize("hasRole('HOST')")
public class BlacklistController {

    private final BlacklistService blacklistService;

    public BlacklistController(BlacklistService blacklistService) {
        this.blacklistService = blacklistService;
    }

    @GetMapping
    public ResponseEntity<List<BlacklistResponse>> list(@AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(blacklistService.list(principal.ownerId()));
    }

    @PostMapping
    public ResponseEntity<BlacklistResponse> add(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody BlacklistAddRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(blacklistService.add(principal.ownerId(), principal.userId(), request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<BlacklistResponse> unlist(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable("id") Long id) {
        return ResponseEntity.ok(blacklistService.unlist(principal.ownerId(), principal.userId(), id));
    }
}
