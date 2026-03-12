package com.loopers.domain.like;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LikeModelTest {

    @DisplayName("좋아요 생성")
    @Nested
    class Create {

        @DisplayName("유효한 정보로 생성할 수 있다")
        @Test
        void createsWithValidInput() {
            // given
            Long userId = 1L;
            Long productId = 2L;

            // when
            LikeModel like = new LikeModel(userId, productId);

            // then
            assertAll(
                () -> assertThat(like.userId()).isEqualTo(userId),
                () -> assertThat(like.productId()).isEqualTo(productId)
            );
        }

        @DisplayName("userId가 null이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsWhenUserIdNull() {
            // given & when
            CoreException result = assertThrows(CoreException.class,
                () -> new LikeModel(null, 1L));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("productId가 null이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsWhenProductIdNull() {
            // given & when
            CoreException result = assertThrows(CoreException.class,
                () -> new LikeModel(1L, null));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
