package com.loopers.domain.queue;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class QueueModeTest {

    @Nested
    class 값_변환 {

        @Test
        void 문자열로_도메인_모드를_생성할_수_있다() {
            assertThat(QueueMode.valueOf("EVENT")).isEqualTo(QueueMode.EVENT);
            assertThat(QueueMode.valueOf("DRAIN")).isEqualTo(QueueMode.DRAIN);
            assertThat(QueueMode.valueOf("NORMAL")).isEqualTo(QueueMode.NORMAL);
        }

        @Test
        void 존재하지_않는_모드_문자열이면_예외() {
            assertThatThrownBy(() -> QueueMode.valueOf("UNKNOWN"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class 전체_값_보장 {

        @Test
        void 모드는_3개이다() {
            assertThat(QueueMode.values()).hasSize(3);
        }
    }
}
