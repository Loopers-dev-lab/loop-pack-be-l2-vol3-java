package com.loopers.domain.queue;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SessionStatusTest {

    @Nested
    class 값_변환 {

        @Test
        void 문자열로_상태를_생성할_수_있다() {
            assertThat(SessionStatus.valueOf("ACTIVE")).isEqualTo(SessionStatus.ACTIVE);
            assertThat(SessionStatus.valueOf("CONSUMED")).isEqualTo(SessionStatus.CONSUMED);
        }

        @Test
        void name으로_Redis_저장값과_일치하는_문자열을_반환한다() {
            assertThat(SessionStatus.ACTIVE.name()).isEqualTo("ACTIVE");
            assertThat(SessionStatus.CONSUMED.name()).isEqualTo("CONSUMED");
        }

        @Test
        void 존재하지_않는_상태_문자열이면_예외() {
            assertThatThrownBy(() -> SessionStatus.valueOf("EXPIRED"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class 전체_값_보장 {

        @Test
        void 상태는_2개이다() {
            assertThat(SessionStatus.values()).hasSize(2);
        }
    }
}
