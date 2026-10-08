package com.tongtin.reports.export;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * RFC 4180 CSV renderer: UTF-8 BOM first byte (Excel auto-detect), CRLF row
 * endings, embedded quotes doubled. Non-numeric cells starting with =, +, @
 * or - get a leading apostrophe (CSV formula-injection guard, OWASP) while
 * numeric cells — including negative money — are emitted raw so Excel parses
 * them as numbers.
 */
public final class CsvRenderer {

    private CsvRenderer() {
    }

    public static byte[] render(Table table) {
        StringBuilder sb = new StringBuilder(4096);
        sb.append('\uFEFF');
        appendRow(sb, table.header());
        for (List<String> row : table.rows()) {
            appendRow(sb, row);
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    static String escape(String raw) {
        String value = raw == null ? "" : raw;
        if (!ExportTables.isNumeric(value)) {
            value = guard(value);
        }
        if (value.indexOf('"') >= 0 || value.indexOf(',') >= 0
                || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }

    private static String guard(String value) {
        if (value.isEmpty()) {
            return value;
        }
        char first = value.charAt(0);
        if (first == '=' || first == '+' || first == '@' || first == '-') {
            return "'" + value;
        }
        return value;
    }

    private static void appendRow(StringBuilder sb, List<String> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(escape(cells.get(i)));
        }
        sb.append("\r\n");
    }
}
