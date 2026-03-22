package com.loopers.domain.like;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

class LikeTest {

    @DisplayName("좋아요를 생성할 때,")
    @Nested
    class Create {

        @DisplayName("유효한 정보이면, 정상 생성된다.")
        @Test
        void success() {
            // arrange
            var userId = 1L;
            var productId = 1L;

            // act
            var like = Like.create(userId, productId);

            // assert
            assertAll(
                    () -> assertThat(like.getUserId()).isEqualTo(userId),
                    () -> assertThat(like.getProductId()).isEqualTo(productId),
                    () -> assertThat(like.getLikedAt()).isNotNull()
            );
        }

        @DisplayName("userId가 null이면, REQUIRED_USER_ID 에러가 발생한다.")
        @ParameterizedTest
        @NullSource
        void throwsException_whenUserIdIsNull(Long userId) {
            assertThatThrownBy(() -> Like.create(userId, 1L))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining(ErrorType.REQUIRED_USER_ID.getMessage());
        }

        @DisplayName("productId가 null이면, REQUIRED_PRODUCT_ID 에러가 발생한다.")
        @ParameterizedTest
        @NullSource
        void throwsException_whenProductIdIsNull(Long productId) {
            assertThatThrownBy(() -> Like.create(1L, productId))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining(ErrorType.REQUIRED_PRODUCT_ID.getMessage());
        }
    }

    @DisplayName("좋아요를 취소할 때,")
    @Nested
    class Unlike {

        @DisplayName("예외 없이 정상 수행된다.")
        @Test
        void success() {
            // arrange
            var like = Like.create(1L, 1L);

            // act & assert
            assertThatCode(like::unlike).doesNotThrowAnyException();
        }
    }
}