package com.tongtin.settings;

/**
 * Metadata for one configurable key: where it shows up in the admin UI, what
 * type it is, its default when no row exists, and its allowed range.
 */
public record SettingDefinition(
        String key,
        String category,
        String label,
        String description,
        SettingType type,
        String defaultValue,
        Integer min,
        Integer max) {

    public static SettingDefinition of(String key, String category, String label, String description,
            SettingType type, String defaultValue) {
        return new SettingDefinition(key, category, label, description, type, defaultValue, null, null);
    }

    public static SettingDefinition numeric(String key, String category, String label, String description,
            String defaultValue, int min, int max) {
        return new SettingDefinition(key, category, label, description, SettingType.INT, defaultValue, min, max);
    }

    public static SettingDefinition numericList(String key, String category, String label, String description,
            String defaultValue, int min, int max) {
        return new SettingDefinition(key, category, label, description, SettingType.INT_LIST, defaultValue, min, max);
    }
}
