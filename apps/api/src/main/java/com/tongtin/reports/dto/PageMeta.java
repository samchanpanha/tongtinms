package com.tongtin.reports.dto;

/**
 * Step 42: Generic pagination metadata appended to paginated report responses.
 * Used as a nested object in paginated report wrappers.
 */
public record PageMeta(int number, int size, long totalElements, int totalPages) {
}
