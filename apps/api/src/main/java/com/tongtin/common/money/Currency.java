package com.tongtin.common.money;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "currencies")
public class Currency {

    @Id
    @Column(name = "code", length = 3)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "symbol", nullable = false)
    private String symbol;

    @Column(name = "exponent", nullable = false)
    private short exponent;

    @Column(name = "rounding", nullable = false)
    private String rounding = "HALF_UP";

    @Column(name = "active", nullable = false)
    private boolean active = true;

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getSymbol() {
        return symbol;
    }

    public short getExponent() {
        return exponent;
    }

    public String getRounding() {
        return rounding;
    }

    public boolean isActive() {
        return active;
    }
}
