package com.loopers.domain.log;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class EventLogTest {

    @Nested
    class 처리_완료 {

        @Test
        void processed하면_상태가_PROCESSED이다() {
            EventLog log = EventLog.processed("e-1", "product.liked", "product-interaction-events", "metrics-aggregation", 15);

            assertThat(log.getStatus()).isEqualTo(EventLogStatus.PROCESSED);
        }

        @Test
        void processed하면_durationMs가_기록된다() {
            EventLog log = EventLog.processed("e-1", "product.liked", "product-interaction-events", "metrics-aggregation", 42);

            assertThat(log.getDurationMs()).isEqualTo(42);
        }

        @Test
        void processed하면_errorMessage가_null이다() {
            EventLog log = EventLog.processed("e-1", "product.liked", "product-interaction-events", "metrics-aggregation", 10);

            assertThat(log.getErrorMessage()).isNull();
        }
    }

    @Nested
    class 스킵 {

        @Test
        void skipped하면_상태가_SKIPPED이다() {
            EventLog log = EventLog.skipped("e-1", "product.liked", "product-interaction-events", "metrics-aggregation");

            assertThat(log.getStatus()).isEqualTo(EventLogStatus.SKIPPED);
        }

        @Test
        void skipped하면_durationMs가_0이다() {
            EventLog log = EventLog.skipped("e-1", "product.liked", "product-interaction-events", "metrics-aggregation");

            assertThat(log.getDurationMs()).isZero();
        }
    }

    @Nested
    class 실패 {

        @Test
        void failed하면_상태가_FAILED이다() {
            EventLog log = EventLog.failed("e-1", "product.liked", "product-interaction-events", "metrics-aggregation", "DB 오류", 30);

            assertThat(log.getStatus()).isEqualTo(EventLogStatus.FAILED);
        }

        @Test
        void failed하면_errorMessage가_기록된다() {
            EventLog log = EventLog.failed("e-1", "product.liked", "product-interaction-events", "metrics-aggregation", "DB 오류", 30);

            assertThat(log.getErrorMessage()).isEqualTo("DB 오류");
        }

        @Test
        void failed하면_durationMs가_기록된다() {
            EventLog log = EventLog.failed("e-1", "product.liked", "product-interaction-events", "metrics-aggregation", "오류", 55);

            assertThat(log.getDurationMs()).isEqualTo(55);
        }

        @Test
        void errorMessage가_500자_초과하면_500자로_잘린다() {
            String longMessage = "x".repeat(600);

            EventLog log = EventLog.failed("e-1", "product.liked", "product-interaction-events", "metrics-aggregation", longMessage, 10);

            assertThat(log.getErrorMessage()).hasSize(500);
        }

        @Test
        void errorMessage가_null이면_null로_저장된다() {
            EventLog log = EventLog.failed("e-1", "product.liked", "product-interaction-events", "metrics-aggregation", null, 10);

            assertThat(log.getErrorMessage()).isNull();
        }
    }
}
