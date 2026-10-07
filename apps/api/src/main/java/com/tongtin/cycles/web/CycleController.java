package com.tongtin.cycles.web;

import com.tongtin.common.security.AuthPrincipal;
import com.tongtin.cycles.dto.CloseCalculateRequest;
import com.tongtin.cycles.dto.CycleResponse;
import com.tongtin.cycles.service.CycleCloseService;
import com.tongtin.cycles.service.CycleService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasRole('HOST')")
public class CycleController {

    private final CycleService cycleService;
    private final CycleCloseService cycleCloseService;

    public CycleController(CycleService cycleService, CycleCloseService cycleCloseService) {
        this.cycleService = cycleService;
        this.cycleCloseService = cycleCloseService;
    }

    @PostMapping("/groups/{id}/cycles/open")
    public ResponseEntity<CycleResponse> open(@AuthenticationPrincipal AuthPrincipal principal,
                                              @PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cycleService.open(principal.userId(), principal.ownerId(), id));
    }

    @GetMapping("/groups/{id}/cycles")
    public List<CycleResponse> listForGroup(@AuthenticationPrincipal AuthPrincipal principal,
                                            @PathVariable Long id) {
        return cycleService.listForGroup(principal.ownerId(), id);
    }

    @GetMapping("/cycles/{cycleId}")
    public CycleResponse get(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long cycleId) {
        return cycleService.get(principal.ownerId(), cycleId);
    }

    @PostMapping("/cycles/{cycleId}/close-and-calculate")
    public CycleResponse closeAndCalculate(@AuthenticationPrincipal AuthPrincipal principal,
                                           @PathVariable Long cycleId,
                                           @RequestBody(required = false) CloseCalculateRequest request) {
        return cycleCloseService.closeAndCalculate(principal.userId(), principal.ownerId(), cycleId, request);
    }

    @PostMapping("/cycles/{cycleId}/confirm-payout")
    public CycleResponse confirmPayout(@AuthenticationPrincipal AuthPrincipal principal,
                                       @PathVariable Long cycleId) {
        return cycleCloseService.confirmPayout(principal.userId(), principal.ownerId(), cycleId);
    }
}