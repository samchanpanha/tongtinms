package com.tongtin.ledger.engine;

/**
 * Raised when CycleInput violates shared invariants or preset rules.
 */
public class FormulaException extends IllegalArgumentException {

    public FormulaException(String message) {
        super(message);
    }
}