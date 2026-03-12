package com.loopers.domain.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ProductLikeModelTest {

    @DisplayName("좋아요를 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("유효한 userId와 productId가 주어지면, 정상적으로 생성된다.")
        @Test
        void create_whenValidValues() {
            // arrange
            Long userId = 1L;
            Long productId = 2L;

            // act
            ProductLikeModel like = ProductLikeModel.create(userId, productId);

            // assert
            assertAll(
                () -> assertThat(like.getUserId()).isEqualTo(userId),
                () -> assertThat(like.getProductId()).isEqualTo(productId)
            );
        }

        @DisplayName("userId가 null이면 예외가 발생한다.")
        @Test
        void create_whenUserIdIsNull() {
            assertThatThrownBy(() -> ProductLikeModel.create(null, 2L))
                .isInstanceOf(CoreException.class);
        }

        @DisplayName("productId가 null이면 예외가 발생한다.")
        @Test
        void create_whenProductIdIsNull() {
            assertThatThrownBy(() -> ProductLikeModel.create(1L, null))
                .isInstanceOf(CoreException.class);
        }
    }
}
