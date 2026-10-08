package com.tongtin.memberportal.web;

import com.tongtin.common.security.AuthPrincipal;
import com.tongtin.cycles.bids.dto.BidResponse;
import com.tongtin.cycles.bids.dto.BidSubmitRequest;
import com.tongtin.memberportal.dto.BalanceResponse;
import com.tongtin.memberportal.dto.MemberGroupResponse;
import com.tongtin.memberportal.service.MemberPortalService;
import com.tongtin.reports.dto.MemberStatementResponse;
import com.tongtin.reports.dto.PublicCycleSummary;
import com.tongtin.reports.export.ExportService;
import com.tongtin.reports.service.MemberReportService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
@PreAuthorize("hasRole('MEMBER')")
public class MemberPortalController {

    private final MemberPortalService memberPortalService;
    private final MemberReportService memberReportService;
    private final ExportService exportService;

    public MemberPortalController(MemberPortalService memberPortalService,
                                  MemberReportService memberReportService,
                                  ExportService exportService) {
        this.memberPortalService = memberPortalService;
        this.memberReportService = memberReportService;
        this.exportService = exportService;
    }

    @GetMapping("/groups")
    public List<MemberGroupResponse> myGroups(@AuthenticationPrincipal AuthPrincipal principal) {
        return memberPortalService.myGroups(principal.userId());
    }

    @GetMapping("/groups/{groupId}")
    public MemberGroupResponse myGroup(@AuthenticationPrincipal AuthPrincipal principal,
                                       @PathVariable Long groupId) {
        return memberPortalService.myGroup(principal.userId(), groupId);
    }

    @GetMapping("/groups/{groupId}/balance")
    public BalanceResponse balance(@AuthenticationPrincipal AuthPrincipal principal,
                                   @PathVariable Long groupId) {
        return memberPortalService.balance(principal.userId(), groupId);
    }

    @GetMapping("/groups/{groupId}/statement")
    public MemberStatementResponse statement(@AuthenticationPrincipal AuthPrincipal principal,
                                             @PathVariable Long groupId) {
        return memberReportService.statement(principal.userId(), groupId);
    }

    @GetMapping("/groups/{groupId}/export/statement")
    public ResponseEntity<byte[]> exportStatement(@AuthenticationPrincipal AuthPrincipal principal,
                                                  @PathVariable Long groupId,
                                                  @RequestParam(defaultValue = "csv") String format) {
        ExportService.Download download = exportService.statement(principal.userId(), groupId, format);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + download.filename() + "\"")
                .contentType(MediaType.valueOf(download.contentType()))
                .body(download.bytes());
    }

    @GetMapping("/groups/{groupId}/cycles")
    public List<PublicCycleSummary> cycles(@AuthenticationPrincipal AuthPrincipal principal,
                                           @PathVariable Long groupId) {
        return memberReportService.cycles(principal.userId(), groupId);
    }

    @PostMapping("/cycles/{cycleId}/bids")
    public ResponseEntity<BidResponse> submitBid(@AuthenticationPrincipal AuthPrincipal principal,
                                                 @PathVariable Long cycleId,
                                                 @Valid @RequestBody BidSubmitRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(memberPortalService.submitBid(principal.userId(), cycleId, request));
    }
}