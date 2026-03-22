package com.loopers.interfaces.event.like;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.loopers.domain.like.LikeService;
import com.loopers.domain.product.Product;
import com.loopers.support.BaseIntegrationTest;

class LikeEventListenerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private LikeService likeService;

    private Long brandId;
    private Long productId;

    @BeforeEach
    void setUp() {
        brandId = initDefaultBrand();
        productId = createProduct(brandId);
    }

    @DisplayName("좋아요 이벤트 리스너가 동작할 때,")
    @Nested
    class LikeEventHandler {

        @DisplayName("좋아요 생성 후 상품의 likeCount가 1 증가한다.")
        @Test
        void incrementsLikeCount_whenLikeCreated() {
            // act
            likeService.like(1L, productId);

            // assert — 비동기 이벤트 처리 대기
            await().atMost(5, SECONDS).untilAsserted(() -> {
                Product product = productService.getActiveProduct(productId);
                assertThat(product.getLikeCount()).isEqualTo(1L);
            });
        }

        @DisplayName("좋아요 취소 후 상품의 likeCount가 1 감소한다.")
        @Test
        void decrementsLikeCount_whenLikeRemoved() {
            // arrange
            likeService.like(1L, productId);

            // act
            await().atMost(5, SECONDS).untilAsserted(() -> {
                Product product = productService.getActiveProduct(productId);
                assertThat(product.getLikeCount()).isEqualTo(1L);
            });
            likeService.unlike(1L, productId);

            // assert — 비동기 이벤트 처리 대기
            await().atMost(5, SECONDS).untilAsserted(() -> {
                Product product = productService.getActiveProduct(productId);
                assertThat(product.getLikeCount()).isZero();
            });
        }
    }
}
