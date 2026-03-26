package com.loopers.application.event;

import com.loopers.domain.like.event.ProductLikedEvent;
import com.loopers.domain.like.event.ProductUnlikedEvent;
import com.loopers.domain.product.ProductService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("LikeEventHandler 단위 테스트")
class LikeEventHandlerTest {

    @Mock
    ProductService productService;

    @Mock
    KafkaTemplate<Object, Object> kafkaTemplate;

    @InjectMocks
    LikeEventHandler likeEventHandler;

    @Test
    @DisplayName("좋아요 이벤트 시 incrementLikeCount가 호출된다")
    void handleProductLiked_ShouldIncrementLikeCount() {
        ProductLikedEvent event = new ProductLikedEvent(1L, 100L);

        likeEventHandler.handleProductLiked(event);

        verify(productService).incrementLikeCount(100L);
    }

    @Test
    @DisplayName("좋아요 이벤트 처리 실패 시 예외가 전파되지 않는다")
    void handleProductLiked_Failure_ShouldNotThrow() {
        ProductLikedEvent event = new ProductLikedEvent(1L, 100L);
        doThrow(new RuntimeException("DB error")).when(productService).incrementLikeCount(100L);

        assertThatCode(() -> likeEventHandler.handleProductLiked(event))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("좋아요 취소 이벤트 시 decrementLikeCount가 호출된다")
    void handleProductUnliked_ShouldDecrementLikeCount() {
        ProductUnlikedEvent event = new ProductUnlikedEvent(1L, 100L);

        likeEventHandler.handleProductUnliked(event);

        verify(productService).decrementLikeCount(100L);
    }

    @Test
    @DisplayName("좋아요 취소 이벤트 처리 실패 시 예외가 전파되지 않는다")
    void handleProductUnliked_Failure_ShouldNotThrow() {
        ProductUnlikedEvent event = new ProductUnlikedEvent(1L, 100L);
        doThrow(new RuntimeException("DB error")).when(productService).decrementLikeCount(100L);

        assertThatCode(() -> likeEventHandler.handleProductUnliked(event))
                .doesNotThrowAnyException();
    }
}
