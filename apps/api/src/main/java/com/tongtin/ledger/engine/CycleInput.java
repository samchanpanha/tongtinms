package com.tongtin.ledger.engine;

/**
 * Input to the money engine. Pure data; no Spring, no DB, no clock.
 * Money fields are long minor units. Currency is a label only.
 */
public record CycleInput(
        String presetCode,
        int engineVersion,
        long C,
        int N,
        int D,
        int A,
        long B,
        int cycleNo,
        int cycleCount,
        String hostFeeType,
        long hostFeeMinor,
        int hostFeeBps,
        long minBid,
        long maxBid,
        long bidStep) {
}