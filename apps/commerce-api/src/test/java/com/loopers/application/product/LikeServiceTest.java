package com.loopers.application.product;

import com.loopers.domain.product.Product;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Like Domain Tests")
class LikeServiceTest {

    private static final Product ACTIVE_PRODUCT = new Product(
            1L,
            "간식",
            12_000,
            10,
            "닭가슴살 간식",
            1L,
            1L,
            3,
            null
    );

    @Nested
    @DisplayName("좋아요 카운트")
    class LikeCount {

        @Test
        @DisplayName("좋아요 등록이면 likeCount가 1 증가한다")
        void increaseLikeCount_whenRegisterLike_thenLikeCountIncreasesByOne() {
            Product result = ACTIVE_PRODUCT.increaseLikeCount();
            assertThat(result.likeCount()).isEqualTo(ACTIVE_PRODUCT.likeCount() + 1);
        }

        @Test
        @DisplayName("좋아요가 중복 등록되어도 증가 로직은 호출 횟수만큼 반영된다")
        void increaseLikeCount_whenRegisteredTwice_thenLikeCountIncreasesTwice() {
            Product once = ACTIVE_PRODUCT.increaseLikeCount();
            Product twice = once.increaseLikeCount();

            assertThat(twice.likeCount()).isEqualTo(ACTIVE_PRODUCT.likeCount() + 2);
        }

        @Test
        @DisplayName("좋아요 취소하면 likeCount가 1 감소한다")
        void decreaseLikeCount_whenCancelLike_thenLikeCountDecreasesByOne() {
            Product result = ACTIVE_PRODUCT.decreaseLikeCount();

            assertThat(result.likeCount()).isEqualTo(ACTIVE_PRODUCT.likeCount() - 1);
        }

        @Test
        @DisplayName("이미 0인 likeCount는 취소 시 음수로 내려가지 않는다")
        void decreaseLikeCount_whenLikeCountZero_thenKeepsZero() {
            Product zeroLiked = new Product(
                    2L,
                    "우산",
                    20_000,
                    5,
                    "산책 우산",
                    1L,
                    1L,
                    0,
                    null
            );

            Product result = zeroLiked.decreaseLikeCount();
            assertThat(result.likeCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("삭제 상품이면 삭제 상태를 판단할 수 있다")
        void isDeleted_whenDeletedAtExists_thenReturnsTrue() {
            Product deletedProduct = new Product(
                    3L,
                    "하네스",
                    15_000,
                    3,
                    "산책 하네스",
                    1L,
                    1L,
                    5,
                    ZonedDateTime.now()
            );

            assertThat(deletedProduct.isDeleted()).isTrue();
        }
    }
}
