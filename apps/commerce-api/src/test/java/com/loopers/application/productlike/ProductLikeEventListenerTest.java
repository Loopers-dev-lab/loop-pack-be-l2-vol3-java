package com.loopers.application.productlike;

import com.loopers.domain.event.ProductLikedEvent;
import com.loopers.domain.event.ProductUnlikedEvent;
import com.loopers.domain.product.ProductService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductLikeEventListener 단위 테스트")
class ProductLikeEventListenerTest {

    @Mock
    private ProductService productService;

    @InjectMocks
    private ProductLikeEventListener productLikeEventListener;

    @Nested
    @DisplayName("좋아요 등록 이벤트 처리")
    class HandleProductLiked {

        @Test
        @DisplayName("ProductLikedEvent 수신 시 likesCount를 증가시킨다")
        void shouldIncreaseLikesCount() {
            // given
            ProductLikedEvent event = new ProductLikedEvent(1L, 100L);

            // when
            productLikeEventListener.handleProductLiked(event);

            // then
            then(productService).should().increaseLikes(100L);
        }
    }

    @Nested
    @DisplayName("좋아요 취소 이벤트 처리")
    class HandleProductUnliked {

        @Test
        @DisplayName("ProductUnlikedEvent 수신 시 likesCount를 감소시킨다")
        void shouldDecreaseLikesCount() {
            // given
            ProductUnlikedEvent event = new ProductUnlikedEvent(1L, 100L);

            // when
            productLikeEventListener.handleProductUnliked(event);

            // then
            then(productService).should().decreaseLikes(100L);
        }
    }
}
