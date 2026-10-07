package com.tongtin.common.money;

/**
 * Money shape for all reports (07-MULTI-CURRENCY §5): amountMinor (long minor
 * units, never floating point), paired with the ISO currency code and its
 * exponent + symbol from the `currencies` table. UI formats from this; the
 * calculator and stores stay on longs.
 */
public record Money(String currency, long amountMinor, short exponent, String symbol) {

    public static Money of(Currency currency, long amountMinor) {
        return new Money(currency.getCode(), amountMinor, currency.getExponent(), currency.getSymbol());
    }
}