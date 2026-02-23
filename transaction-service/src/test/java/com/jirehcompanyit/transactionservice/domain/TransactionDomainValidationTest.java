package com.jirehcompanyit.transactionservice.domain;

import com.jirehcompanyit.transactionservice.domain.model.Transaction;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TransactionDomainValidationTest {

    private static final Logger log = LoggerFactory.getLogger(TransactionDomainValidationTest.class);

    @Test
    void constructingTransaction_withZeroAmount_shouldThrow() {
        // Given
        String userId = "user-123";
        String type = "DEBIT";
        BigDecimal amount = BigDecimal.ZERO;
        String currency = "COP";
        String category = "GROCERIES";
        Instant timestamp = Instant.parse("2026-02-22T17:00:00Z");

        log.info("[Given] userId={}, type={}, amount={}, currency={}, category={}, timestamp={}",
                userId, type, amount, currency, category, timestamp);

        // When / Then
        log.info("[When] constructing Transaction with amount=0 expecting IllegalArgumentException");
        assertThrows(IllegalArgumentException.class, () -> {
            new Transaction(userId, type, amount, currency, category, timestamp);
        });
        log.info("[Then] IllegalArgumentException was thrown as expected");
    }

    @Test
    void constructingTransaction_withNullType_shouldThrow() {
        // Given
        String userId = "user-123";
        String type = null;
        BigDecimal amount = new BigDecimal("10.00");
        String currency = "COP";
        String category = "GROCERIES";
        Instant timestamp = Instant.parse("2026-02-22T17:00:00Z");

        log.info("[Given] userId={}, type=null, amount={}, currency={}, category={}, timestamp={}",
                userId, amount, currency, category, timestamp);

        // When / Then
        log.info("[When] constructing Transaction with null type expecting IllegalArgumentException");
        assertThrows(IllegalArgumentException.class, () -> {
            new Transaction(userId, type, amount, currency, category, timestamp);
        });
        log.info("[Then] IllegalArgumentException was thrown as expected for null type");
    }

    @Test
    void constructingTransaction_withMinimumAmount_shouldSucceed() {
        // Given
        String userId = "user-123";
        String type = "DEBIT";
        BigDecimal amount = new BigDecimal("0.01");
        String currency = "COP";
        String category = "GROCERIES";
        Instant timestamp = Instant.parse("2026-02-22T17:00:00Z");

        log.info("[Given] userId={}, type={}, amount={}, currency={}, category={}, timestamp={}",
                userId, type, amount, currency, category, timestamp);

        // When
        log.info("[When] constructing Transaction with amount=0.01 expecting success");
        Transaction tx = assertDoesNotThrow(() -> new Transaction(userId, type, amount, currency, category, timestamp));

        // Then
        assertEquals(amount, tx.getAmount());
        assertEquals(userId, tx.getUserId());
        log.info("[Then] Transaction created successfully with id={}", tx.getId());
    }
}
