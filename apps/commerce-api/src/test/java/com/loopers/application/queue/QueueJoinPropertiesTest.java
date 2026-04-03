package com.loopers.application.queue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QueueJoinPropertiesTest {

    @Test
    @DisplayName("max-waiting이 1 이상이면 setter 성공")
    void setMaxWaiting_withPositiveMax_shouldSucceed() {
        assertThatCode(
                        () -> {
                            var p = new QueueJoinProperties();
                            p.setMaxWaiting(1L);
                        })
                .doesNotThrowAnyException();
        assertThatCode(
                        () -> {
                            var p = new QueueJoinProperties();
                            p.setMaxWaiting(100_000L);
                        })
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("max-waiting이 1 미만이면 IllegalArgumentException")
    void setMaxWaiting_withNonPositiveMax_shouldThrow() {
        assertThatThrownBy(
                        () -> {
                            var p = new QueueJoinProperties();
                            p.setMaxWaiting(0L);
                        })
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () -> {
                            var p = new QueueJoinProperties();
                            p.setMaxWaiting(-1L);
                        })
                .isInstanceOf(IllegalArgumentException.class);
    }
}
