package com.tongtin.common.util;

public final class PhoneUtil {

    private PhoneUtil() {
    }

    public static String normalize(String phone) {
        String trimmed = phone.trim();
        if (trimmed.startsWith("0")) {
            return "+84" + trimmed.substring(1);
        }
        return trimmed;
    }
}
