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
 * 역할: commerce-api 전체 컨텍스트가 뜨는지, 결제 연동에 필요한 Feign·Resilience4j·PG 클라이언트
 * 빈이 스캔되는지 최소 스모크로 확인한다 (06 checklist Phase 0).
 */
@SpringBootTest
class CommerceApiContextTest {

    @Autowired
    private ApplicationContext applicationContext;

    /** 애플리케이션 설정·빈 정의 오류가 없으면 통과. */
    @Test
    void contextLoads() {
        assertThat(applicationContext).isNotNull();
    }

    /** 결제 Facade가 의존하는 회복력·외부호출 빈 존재 여부. */
    @Test
    void context_shouldContainPaymentResilienceAndFeignBeans() {
        assertThat(applicationContext.getBean(CircuitBreakerRegistry.class)).isNotNull();
        assertThat(applicationContext.getBean(PgPaymentRequester.class)).isNotNull();
        assertThat(applicationContext.getBean(PgSimulatorClient.class)).isNotNull();
    }
}
