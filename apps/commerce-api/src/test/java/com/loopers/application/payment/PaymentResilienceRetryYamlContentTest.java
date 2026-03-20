package com.loopers.application.payment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 역할: Spring Environment 바인딩 대신 classpath application.yml 문자열로 Retry 백오프 설정 존재를 검증한다.
 * Gradle 테스트 JVM에서 일부 프로퍼티가 비어 실패하는 경우를 피하고, 문서(06 §4.2·§12)와 YAML 정합성만 본다.
 */
class PaymentResilienceRetryYamlContentTest {

    /** pgRetry 인스턴스에 지수 백오프·랜덤 대기 키가 선언돼 있는지 확인. */
    @Test
    @DisplayName("application.yml pgRetry에 exponential backoff·randomized wait 설정이 있다.")
    void applicationYml_shouldDeclarePgRetryBackoffAndJitter() throws Exception {
        // given / when
        String content;
        try (var in = PaymentResilienceRetryYamlContentTest.class.getResourceAsStream("/application.yml")) {
            assertThat(in).as("classpath:/application.yml").isNotNull();
            content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        // then
        assertThat(content)
                .contains("enable-exponential-backoff: true")
                .contains("exponential-backoff-multiplier: 2")
                .contains("enable-randomized-wait: true")
                .contains("randomized-wait-factor: 0.5");
    }
}
