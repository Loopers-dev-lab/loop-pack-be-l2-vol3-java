package com.loopers.domain.outbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class OutboxEventTest {

    @DisplayName("OutboxEvent 생성 시 PENDING 상태와 UUID eventId가 할당된다")
    @Test
    void createsWithPendingStatusAndEventId() {
        // given & when
        OutboxEvent event = new OutboxEvent(
            "Product", 1L, "LIKED", "catalog-events", "1", "{}"
        );

        // then
        assertAll(
            () -> assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING),
            () -> assertThat(event.getEventId()).isNotNull(),
            () -> assertThat(event.getEventId()).hasSize(36),
            () -> assertThat(event.getRetryCount()).isZero(),
            () -> assertThat(event.getCreatedAt()).isNotNull()
        );
    }

    @DisplayName("markProcessing → PROCESSING 상태로 전환")
    @Test
    void markProcessingChangesStatus() {
        // given
        OutboxEvent event = new OutboxEvent("Product", 1L, "LIKED", "catalog-events", "1", "{}");

        // when
        event.markProcessing();

        // then
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PROCESSING);
    }

    @DisplayName("markPublished → PUBLISHED 상태 + publishedAt 설정")
    @Test
    void markPublishedSetsPublishedAt() {
        // given
        OutboxEvent event = new OutboxEvent("Product", 1L, "LIKED", "catalog-events", "1", "{}");
        event.markProcessing();

        // when
        event.markPublished();

        // then
        assertAll(
            () -> assertThat(event.getStatus()).isEqualTo(OutboxStatus.PUBLISHED),
            () -> assertThat(event.getPublishedAt()).isNotNull()
        );
    }

    @DisplayName("markFailed → FAILED 상태 + retryCount 증가 + errorMessage 저장")
    @Test
    void markFailedIncrementsRetryCount() {
        // given
        OutboxEvent event = new OutboxEvent("Product", 1L, "LIKED", "catalog-events", "1", "{}");
        event.markProcessing();

        // when
        event.markFailed("Connection timeout");

        // then
        assertAll(
            () -> assertThat(event.getStatus()).isEqualTo(OutboxStatus.FAILED),
            () -> assertThat(event.getRetryCount()).isEqualTo(1),
            () -> assertThat(event.getErrorMessage()).isEqualTo("Connection timeout")
        );
    }

    @DisplayName("markRetry → PENDING 상태로 복구")
    @Test
    void markRetryResetsToPending() {
        // given
        OutboxEvent event = new OutboxEvent("Product", 1L, "LIKED", "catalog-events", "1", "{}");
        event.markProcessing();
        event.markFailed("error");

        // when
        event.markRetry();

        // then
        assertAll(
            () -> assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING),
            () -> assertThat(event.getRetryCount()).isEqualTo(1) // retryCount는 유지
        );
    }
}
