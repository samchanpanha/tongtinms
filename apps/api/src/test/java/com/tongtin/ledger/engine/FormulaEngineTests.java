package com.tongtin.ledger.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;

class FormulaEngineTests {

    private static final String CLASSIC = "BIDDING_CLASSIC";
    private static final String NO_FEE = "BIDDING_NO_FEE";
    private static final String FIXED = "FIXED_EQUAL";

    private CycleInput bidding(long C, int N, int D, int A, long B, int cycleNo,
                               String feeType, long feeMinor, int feeBps) {
        return new CycleInput(CLASSIC, 1, C, N, D, A, B, cycleNo, N, feeType, feeMinor, feeBps, 0, C - 1, 10_000);
    }

    @Test
    void fixtureA_classicVndMatchesExactly() {
        CycleInput c1 = bidding(1_000_000, 10, 0, 10, 200_000, 1, "FIXED_PER_CYCLE", 100_000, 0);
        CycleResult r1 = FormulaEngine.calculate(c1);
        assertThat(r1.deadPay()).isEqualTo(1_000_000L);
        assertThat(r1.alivePay()).isEqualTo(800_000L);
        assertThat(r1.winnerPay()).isZero();
        assertThat(r1.grossPot()).isEqualTo(7_200_000L);
        assertThat(r1.T()).isEqualTo(100_000L);
        assertThat(r1.netPayout()).isEqualTo(7_100_000L);

        CycleInput c2 = bidding(1_000_000, 10, 1, 9, 150_000, 2, "FIXED_PER_CYCLE", 100_000, 0);
        CycleResult r2 = FormulaEngine.calculate(c2);
        assertThat(r2.grossPot()).isEqualTo(7_800_000L);
        assertThat(r2.netPayout()).isEqualTo(7_700_000L);

        CycleInput last = bidding(1_000_000, 10, 9, 1, 0, 10, "FIXED_PER_CYCLE", 100_000, 0);
        CycleResult rLast = FormulaEngine.calculate(last);
        assertThat(rLast.grossPot()).isEqualTo(9_000_000L);
        assertThat(rLast.netPayout()).isEqualTo(8_900_000L);
    }

    @Test
    void fixtureB_usdClassicIsCurrencyBlind() {
        CycleInput c1 = bidding(10_000, 10, 0, 10, 2_000, 1, "FIXED_PER_CYCLE", 500, 0);
        CycleResult r1 = FormulaEngine.calculate(c1);
        assertThat(r1.grossPot()).isEqualTo(72_000L);
        assertThat(r1.T()).isEqualTo(500L);
        assertThat(r1.netPayout()).isEqualTo(71_500L);
    }

    @Test
    void fixtureC_fixedEqualSameEveryCycle() {
        for (int cycleNo = 1; cycleNo <= 10; cycleNo++) {
            CycleInput in = new CycleInput(FIXED, 1, 1_000_000, 10, cycleNo - 1, 11 - cycleNo, 0,
                    cycleNo, 10, "FIXED_PER_CYCLE", 100_000, 0, 0, 0, 0);
            CycleResult r = FormulaEngine.calculate(in);
            assertThat(r.grossPot()).isEqualTo(9_000_000L);
            assertThat(r.netPayout()).isEqualTo(8_900_000L);
        }
    }

    @Test
    void fixtureD_rejectsInvalidInputs() {
        assertThatThrownBy(() -> FormulaEngine.calculate(
                bidding(1_000_000, 10, 0, 10, 1_000_000, 1, "NONE", 0, 0)))
                .isInstanceOf(FormulaException.class)
                .hasMessageContaining("B must be < C");

        assertThatThrownBy(() -> FormulaEngine.calculate(
                bidding(1_000_000, 10, 2, 9, 100_000, 1, "NONE", 0, 0)))   // A + D = 11
                .isInstanceOf(FormulaException.class)
                .hasMessageContaining("A + D must equal N");

        assertThatThrownBy(() -> FormulaEngine.calculate(
                bidding(1_000_000, 10, 9, 1, 50_000, 10, "NONE", 0, 0)))   // last-cycle B != 0
                .isInstanceOf(FormulaException.class)
                .hasMessageContaining("B == 0");

        assertThatThrownBy(() -> FormulaEngine.calculate(
                bidding(1_000_000, 10, 0, 10, 100_000, 1, "PERCENT_OF_POT", 0, 20_000)))  // T > grossPot
                .isInstanceOf(FormulaException.class)
                .hasMessageContaining("T exceeds grossPot");
    }

