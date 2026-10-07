package com.tongtin.common.money;

import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Caches the seeded `currencies` rows at boot so report services can build the
 * Money shape {currency, amountMinor, exponent, symbol} without per-row lookups.
 */
@Component
public class MoneyLookup {

    private final CurrencyRepository currencyRepository;
    private Map<String, Currency> byCode = Map.of();

    public MoneyLookup(CurrencyRepository currencyRepository) {
        this.currencyRepository = currencyRepository;
    }

    @PostConstruct
    void load() {
        byCode = currencyRepository.findAll().stream()
                .collect(Collectors.toMap(Currency::getCode, Function.identity()));
    }

    public Money money(String code, long amountMinor) {
        Currency currency = byCode.get(code);
        if (currency == null) {
            throw new IllegalStateException("Unknown currency code: " + code);
        }
        return Money.of(currency, amountMinor);
    }

    public Money zero(String code) {
        return money(code, 0L);
    }

    public Money nullable(String code, Long amountMinor) {
        return amountMinor == null ? null : money(code, amountMinor);
    }
}