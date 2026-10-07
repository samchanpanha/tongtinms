package com.tongtin.ledger.engine;

/**
 * Result of a cycle calculation.
 * winnerShareId is NOT chosen here (bidding module supplies B + winner).
 * Money fields are long minor units.
 */
public record CycleResult(
        long deadPay,
        long alivePay,
        long winnerPay,
        long grossPot,
        long T,
        long netPayout) {
}