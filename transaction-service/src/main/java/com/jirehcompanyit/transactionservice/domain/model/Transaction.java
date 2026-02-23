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
        // validate in logical parameter order: userId, type, amount
        Validator.validateUserId(userId);
        Validator.validateType(type);
        Validator.validateAmount(amount);
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

        private static final String ERR_AMOUNT = "amount must be > 0";
        private static final String ERR_TYPE = "type is required";
        private static final String ERR_USERID = "userId is required";

        static void validateAmount(BigDecimal amount) {
            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException(ERR_AMOUNT);
            }
        }

        static void validateType(String type) {
            if (type == null) {
                throw new IllegalArgumentException(ERR_TYPE);
            }
        }

        static void validateUserId(String userId) {
            if (userId == null || userId.isBlank()) {
                throw new IllegalArgumentException(ERR_USERID);
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
