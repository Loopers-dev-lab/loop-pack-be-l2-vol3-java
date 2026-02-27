package com.loopers.domain.like;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LikeTest {

    @DisplayName("Like 생성 시")
    @Nested
    class Create {

        @DisplayName("userId와 productId를 정상적으로 제공하면 Like가 생성된다.")
        @Test
        void createsLike_whenValidUserIdAndProductId() {
            // arrange
            Long userId = 1L;
            Long productId = 2L;

            // act
            Like like = new Like(userId, productId);

            // assert
            assertThat(like.getUserId()).isEqualTo(userId);
            assertThat(like.getProductId()).isEqualTo(productId);
        }

        @DisplayName("userId가 null이면 BAD_REQUEST 에러가 발생한다.")
        @Test
        void throwsBadRequest_whenUserIdIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class,
                    () -> new Like(null, 2L));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("productId가 null이면 BAD_REQUEST 에러가 발생한다.")
        @Test
        void throwsBadRequest_whenProductIdIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class,
                    () -> new Like(1L, null));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
