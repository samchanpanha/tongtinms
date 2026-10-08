package com.tongtin.reports.export;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Real .xlsx renderer (Apache POI). One sheet named after the export kind.
 * Cells whose full content parses as a number become numeric cells (summable
 * in Excel); everything else is a text cell — text cells never evaluate as
 * formulas, so no formula guard is needed here. Money crosses the format
 * boundary only at this point, via BigDecimal, never through domain code.
 */
public final class XlsxRenderer {

    private XlsxRenderer() {
    }

    public static byte[] render(Table table) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(table.kind());
            writeRow(sheet.createRow(0), table.header());
            int rowIndex = 1;
            for (List<String> row : table.rows()) {
                writeRow(sheet.createRow(rowIndex++), row);
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException("failed to render " + table.kind() + " workbook", ex);
        }
    }

    private static void writeRow(Row row, List<String> values) {
        for (int i = 0; i < values.size(); i++) {
            writeCell(row, i, values.get(i));
        }
    }

    private static void writeCell(Row row, int index, String value) {
        if (value == null || value.isEmpty()) {
            row.createCell(index);
            return;
        }
        Cell cell = row.createCell(index);
        if (ExportTables.isNumeric(value)) {
            // xlsx numeric cells are IEEE 754 by format; exact for |v| < 2^53
            cell.setCellValue(new BigDecimal(value).doubleValue());
        } else {
            cell.setCellValue(value);
        }
    }
}
