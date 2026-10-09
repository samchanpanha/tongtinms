package com.tongtin.cycles.dto;

/**
 * Step 10: host-provided winner hint for HOST_DECISION tiebreak.
 * Step 40: winnerSelectionMode = "RANDOM_EQUAL_MAX" triggers random draw
 *           among all ALIVE shares, treating each as having bid group.maxBid.
 *           Default (null / "HIGHEST_BID") is existing behaviour.
 */
public record CloseCalculateRequest(Long winnerShareId, String winnerSelectionMode) {
    /** Returns true when the host wants a random lottery draw. */
    public boolean isRandomEqualMax() {
        return "RANDOM_EQUAL_MAX".equalsIgnoreCase(winnerSelectionMode);
    }
}

