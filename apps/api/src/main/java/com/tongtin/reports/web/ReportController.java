package com.tongtin.reports.web;

import com.tongtin.common.security.AuthPrincipal;
import com.tongtin.reports.dto.LedgerReportResponse;
import com.tongtin.reports.dto.ProfitReportResponse;
import com.tongtin.reports.export.ExportService;
import com.tongtin.reports.service.ReportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasRole('HOST')")
public class ReportController {

    private final ReportService reportService;
    private final ExportService exportService;

    public ReportController(ReportService reportService, ExportService exportService) {
        this.reportService = reportService;
        this.exportService = exportService;
    }

    /**
     * Step 42: optional filters cycleId / type / status + pagination (page 0-based, size default 50).
     * When page param is omitted (defaults to -1) the full unpaginated response is returned (backward-compat).
     */
    @GetMapping("/groups/{id}/ledger")
    public LedgerReportResponse ledger(@AuthenticationPrincipal AuthPrincipal principal,
                                       @PathVariable Long id,
                                       @RequestParam(required = false) Long cycleId,
                                       @RequestParam(required = false) String type,
                                       @RequestParam(required = false) String status,
                                       @RequestParam(defaultValue = "-1") int page,
                                       @RequestParam(defaultValue = "50") int size) {
        if (page >= 0) {
            return reportService.ledgerPaged(principal.ownerId(), id, cycleId, type, status, page, size);
        }
        return reportService.ledger(principal.ownerId(), id);
    }

    @GetMapping("/groups/{id}/profit")
    public ProfitReportResponse profit(@AuthenticationPrincipal AuthPrincipal principal,
                                       @PathVariable Long id) {
        return reportService.profit(principal.ownerId(), id);
    }

    @GetMapping("/groups/{id}/export/ledger")
    public ResponseEntity<byte[]> exportLedger(@AuthenticationPrincipal AuthPrincipal principal,
                                               @PathVariable Long id,
                                               @RequestParam(defaultValue = "csv") String format) {
        return respond(exportService.ledger(principal.ownerId(), id, format));
    }

    @GetMapping("/groups/{id}/export/profit")
    public ResponseEntity<byte[]> exportProfit(@AuthenticationPrincipal AuthPrincipal principal,
                                               @PathVariable Long id,
                                               @RequestParam(defaultValue = "csv") String format) {
        return respond(exportService.profit(principal.ownerId(), id, format));
    }

    private static ResponseEntity<byte[]> respond(ExportService.Download download) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + download.filename() + "\"")
                .contentType(MediaType.valueOf(download.contentType()))
                .body(download.bytes());
    }
}
