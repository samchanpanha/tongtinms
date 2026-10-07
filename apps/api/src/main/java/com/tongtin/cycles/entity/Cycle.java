package com.tongtin.cycles.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "cycles")
public class Cycle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "cycle_no", nullable = false)
    private int cycleNo;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "open_at", nullable = false, insertable = false, updatable = false)
    private Instant openAt;

    @Column(name = "bid_close_at")
    private Instant bidCloseAt;

    @Column(name = "due_at")
    private Instant dueAt;

    @Column(name = "winner_share_id")
    private Long winnerShareId;

    @Column(name = "winning_bid")
    private Long winningBid;

    @Column(name = "gross_pot")
    private Long grossPot;

    @Column(name = "host_fee")
    private Long hostFee;

    @Column(name = "net_payout")
    private Long netPayout;

    @Column(name = "calculated_at")
    private Instant calculatedAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

    public int getCycleNo() {
        return cycleNo;
    }

    public void setCycleNo(int cycleNo) {
        this.cycleNo = cycleNo;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public Instant getOpenAt() {
        return openAt;
    }

    public Instant getBidCloseAt() {
        return bidCloseAt;
    }

    public void setBidCloseAt(Instant bidCloseAt) {
        this.bidCloseAt = bidCloseAt;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public void setDueAt(Instant dueAt) {
        this.dueAt = dueAt;
    }

    public Long getWinnerShareId() {
        return winnerShareId;
    }

    public void setWinnerShareId(Long winnerShareId) {
        this.winnerShareId = winnerShareId;
    }

    public Long getWinningBid() {
        return winningBid;
    }

    public void setWinningBid(Long winningBid) {
        this.winningBid = winningBid;
    }

    public Long getGrossPot() {
        return grossPot;
    }

    public void setGrossPot(Long grossPot) {
        this.grossPot = grossPot;
    }

    public Long getHostFee() {
        return hostFee;
    }

    public void setHostFee(Long hostFee) {
        this.hostFee = hostFee;
    }

    public Long getNetPayout() {
        return netPayout;
    }

    public void setNetPayout(Long netPayout) {
        this.netPayout = netPayout;
    }

    public Instant getCalculatedAt() {
        return calculatedAt;
    }

    public void setCalculatedAt(Instant calculatedAt) {
        this.calculatedAt = calculatedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}