package com.loopers.application.payment;

import com.loopers.testcontainers.MySqlTestContainersConfig;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Phase 8: PG 폴링·수동 복구 유스케이스 (06 §14). 구현 전 placeholder.
 */
@SpringBootTest
@Import(MySqlTestContainersConfig.class)
@Disabled("Phase 8 recoverOrPoll API·배치 미구현 — checklist.md 참고")
class PaymentRecoverPollIntegrationTest {

    @Test
    @DisplayName("recoverOrPoll_whenPgReturnsSuccess_shouldReflectCompletePayment (미구현)")
    void recoverOrPoll_whenPgReturnsSuccess_shouldReflectCompletePayment() {
        // 구현 시: PENDING → PG 조회 SUCCESS → completePayment·상태 반영
    }

    @Test
    @DisplayName("recoverOrPoll_whenPgReturnsNotAccepted_shouldMarkTimeoutOrFailed (미구현)")
    void recoverOrPoll_whenPgReturnsNotAccepted_shouldMarkTimeoutOrFailed() {
        // 구현 시: PG에 주문 없음 → TIMEOUT/FAILED 정책 반영
    }
}
