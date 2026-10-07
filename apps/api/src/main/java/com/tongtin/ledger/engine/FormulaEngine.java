package com.tongtin.ledger.engine;

/**
 * Pure Java money engine for Tong Tin presets, engine v1.
 *
 * No Spring, no DB, no clock, no random, no floating point.
 * Money is long minor units; currency is a label. Winner picking is outside.
 *
 * Presets (engine v1):
 *   BIDDING_CLASSIC  Hoi khui / dau gia  (MVP default)
 *   BIDDING_NO_FEE   alias of BIDDING_CLASSIC with hostFeeType NONE
 *   FIXED_EQUAL      Hoi deu / super
 */
public final class FormulaEngine {

    public static final int ENGINE_VERSION = 1;

    private FormulaEngine() {
    }

    public static CycleResult calculate(CycleInput in) {
        validateInvariants(in);

        long deadPay = in.C();
        long alivePay;
        long grossPot;
        if ("FIXED_EQUAL".equals(in.presetCode())) {
            alivePay = in.C();
            grossPot = Math.multiplyExact((long) in.N() - 1, in.C());
        } else if ("BIDDING_CLASSIC".equals(in.presetCode()) || "BIDDING_NO_FEE".equals(in.presetCode())) {
            alivePay = in.C() - in.B();
            grossPot = Math.addExact(
                    Math.multiplyExact((long) in.D(), in.C()),
                    Math.multiplyExact((long) in.A() - 1, in.C() - in.B()));
        } else {
            throw new FormulaException("Unknown preset: " + in.presetCode());
        }

        long T = hostFee(in, grossPot);
        long netPayout = grossPot - T;
        if (netPayout < 0) {
            throw new FormulaException("T exceeds grossPot");
        }
        return new CycleResult(deadPay, alivePay, 0L, grossPot, T, netPayout);
    }

    /**
     * HALF_UP away from zero on .5: (amount * bps + 5000) / 10000, integer only.
     */
    public static long roundBps(long amount, int bps) {
        if (amount < 0 || bps < 0) {
            throw new FormulaException("roundBps requires amount >= 0 and bps >= 0");
        }
        return Math.addExact(Math.multiplyExact(amount, bps), 5000L) / 10000L;
    }

    private static long hostFee(CycleInput in, long grossPot) {
        if ("BIDDING_NO_FEE".equals(in.presetCode()) || "NONE".equals(in.hostFeeType())) {
            return 0L;
        }
        if ("FIXED_PER_CYCLE".equals(in.hostFeeType())) {
            return in.hostFeeMinor();
        }
        if ("PERCENT_OF_POT".equals(in.hostFeeType())) {
            return roundBps(grossPot, in.hostFeeBps());
        }
        throw new FormulaException("Host fee type not in engine v1: " + in.hostFeeType());
    }

    private static void validateInvariants(CycleInput in) {
        boolean bidding = "BIDDING_CLASSIC".equals(in.presetCode()) || "BIDDING_NO_FEE".equals(in.presetCode());
        boolean fixed = "FIXED_EQUAL".equals(in.presetCode());
        if (!bidding && !fixed) {
            throw new FormulaException("Unknown preset: " + in.presetCode());
        }
        if (in.N() < 2) {
            throw new FormulaException("N must be >= 2");
        }
        if (in.C() <= 0) {
            throw new FormulaException("C must be > 0");
        }
        if (in.A() < 1) {
            throw new FormulaException("A must be >= 1");
        }
        if (in.D() < 0) {
            throw new FormulaException("D must be >= 0");
        }
        if (in.A() + in.D() != in.N()) {
            throw new FormulaException("A + D must equal N");
        }
        if (in.cycleNo() < 1 || in.cycleNo() > in.cycleCount()) {
            throw new FormulaException("cycleNo out of range");
        }
        if (in.cycleCount() != in.N()) {
            throw new FormulaException("cycleCount must equal N in engine v1");
        }
        if (bidding) {
            if (in.B() >= in.C()) {
                throw new FormulaException("B must be < C");
            }
            if (in.B() < 0) {
                throw new FormulaException("B must be >= 0");
            }
        }
        if (in.hostFeeMinor() < 0 || in.hostFeeBps() < 0 || in.minBid() < 0 || in.maxBid() < 0 || in.bidStep() < 0) {
            throw new FormulaException("Money fields must be >= 0");
        }
        boolean lastCycle = in.cycleNo() == in.cycleCount();
        if (lastCycle && in.A() != 1) {
            throw new FormulaException("Last cycle requires A == 1");
        }
        if (lastCycle && in.B() != 0) {
            throw new FormulaException("Last cycle requires B == 0");
        }
    }
}