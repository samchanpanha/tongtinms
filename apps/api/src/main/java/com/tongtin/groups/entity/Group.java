package com.tongtin.groups.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "groups")
public class Group {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "base_amount", nullable = false)
    private long baseAmount;

    @Column(name = "share_count", nullable = false)
    private int shareCount;

    @Column(name = "cycle_unit", nullable = false)
    private String cycleUnit;

    @Column(name = "cycle_count", nullable = false)
    private int cycleCount;

    @Column(name = "start_at")
    private LocalDate startAt;

    @Column(name = "status", nullable = false)
    private String status = "DRAFT";

    @Column(name = "host_fee_type", nullable = false)
    private String hostFeeType = "NONE";

    @Column(name = "host_fee_minor", nullable = false)
    private long hostFeeMinor;

    @Column(name = "host_fee_bps", nullable = false)
    private int hostFeeBps;

    @Column(name = "min_bid", nullable = false)
    private long minBid;

    @Column(name = "max_bid", nullable = false)
    private long maxBid;

    @Column(name = "bid_step", nullable = false)
    private long bidStep = 1000;

    @Column(name = "tie_break", nullable = false)
    private String tieBreak = "EARLIEST_BID";

    @Column(name = "late_fee_type", nullable = false)
    private String lateFeeType = "NONE";

    @Column(name = "late_fee_value", nullable = false)
    private long lateFeeValue;

    @Column(name = "bid_open_offset", nullable = false)
    private int bidOpenOffset;

    @Column(name = "bid_close_offset", nullable = false)
    private int bidCloseOffset;

    @Column(name = "allow_multi_share", nullable = false)
    private boolean allowMultiShare = true;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "rules_frozen", nullable = false)
    private boolean rulesFrozen;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public long getBaseAmount() {
        return baseAmount;
    }

    public void setBaseAmount(long baseAmount) {
        this.baseAmount = baseAmount;
    }

    public int getShareCount() {
        return shareCount;
    }

    public void setShareCount(int shareCount) {
        this.shareCount = shareCount;
    }

    public String getCycleUnit() {
        return cycleUnit;
    }

    public void setCycleUnit(String cycleUnit) {
        this.cycleUnit = cycleUnit;
    }

    public int getCycleCount() {
        return cycleCount;
    }

    public void setCycleCount(int cycleCount) {
        this.cycleCount = cycleCount;
    }

    public LocalDate getStartAt() {
        return startAt;
    }

    public void setStartAt(LocalDate startAt) {
        this.startAt = startAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getHostFeeType() {
        return hostFeeType;
    }

    public void setHostFeeType(String hostFeeType) {
        this.hostFeeType = hostFeeType;
    }

    public long getHostFeeMinor() {
        return hostFeeMinor;
    }

    public void setHostFeeMinor(long hostFeeMinor) {
        this.hostFeeMinor = hostFeeMinor;
    }

    public int getHostFeeBps() {
        return hostFeeBps;
    }

    public void setHostFeeBps(int hostFeeBps) {
        this.hostFeeBps = hostFeeBps;
    }

    public long getMinBid() {
        return minBid;
    }

    public void setMinBid(long minBid) {
        this.minBid = minBid;
    }

    public long getMaxBid() {
        return maxBid;
    }

    public void setMaxBid(long maxBid) {
        this.maxBid = maxBid;
    }

    public long getBidStep() {
        return bidStep;
    }

    public void setBidStep(long bidStep) {
        this.bidStep = bidStep;
    }

    public String getTieBreak() {
        return tieBreak;
    }

    public void setTieBreak(String tieBreak) {
        this.tieBreak = tieBreak;
    }

    public String getLateFeeType() {
        return lateFeeType;
    }

    public void setLateFeeType(String lateFeeType) {
        this.lateFeeType = lateFeeType;
    }

    public long getLateFeeValue() {
        return lateFeeValue;
    }

    public void setLateFeeValue(long lateFeeValue) {
        this.lateFeeValue = lateFeeValue;
    }

    public int getBidOpenOffset() {
        return bidOpenOffset;
    }

    public void setBidOpenOffset(int bidOpenOffset) {
        this.bidOpenOffset = bidOpenOffset;
    }

    public int getBidCloseOffset() {
        return bidCloseOffset;
    }

    public void setBidCloseOffset(int bidCloseOffset) {
        this.bidCloseOffset = bidCloseOffset;
    }

    public boolean isAllowMultiShare() {
        return allowMultiShare;
    }

    public void setAllowMultiShare(boolean allowMultiShare) {
        this.allowMultiShare = allowMultiShare;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public boolean isRulesFrozen() {
        return rulesFrozen;
    }

    public void setRulesFrozen(boolean rulesFrozen) {
        this.rulesFrozen = rulesFrozen;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
