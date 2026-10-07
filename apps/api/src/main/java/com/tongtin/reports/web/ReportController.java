package com.tongtin.reports.web;

import com.tongtin.common.security.AuthPrincipal;
import com.tongtin.reports.dto.LedgerReportResponse;
import com.tongtin.reports.dto.ProfitReportResponse;
import com.tongtin.reports.service.ReportService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasRole('HOST')")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/groups/{id}/ledger")
    public LedgerReportResponse ledger(@AuthenticationPrincipal AuthPrincipal principal,
                                       @PathVariable Long id) {
        return reportService.ledger(principal.ownerId(), id);
    }

    @GetMapping("/groups/{id}/profit")
    public ProfitReportResponse profit(@AuthenticationPrincipal AuthPrincipal principal,
                                       @PathVariable Long id) {
        return reportService.profit(principal.ownerId(), id);
    }
}