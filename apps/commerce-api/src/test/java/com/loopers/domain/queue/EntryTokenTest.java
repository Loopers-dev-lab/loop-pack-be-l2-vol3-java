package com.loopers.domain.queue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EntryTokenTest {

    @DisplayName("isValid 호출 시, ")
    @Nested
    class IsValid {

        @DisplayName("만료 시각이 미래이면 true 를 반환한다.")
        @Test
        void returnsTrue_whenNotExpired() {
            // arrange
            long futureExpiry = System.currentTimeMillis() + 60_000L;
            EntryToken token = new EntryToken(1L, "uuid", futureExpiry);

            // act & assert
            assertThat(token.isValid()).isTrue();
        }

        @DisplayName("만료 시각이 과거이면 false 를 반환한다.")
        @Test
        void returnsFalse_whenExpired() {
            // arrange
            long pastExpiry = System.currentTimeMillis() - 1L;
            EntryToken token = new EntryToken(1L, "uuid", pastExpiry);

            // act & assert
            assertThat(token.isValid()).isFalse();
        }
    }
}
