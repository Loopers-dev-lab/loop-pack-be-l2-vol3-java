package com.loopers.support;

import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.loopers.infrastructure.payment.pg.PgPaymentHttpInterface;

@TestConfiguration
public class MockPgClientConfig {

    @Bean
    @Primary
    PgPaymentHttpInterface mockPgPaymentHttpInterface() {
        return Mockito.mock(PgPaymentHttpInterface.class);
    }
}
