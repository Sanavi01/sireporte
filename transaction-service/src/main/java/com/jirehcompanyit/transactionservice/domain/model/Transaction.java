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
        Validator.validateAmount(amount);
        Validator.validateType(type);
        Validator.validateUserId(userId);
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.type = type;
        this.amount = amount;
        this.currency = currency;
        this.category = category;
        this.timestamp = timestamp;
    }

    /**
     * Internal validator for domain invariants. Kept private to the domain model.
     * Extracted to a nested class to improve readability and make future
     * validation rules easier to extend without changing constructor flow.
     */
    private static final class Validator {
        private Validator() { }

        static void validateAmount(BigDecimal amount) {
            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("amount must be > 0");
            }
        }

        static void validateType(String type) {
            if (type == null) {
                throw new IllegalArgumentException("type is required");
            }
        }

        static void validateUserId(String userId) {
            if (userId == null || userId.isBlank()) {
                throw new IllegalArgumentException("userId is required");
            }
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
