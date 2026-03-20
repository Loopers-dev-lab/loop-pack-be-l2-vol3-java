package com.loopers.application.payment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 5: Retry exponential backoff·jitter가 application.yml에 선언되어 있는지 검증 (06 §4.2, §12).
 */
class PaymentResilienceRetryYamlContentTest {

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
