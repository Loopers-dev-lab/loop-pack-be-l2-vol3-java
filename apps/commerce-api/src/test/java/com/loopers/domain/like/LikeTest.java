package com.loopers.domain.like;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

class LikeTest {

    @DisplayName("좋아요를 생성할 때")
    @Nested
    class Create {

        @DisplayName("유효한 회원ID와 상품ID가 주어지면 성공한다")
        @Test
        void success() {
            Long memberId = 1L;
            Long productId = 100L;

            Like like = new Like(memberId, productId);

            assertAll(
                () -> assertThat(like.getMemberId()).isEqualTo(memberId),
                () -> assertThat(like.getProductId()).isEqualTo(productId)
            );
        }

        @DisplayName("회원ID가 null이면 예외가 발생한다")
        @Test
        void failsWhenMemberIdIsNull() {
            assertThatThrownBy(() -> new Like(null, 1L))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST);
        }

        @DisplayName("상품ID가 null이면 예외가 발생한다")
        @Test
        void failsWhenProductIdIsNull() {
            assertThatThrownBy(() -> new Like(1L, null))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST);
        }
    }
}
