package com.tongtin.reports.export;

import java.util.List;

/**
 * One rectangular table ready for either renderer: a header row plus data
 * rows of already-rendered strings (money as exact major-unit decimals, dates
 * as ISO-8601). kind doubles as the XLSX sheet name and the filename stem.
 */
public record Table(String kind, long groupId, List<String> header, List<List<String>> rows) {

    public String filename(String extension) {
        return "tongtin-" + kind + "-g" + groupId + "." + extension;
    }
}
