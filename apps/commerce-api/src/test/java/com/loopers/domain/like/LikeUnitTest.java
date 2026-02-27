package com.loopers.domain.like;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LikeUnitTest {

    @DisplayName("좋아요를 생성할 때,")
    @Nested
    class Create {

        @DisplayName("userId와 productId가 정상이면, Like가 생성된다.")
        @Test
        void createsLike_whenUserIdAndProductIdAreValid() {
            // arrange
            Long userId = 1L;
            Long productId = 100L;

            // act
            Like like = new Like(userId, productId);

            // assert
            assertThat(like.getUserId()).isEqualTo(1L);
            assertThat(like.getProductId()).isEqualTo(100L);
        }

        @DisplayName("userId가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsException_whenUserIdIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new Like(null, 100L));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("productId가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsException_whenProductIdIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new Like(1L, null));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
