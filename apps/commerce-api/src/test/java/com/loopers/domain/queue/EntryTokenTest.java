package com.loopers.domain.queue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EntryTokenTest {

    @DisplayName("EntryToken을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("올바른 정보이면, 정상 생성된다.")
        @Test
        void createsToken_whenValidInfo() {
            EntryToken token = new EntryToken(1L, "uuid-token", 1000L);

            assertThat(token.userId()).isEqualTo(1L);
            assertThat(token.token()).isEqualTo("uuid-token");
            assertThat(token.activateAt()).isEqualTo(1000L);
        }

        @DisplayName("userId가 null이면, NullPointerException이 발생한다.")
        @Test
        void throwsNpe_whenUserIdIsNull() {
            assertThrows(NullPointerException.class,
                () -> new EntryToken(null, "uuid-token", 1000L));
        }

        @DisplayName("token이 null이면, NullPointerException이 발생한다.")
        @Test
        void throwsNpe_whenTokenIsNull() {
            assertThrows(NullPointerException.class,
                () -> new EntryToken(1L, null, 1000L));
        }
    }

    @DisplayName("activateAt을 검증할 때, ")
    @Nested
    class IsActivated {

        @DisplayName("현재 시간이 activateAt 이후이면, true를 반환한다.")
        @Test
        void returnsTrue_whenNowIsAfterActivateAt() {
            EntryToken token = new EntryToken(1L, "uuid-token", 1000L);

            assertThat(token.isActivated(1000L)).isTrue();
            assertThat(token.isActivated(1500L)).isTrue();
        }

        @DisplayName("현재 시간이 activateAt 이전이면, false를 반환한다.")
        @Test
        void returnsFalse_whenNowIsBeforeActivateAt() {
            EntryToken token = new EntryToken(1L, "uuid-token", 1000L);

            assertThat(token.isActivated(999L)).isFalse();
        }
    }
}
