package com.tongtin.cycles.bids.web;

import com.tongtin.common.security.AuthPrincipal;
import com.tongtin.cycles.bids.dto.BidResponse;
import com.tongtin.cycles.bids.dto.BidSubmitRequest;
import com.tongtin.cycles.bids.dto.CycleBidSummaryResponse;
import com.tongtin.cycles.bids.service.BidService;
import jakarta.validation.Valid;
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
public class BidController {

    private final BidService bidService;

    public BidController(BidService bidService) {
        this.bidService = bidService;
    }

    @PostMapping("/cycles/{cycleId}/bids")
    public ResponseEntity<BidResponse> submit(@AuthenticationPrincipal AuthPrincipal principal,
                                              @PathVariable Long cycleId,
                                              @Valid @RequestBody BidSubmitRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(bidService.submit(principal.userId(), principal.ownerId(), cycleId, request));
    }

    @GetMapping("/cycles/{cycleId}/summary")
    public CycleBidSummaryResponse summary(@AuthenticationPrincipal AuthPrincipal principal,
                                           @PathVariable Long cycleId) {
        return bidService.summary(principal.ownerId(), cycleId);
    }
}