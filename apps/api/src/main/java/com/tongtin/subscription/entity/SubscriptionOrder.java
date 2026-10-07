package com.tongtin.subscription.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "subscription_orders")
public class SubscriptionOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    @Column(name = "tran_id", nullable = false, unique = true)
    private String tranId;

    @Column(name = "amount_minor", nullable = false)
    private Long amountMinor;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Column(name = "status", nullable = false)
    private String status = "PENDING"; // PENDING | PAID | FAILED | CANCELLED

    @Column(name = "payment_gateway", nullable = false)
    private String paymentGateway = "ABA_PAYWAY";

    @Column(name = "gateway_tran_id")
    private String gatewayTranId;

    @Column(name = "gateway_response_json")
    private String gatewayResponseJson;

    @Column(name = "payway_hash")
    private String paywayHash;

    @Column(name = "req_time")
    private String reqTime;

    @Column(name = "paid_at")
    private Instant paidAt;

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

    public Long getPlanId() {
        return planId;
    }

    public void setPlanId(Long planId) {
        this.planId = planId;
    }

    public String getTranId() {
        return tranId;
    }

    public void setTranId(String tranId) {
        this.tranId = tranId;
    }

    public Long getAmountMinor() {
        return amountMinor;
    }

    public void setAmountMinor(Long amountMinor) {
        this.amountMinor = amountMinor;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPaymentGateway() {
        return paymentGateway;
    }

    public void setPaymentGateway(String paymentGateway) {
        this.paymentGateway = paymentGateway;
    }

    public String getGatewayTranId() {
        return gatewayTranId;
    }

    public void setGatewayTranId(String gatewayTranId) {
        this.gatewayTranId = gatewayTranId;
    }

    public String getGatewayResponseJson() {
        return gatewayResponseJson;
    }

    public void setGatewayResponseJson(String gatewayResponseJson) {
        this.gatewayResponseJson = gatewayResponseJson;
    }

    public String getPaywayHash() {
        return paywayHash;
    }

    public void setPaywayHash(String paywayHash) {
        this.paywayHash = paywayHash;
    }

    public String getReqTime() {
        return reqTime;
    }

    public void setReqTime(String reqTime) {
        this.reqTime = reqTime;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(Instant paidAt) {
        this.paidAt = paidAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
