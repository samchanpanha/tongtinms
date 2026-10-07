package com.tongtin.groups.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record GroupPatchRequest(
        @Size(max = 120) String name,
        @Pattern(regexp = "^(BIDDING|FIXED)$") String type,
        @Positive Long baseAmount,
        @Min(value = 2) Integer shareCount,
        @Pattern(regexp = "^(DAY|WEEK|MONTH)$") String cycleUnit,
        @Min(value = 1) Integer cycleCount,
        @Size(min = 3, max = 3) String currency,
        String startAt,
        @Pattern(regexp = "^(NONE|FIXED_PER_CYCLE|PERCENT_OF_POT)$") String hostFeeType,
        @Min(0) Long hostFeeMinor,
        @Min(0) Integer hostFeeBps,
        @Min(0) Long minBid,
        @Min(0) Long maxBid,
        @Min(1) Long bidStep,
        @Pattern(regexp = "^(LOWEST_MEMBER_CODE|EARLIEST_BID|HOST_DECISION)$") String tieBreak,
        @Pattern(regexp = "^(NONE|FIXED|PERCENT_PER_DAY)$") String lateFeeType,
        @Min(0) Long lateFeeValue,
        @Min(0) Integer bidOpenOffset,
        @Min(0) Integer bidCloseOffset,
        Boolean allowMultiShare) {
}
