package com.jirehcompanyit.transactionservice.domain;

import com.jirehcompanyit.transactionservice.domain.model.Transaction;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertThrows;

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
}
