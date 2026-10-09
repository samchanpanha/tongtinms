package com.tongtin.groups.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record GroupCreateRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Pattern(regexp = "^(BIDDING|FIXED)$") String type,
        @Positive long baseAmount,
        @Min(value = 2, message = "shareCount must be >= 2") int shareCount,
        @NotBlank @Pattern(regexp = "^(DAY|WEEK|MONTH)$") String cycleUnit,
        @Min(value = 1, message = "cycleCount must be >= 1") int cycleCount,
        @NotBlank @Size(min = 3, max = 3) String currency,
        LocalDate startAt,
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
