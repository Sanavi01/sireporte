package com.jirehcompanyit.transactionservice.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class Transaction {

    private final UUID id;
    private final String userId;
    private final String type;
    private final BigDecimal amount;
    private final String currency;
    private final String category;
    private final Instant timestamp;

    public Transaction(String userId,
                       String type,
                       BigDecimal amount,
                       String currency,
                       String category,
                       Instant timestamp) {
        validateAmount(amount);
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.type = type;
        this.amount = amount;
        this.currency = currency;
        this.category = category;
        this.timestamp = timestamp;
    }

    private static void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("amount must be > 0");
        }
    }

    public UUID getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getCategory() {
        return category;
    }

    public Instant getTimestamp() {
        return timestamp;
    }
}