    @Test
    void roundBpsIsIntegerHalFUp() {
        assertThat(FormulaEngine.roundBps(1_000_000, 100)).isEqualTo(10_000L);
        assertThat(FormulaEngine.roundBps(1_000_000, 1000)).isEqualTo(100_000L);
        assertThat(FormulaEngine.roundBps(7_200_000, 100)).isEqualTo(72_000L);
        assertThat(FormulaEngine.roundBps(1, 5000)).isEqualTo(1L);        // 0.5 -> 1
        assertThat(FormulaEngine.roundBps(1, 2500)).isZero();             // 0.25 -> 0
        assertThat(FormulaEngine.roundBps(7_200_000, 10000)).isEqualTo(7_200_000L);
    }

    @Test
    void roundBpsHalfUpBoundaries() {
        for (int bps = 1; bps <= 10_000; bps++) {
            assertThat(FormulaEngine.roundBps(1, bps))
                    .as("roundBps(1, %d) must be 0 or 1 (integer, no fractions)", bps)
                    .isIn(0L, 1L);
        }
        assertThat(FormulaEngine.roundBps(3, 5000)).isEqualTo(2L);        // 1.5 exactly -> 2 (HALF_UP)
        assertThat(FormulaEngine.roundBps(7, 5000)).isEqualTo(4L);        // 3.5 exactly -> 4
        assertThat(FormulaEngine.roundBps(1, 4999)).isZero();             // 0.4999 -> 0
        assertThat(FormulaEngine.roundBps(1, 5001)).isEqualTo(1L);        // 0.5001 -> 1
        assertThat(FormulaEngine.roundBps(2, 3333)).isEqualTo(1L);        // 0.6666 -> 1
        assertThat(FormulaEngine.roundBps(9, 9999)).isEqualTo(9L);        // 8.9991 -> 9
        assertThat(FormulaEngine.roundBps(0, 5000)).isZero();             // 0 -> 0
        assertThat(FormulaEngine.roundBps(9_999_999_999L, 1)).isEqualTo(1_000_000L);
    }

    @Test
    void noFeePresetAliasForcesTZeroEvenWithFeeConfigured() {
        CycleInput in = new CycleInput(NO_FEE, 1, 1_000_000, 10, 0, 10, 200_000,
                1, 10, "FIXED_PER_CYCLE", 100_000, 0, 0, 900_000, 10_000);
        CycleResult r = FormulaEngine.calculate(in);
        assertThat(r.T()).isZero();
        assertThat(r.netPayout()).isEqualTo(r.grossPot()).isEqualTo(7_200_000L);
    }

    @Test
    void percentOfPotUsesRoundBps() {
        CycleInput in = bidding(1_000_000, 10, 0, 10, 200_000, 1, "PERCENT_OF_POT", 0, 100); // 1%
        CycleResult r = FormulaEngine.calculate(in);
        assertThat(r.T()).isEqualTo(72_000L);
        assertThat(r.netPayout()).isEqualTo(7_128_000L);
    }

    @Test
    void randomInputsHoldSectionTwoInvariants() {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int i = 0; i < 20; i++) {
            int N = rnd.nextInt(2, 21);
            long C = 1_000L * rnd.nextInt(1, 1000);
            int cycleNo = rnd.nextInt(1, N + 1);
            boolean last = cycleNo == N;
            int A = last ? 1 : rnd.nextInt(1, N);
            int D = N - A;
            long B = last ? 0 : rnd.nextInt(0, (int) C / 2 + 1);
            String preset = i % 2 == 0 ? CLASSIC : FIXED;
            if (FIXED.equals(preset) && last) {
                B = 0;
            }
            if (BIDDING(preset) && B >= C) {
                B = C - 1;
            }
            CycleInput in = new CycleInput(preset, 1, C, N, D, A, B, cycleNo, N,
                    "FIXED_PER_CYCLE", 1_000, 0, 0, C - 1, 10_000);
            CycleResult r = FormulaEngine.calculate(in);
            assertThat(r.winnerPay()).isZero();
            assertThat(r.deadPay()).isEqualTo(C);
            assertThat(r.alivePay()).isGreaterThanOrEqualTo(0);
            assertThat(r.T()).isGreaterThanOrEqualTo(0);
            assertThat(r.grossPot()).isGreaterThanOrEqualTo(0);
            assertThat(r.netPayout()).isEqualTo(r.grossPot() - r.T()).isGreaterThanOrEqualTo(0);
        }
    }

    private boolean BIDDING(String preset) {
        return !FIXED.equals(preset);
    }
}