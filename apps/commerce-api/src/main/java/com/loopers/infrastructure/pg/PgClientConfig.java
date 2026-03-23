package com.loopers.infrastructure.pg;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Slf4j
@Configuration
public class PgClientConfig {

    @Bean
    public RestTemplate pgRestTemplate(
        @Value("${pg.timeout.connect-millis:3000}") int connectMillis,
        @Value("${pg.timeout.read-millis:5000}") int readMillis
    ) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectMillis);
        factory.setReadTimeout(readMillis);
        return new RestTemplate(factory);
    }

    @Bean
    public Customizer<Resilience4JCircuitBreakerFactory> pgCircuitBreakerCustomizer() {
        CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
            .slidingWindowSize(10)
            .failureRateThreshold(50)
            .slowCallDurationThreshold(Duration.ofSeconds(3))
            .slowCallRateThreshold(50)
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .permittedNumberOfCallsInHalfOpenState(3)
            .build();

        // TimeLimiter는 RestTemplate read timeout(5s)보다 여유를 두어 설정
        TimeLimiterConfig tlConfig = TimeLimiterConfig.custom()
            .timeoutDuration(Duration.ofSeconds(6))
            .build();

        return factory -> {
            factory.configureDefault(id ->
                new Resilience4JConfigBuilder(id)
                    .circuitBreakerConfig(cbConfig)
                    .timeLimiterConfig(tlConfig)
                    .build()
            );

            // 서킷브레이커 상태 전이 로깅
            factory.addCircuitBreakerCustomizer(circuitBreaker ->
                circuitBreaker.getEventPublisher()
                    .onStateTransition(event ->
                        log.info("[CircuitBreaker] {} 상태 전이: {} → {}",
                            event.getCircuitBreakerName(),
                            event.getStateTransition().getFromState(),
                            event.getStateTransition().getToState()
                        )
                    ),
                "pg-payment", "pg-query"
            );
        };
    }

    /**
     * PG 조회 API 전용 RetryTemplate.
     * GET 요청(멱등)에만 적용 — 결제 요청(POST)에는 절대 사용하지 않는다.
     */
    @Bean
    public RetryTemplate pgQueryRetryTemplate() {
        return RetryTemplate.builder()
            .maxAttempts(2)
            .fixedBackoff(500)
            .retryOn(Exception.class)
            .build();
    }
}
