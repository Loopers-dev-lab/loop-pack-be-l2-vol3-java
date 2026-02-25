package com.loopers.domain.like;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LikeModelTest {

    private static final Long USER_ID = 1L;
    private static final Long PRODUCT_ID = 100L;

    @DisplayName("create 시")
    @Nested
    class Create {

        @DisplayName("유효한 userId와 productId가 주어지면 생성된다.")
        @Test
        void create_withValidInputs_shouldSucceed() {
            // given
            // when
            LikeModel like = LikeModel.create(USER_ID, PRODUCT_ID);

            // then
            assertThat(like.getUserId()).isEqualTo(USER_ID);
            assertThat(like.getProductId()).isEqualTo(PRODUCT_ID);
        }

        @DisplayName("userId가 null이면 IllegalArgumentException이 발생한다.")
        @Test
        void create_withNullUserId_shouldThrow() {
            // when & then
            assertThrows(IllegalArgumentException.class, () ->
                LikeModel.create(null, PRODUCT_ID));
        }

        @DisplayName("productId가 null이면 IllegalArgumentException이 발생한다.")
        @Test
        void create_withNullProductId_shouldThrow() {
            // when & then
            assertThrows(IllegalArgumentException.class, () ->
                LikeModel.create(USER_ID, null));
        }
    }
}
