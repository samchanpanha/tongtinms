package com.tongtin.groups.shares.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "group_shares")
public class GroupShare {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "member_profile_id", nullable = false)
    private Long memberProfileId;

    @Column(name = "share_no", nullable = false)
    private int shareNo;

    @Column(name = "status", nullable = false)
    private String status = "ALIVE";

    @Column(name = "won_cycle_id")
    private Long wonCycleId;

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

    public Long getMemberProfileId() {
        return memberProfileId;
    }

    public void setMemberProfileId(Long memberProfileId) {
        this.memberProfileId = memberProfileId;
    }

    public int getShareNo() {
        return shareNo;
    }

    public void setShareNo(int shareNo) {
        this.shareNo = shareNo;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getWonCycleId() {
        return wonCycleId;
    }

    public void setWonCycleId(Long wonCycleId) {
        this.wonCycleId = wonCycleId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
