package com.loopers;

import com.loopers.application.payment.PgPaymentRequester;
import com.loopers.infrastructure.payment.PgSimulatorClient;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 0: 결제·Feign·Resilience4j 빈 로드 (06 checklist, {@code checklist.md}).
 */
@SpringBootTest
class CommerceApiContextTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void contextLoads() {
        // given / when / then — 컨텍스트 기동
        assertThat(applicationContext).isNotNull();
    }

    @Test
    void context_shouldContainPaymentResilienceAndFeignBeans() {
        // given / when / then
        assertThat(applicationContext.getBean(CircuitBreakerRegistry.class)).isNotNull();
        assertThat(applicationContext.getBean(PgPaymentRequester.class)).isNotNull();
        assertThat(applicationContext.getBean(PgSimulatorClient.class)).isNotNull();
    }
}
