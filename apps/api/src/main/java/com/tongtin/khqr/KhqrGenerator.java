package com.tongtin.khqr;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Step 34: pure-Java EMVCo merchant-presented QR payload builder for a single
 * obligation (01-DOMAIN §16). One QR = one ledger entry; money is never read
 * back from a QR. No Spring, no DB — mirrors the formula-engine rule. All
 * arithmetic is integer math; no FP types anywhere (defined explicitly so the
 * zero-FP release gate stays green).
 *
 * Layout: 00 payload format "01", 01 point-of-initiation "12" (dynamic),
 * 26 merchant account info (00 = globally unique identifier, 01 = service ref),
 * 52 merchant category, 53 ISO 4217 numeric currency, 54 amount as a major-unit
 * decimal (from the currency exponent), 58 country "KH", 59 merchant name,
 * 60 merchant city, 62/01 bill number = the obligation reference, 63/04 real
 * CRC16-CCITT (poly 0x1021, init 0xFFFF) over the whole payload.
 */
public final class KhqrGenerator {

    /** ISO 4217 numeric codes for every supported group currency (07 §10). */
    private static final Map<String, Integer> CURRENCY_NUMERIC = Map.of(
            "VND", 704,
            "KHR", 116,
            "LAK", 418,
            "USD", 840,
            "THB", 764,
            "SGD", 702);

    private static final int CRC_TAG_LEN = 4;
    private static final int MAX_BILL_NUMBER = 25;
    private static final int MAX_MERCHANT_NAME = 25;
    private static final int MAX_CITY = 15;

    private KhqrGenerator() {
    }

    /**
     * Builds a KHQR payload for one obligation.
     *
     * @param merchantName sanitizable group name (EMVCo A-Z/a-z/0-9/space)
     * @param merchantCity fixed city, e.g. "PHNOM PENH"
     * @param amountMinor  the remaining minor units to encode
     * @param currency     ISO 4217 code (VND/KHR/LAK/USD/THB/SGD supported)
     * @param exponent     decimal exponent for the money (0 or 2 in practice)
     * @param reference    obligation reference, emitted as EMVCo 62/01
     * @return the full "000201…6304<CRC>" payload string
     * @throws IllegalArgumentException for an unsupported currency
     */
    public static String generate(String merchantName, String merchantCity,
                                  long amountMinor, String currency, short exponent,
                                  String reference) {
        Integer numeric = CURRENCY_NUMERIC.get(currency.trim().toUpperCase());
        if (numeric == null) {
            throw new IllegalArgumentException("unsupported currency for KHQR: " + currency);
        }

        StringBuilder payload = new StringBuilder(256);
        payload.append(tlv("00", "01"));
        payload.append(tlv("01", "12"));

        String merchantInfo = tlv("00", "com.tongtin")
                + tlv("01", sanitize(merchantName, MAX_MERCHANT_NAME));
        payload.append(tlv("26", merchantInfo));
        payload.append(tlv("52", "5999"));
        payload.append(tlv("53", String.valueOf(numeric)));
        payload.append(tlv("54", decimalAmount(amountMinor, exponent)));
        payload.append(tlv("58", "KH"));
        payload.append(tlv("59", sanitize(merchantName, MAX_MERCHANT_NAME)));
        payload.append(tlv("60", sanitize(merchantCity, MAX_CITY)));
        payload.append(tlv("62", tlv("01", trimmedReference(reference))));

        String base = payload.toString();
        int crc = crc16(base + "6304" + "0000");
        return base + "6304" + String.format("%04X", crc);
    }

    /**
     * Verifies that a payload is a KHQR we generated: shape + a genuine CRC.
     * Used by the render endpoint before rasterizing and by tests (the QR value
     * itself is never needed for business logic).
     */
    public static boolean isValid(String payload) {
        if (payload == null || payload.length() < 11 || !payload.startsWith("000201010212")) {
            return false;
        }
        int crc;
        try {
            crc = Integer.parseInt(payload.substring(payload.length() - CRC_TAG_LEN), 16);
        } catch (NumberFormatException ex) {
            return false;
        }
        String input = payload.substring(0, payload.length() - CRC_TAG_LEN) + "0000";
        return crc16(input) == crc;
    }

    /** Exposes the CRC for the "123456789" golden vector in tests. */
    public static int crc16Of(String data) {
        return crc16(data);
    }

    private static int crc16(String data) {
        byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
        int crc = 0xFFFF;
        for (byte b : bytes) {
            crc ^= (b & 0xFF) << 8;
            for (int i = 0; i < 8; i++) {
                if ((crc & 0x8000) != 0) {
                    crc = (crc << 1) ^ 0x1021;
                } else {
                    crc = crc << 1;
                }
                crc &= 0xFFFF;
            }
        }
        return crc;
    }

    /** Tag + 2-digit string length + value (EMVCo TLV, all ASCII). */
    private static String tlv(String tag, String value) {
        return tag + String.format("%02d", value.length()) + value;
    }

    /**
     * Renders minor units as a major-unit decimal with exactly {@code exponent}
     * fraction digits, zero-padded, using long math only (07 §10).
     */
    private static String decimalAmount(long amountMinor, short exponent) {
        if (exponent <= 0) {
            return Long.toString(amountMinor);
        }
        long pow10 = 1;
        for (int i = 0; i < exponent; i++) {
            pow10 *= 10;
        }
        long whole = amountMinor / pow10;
        long fraction = amountMinor % pow10;
        String fractionStr = Long.toString(fraction);
        StringBuilder padded = new StringBuilder();
        for (int pad = fractionStr.length(); pad < exponent; pad++) {
            padded.append('0');
        }
        padded.append(fractionStr);
        return whole + "." + padded;
    }

    private static String trimmedReference(String reference) {
        if (reference == null) {
            return "";
        }
        if (reference.length() <= MAX_BILL_NUMBER) {
            return reference;
        }
        return reference.substring(reference.length() - MAX_BILL_NUMBER);
    }

    private static String sanitize(String value, int max) {
        if (value == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (char c : value.toCharArray()) {
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9') || c == ' ') {
                builder.append(c);
            } else {
                builder.append(' ');
            }
            if (builder.length() >= max) {
                break;
            }
        }
        return builder.toString().trim();
    }
}