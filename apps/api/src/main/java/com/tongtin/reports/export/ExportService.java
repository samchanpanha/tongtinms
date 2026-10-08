package com.tongtin.reports.export;

import com.tongtin.reports.service.MemberReportService;
import com.tongtin.reports.service.ReportService;
import org.springframework.stereotype.Service;

/**
 * Step 30 export facade: pulls the Step 15 JSON reports (same services, same
 * permission checks, same 404s) and renders them as CSV or XLSX attachments.
 * No new queries, no new math — export adds nothing to the domain.
 */
@Service
public class ExportService {

    private final ReportService reportService;
    private final MemberReportService memberReportService;

    public ExportService(ReportService reportService, MemberReportService memberReportService) {
        this.reportService = reportService;
        this.memberReportService = memberReportService;
    }

    public record Download(String filename, String contentType, byte[] bytes) {
    }

    public Download ledger(long ownerId, long groupId, String format) {
        return render(ExportTables.ledger(reportService.ledger(ownerId, groupId)), format);
    }

    public Download profit(long ownerId, long groupId, String format) {
        return render(ExportTables.profit(reportService.profit(ownerId, groupId)), format);
    }

    public Download statement(long userId, long groupId, String format) {
        return render(ExportTables.statement(memberReportService.statement(userId, groupId)), format);
    }

    private Download render(Table table, String format) {
        ExportFormat exportFormat = ExportFormat.parse(format);
        byte[] bytes = exportFormat == ExportFormat.XLSX
                ? XlsxRenderer.render(table)
                : CsvRenderer.render(table);
        return new Download(table.filename(exportFormat.extension()), exportFormat.contentType(), bytes);
    }
}
