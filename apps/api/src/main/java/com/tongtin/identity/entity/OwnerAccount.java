package com.tongtin.identity.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "owner_accounts")
public class OwnerAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "status", nullable = false)
    private String status = "ACTIVE";

    @Column(name = "cccd")
    private String cccd;

    @Column(name = "bank_name")
    private String bankName;

    @Column(name = "bank_account")
    private String bankAccount;

    @Column(name = "account_holder")
    private String accountHolder;

    @Column(name = "zalo")
    private String zalo;

    @Column(name = "city")
    private String city;

    @Column(name = "telegram_chat_id")
    private Long telegramChatId;

    @Column(name = "default_currency", nullable = false)
    private String defaultCurrency = "VND";

    @Column(name = "subscription_status", nullable = false)
    private String subscriptionStatus = "TRIAL";

    @Column(name = "trial_ends_at", nullable = false)
    private Instant trialEndsAt;

    @Column(name = "subscription_ends_at", nullable = false)
    private Instant subscriptionEndsAt;

    @Column(name = "current_plan_id")
    private Long currentPlanId;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCccd() {
        return cccd;
    }

    public void setCccd(String cccd) {
        this.cccd = cccd;
    }

    public String getBankName() {
        return bankName;
    }

    public void setBankName(String bankName) {
        this.bankName = bankName;
    }

    public String getBankAccount() {
        return bankAccount;
    }

    public void setBankAccount(String bankAccount) {
        this.bankAccount = bankAccount;
    }

    public String getAccountHolder() {
        return accountHolder;
    }

    public void setAccountHolder(String accountHolder) {
        this.accountHolder = accountHolder;
    }

    public String getZalo() {
        return zalo;
    }

    public void setZalo(String zalo) {
        this.zalo = zalo;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public Long getTelegramChatId() {
        return telegramChatId;
    }

    public void setTelegramChatId(Long telegramChatId) {
        this.telegramChatId = telegramChatId;
    }

    public String getDefaultCurrency() {
        return defaultCurrency;
    }

    public void setDefaultCurrency(String defaultCurrency) {
        this.defaultCurrency = defaultCurrency;
    }

    public String getSubscriptionStatus() {
        return subscriptionStatus;
    }

    public void setSubscriptionStatus(String subscriptionStatus) {
        this.subscriptionStatus = subscriptionStatus;
    }

    public Instant getTrialEndsAt() {
        return trialEndsAt;
    }

    public void setTrialEndsAt(Instant trialEndsAt) {
        this.trialEndsAt = trialEndsAt;
    }

    public Instant getSubscriptionEndsAt() {
        return subscriptionEndsAt;
    }

    public void setSubscriptionEndsAt(Instant subscriptionEndsAt) {
        this.subscriptionEndsAt = subscriptionEndsAt;
    }

    public Long getCurrentPlanId() {
        return currentPlanId;
    }

    public void setCurrentPlanId(Long currentPlanId) {
        this.currentPlanId = currentPlanId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
