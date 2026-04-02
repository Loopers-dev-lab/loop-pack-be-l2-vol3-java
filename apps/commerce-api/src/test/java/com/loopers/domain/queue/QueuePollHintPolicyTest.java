package com.loopers.domain.queue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link QueuePollHintPolicy} 테스트
 */
class QueuePollHintPolicyTest {

    @Nested
    @DisplayName("suggestedPollIntervalMs")
    class SuggestedPollIntervalMs {

        @ParameterizedTest
        @CsvSource({
                "0, 1000",
                "50, 1000",
                "100, 1000",
                "101, 3000",
                "1000, 3000",
                "1001, 5000",
                "10000, 5000",
                "10001, 10000",
                "9223372036854775807, 10000"
        })
        void shouldMatchRoadmapBands(long position, long expectedMs) {
            assertThat(QueuePollHintPolicy.suggestedPollIntervalMs(position)).isEqualTo(expectedMs);
        }
    }

    @Nested
    @DisplayName("retryAfterSeconds")
    class RetryAfterSeconds {

        @ParameterizedTest
        @CsvSource({
                "0, 1",
                "100, 1",
                "101, 3",
                "1000, 3",
                "1001, 5",
                "10000, 5",
                "10001, 10",
                "9223372036854775807, 10"
        })
        void shouldMatchRoadmapBands(long position, long expectedSec) {
            assertThat(QueuePollHintPolicy.retryAfterSeconds(position)).isEqualTo(expectedSec);
        }
    }
}
