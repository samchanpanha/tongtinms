package com.tongtin.settings;

/**
 * Value type of a catalog setting. Drives both validation on write and the
 * input widget the admin UI renders.
 */
public enum SettingType {
    STRING,
    SECRET,
    URL,
    INT,
    BOOLEAN,
    INT_LIST
}
