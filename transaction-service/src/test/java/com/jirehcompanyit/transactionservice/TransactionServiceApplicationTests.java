package com.jirehcompanyit.transactionservice;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class TransactionServiceApplicationTests {

    private static final Logger log = LoggerFactory.getLogger(TransactionServiceApplicationTests.class);

    @Test
    void contextLoads() {
        log.info("Application context loads for tests");
    }

}
