package com.tongtin.reports.export;

import com.tongtin.common.errors.BadRequestException;
import java.util.Locale;

/**
 * Step 30 export formats: hand-rolled RFC 4180 CSV (UTF-8 BOM for Excel) and
 * real .xlsx via Apache POI. Unknown formats are a client error, never a
 * silent default (default = CSV applied only when the parameter is absent).
 */
public enum ExportFormat {

    CSV("csv", "text/csv;charset=UTF-8"),
    XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final String extension;
    private final String contentType;

    ExportFormat(String extension, String contentType) {
        this.extension = extension;
        this.contentType = contentType;
    }

    public String extension() {
        return extension;
    }

    public String contentType() {
        return contentType;
    }

    public static ExportFormat parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return CSV;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "csv" -> CSV;
            case "xlsx" -> XLSX;
            default -> throw new BadRequestException("unknown export format: " + raw);
        };
    }
}
