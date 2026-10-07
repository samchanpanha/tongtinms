package com.tongtin.settings.dto;

import java.time.Instant;

/**
 * One setting as shown in the admin UI. SECRET values are never returned:
 * `value` and `defaultValue` are null and `configured` tells the UI whether a
 * secret is already stored.
 */
public record SettingDto(
        String key,
        String label,
        String description,
        String type,
        String value,
        boolean configured,
        String defaultValue,
        Integer min,
        Integer max,
        Instant updatedAt) {
}
