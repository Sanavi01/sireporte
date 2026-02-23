package com.jirehcompanyit.transactionservice.domain;

import com.jirehcompanyit.transactionservice.domain.model.Transaction;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class TransactionDomainValidationTest {

    @Test
    void constructingTransaction_withZeroAmount_shouldThrow() {
        // Given
        String userId = "user-123";
        String type = "DEBIT";
        BigDecimal amount = BigDecimal.ZERO;
        String currency = "COP";
        String category = "GROCERIES";
        Instant timestamp = Instant.parse("2026-02-22T17:00:00Z");

        // When / Then
        assertThrows(IllegalArgumentException.class, () -> {
            new Transaction(userId, type, amount, currency, category, timestamp);
        });
    }
}
