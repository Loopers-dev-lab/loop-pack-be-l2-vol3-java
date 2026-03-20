package com.loopers.domain.payment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CallbackInboxTest {

    @DisplayName("U4-7: 콜백 원본 저장 → RECEIVED 상태 확인")
    @Test
    void create_callbackInbox_receivedStatus() {
        CallbackInbox inbox = CallbackInbox.create(
            "TX-001", 1L, "SUCCESS", "{\"status\":\"SUCCESS\",\"transactionKey\":\"TX-001\"}");

        assertThat(inbox.getTransactionKey()).isEqualTo("TX-001");
        assertThat(inbox.getOrderId()).isEqualTo(1L);
        assertThat(inbox.getPgStatus()).isEqualTo("SUCCESS");
        assertThat(inbox.getPayload()).contains("TX-001");
        assertThat(inbox.getStatus()).isEqualTo(CallbackInboxStatus.RECEIVED);
        assertThat(inbox.getRetryCount()).isZero();
    }

    @DisplayName("콜백 처리 완료 → PROCESSED 상태 전이")
    @Test
    void markProcessed_changesStatus() {
        CallbackInbox inbox = CallbackInbox.create("TX-001", 1L, "SUCCESS", "{}");

        inbox.markProcessed();

        assertThat(inbox.getStatus()).isEqualTo(CallbackInboxStatus.PROCESSED);
        assertThat(inbox.getProcessedAt()).isNotNull();
    }

    @DisplayName("콜백 처리 실패 → FAILED 상태 + retryCount 증가")
    @Test
    void markFailed_changesStatusAndIncrementsRetry() {
        CallbackInbox inbox = CallbackInbox.create("TX-001", 1L, "SUCCESS", "{}");

        inbox.markFailed("Payment not found");

        assertThat(inbox.getStatus()).isEqualTo(CallbackInboxStatus.FAILED);
        assertThat(inbox.getErrorMessage()).isEqualTo("Payment not found");
        assertThat(inbox.getRetryCount()).isEqualTo(1);
    }
}
