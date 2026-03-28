package com.loopers.application.like;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.like.event.ProductLikedEvent;
import com.loopers.domain.like.event.ProductUnlikedEvent;
import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxEventRepository;
import com.loopers.domain.product.ProductService;
import com.loopers.infrastructure.product.ProductCacheService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductLikeEventHandlerUnitTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private ProductService productService;

    @Mock
    private ProductCacheService productCacheService;

    @InjectMocks
    private ProductLikeEventHandler handler;

    @DisplayName("BEFORE_COMMIT 핸들러 (Outbox 저장)")
    @Nested
    class BeforeCommitHandler {

        @DisplayName("ProductLikedEvent 수신 시 Outbox에 PRODUCT_LIKED 이벤트가 저장된다")
        @Test
        void saveToOutboxOnLikeSuccess() throws Exception {
            // given
            ProductLikedEvent event = ProductLikedEvent.from(1L, 10L);
            when(objectMapper.writeValueAsString(event))
                    .thenReturn("{\"memberId\":1,\"productId\":10}");

            // when
            handler.saveToOutboxOnLike(event);

            // then
            ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
            verify(outboxEventRepository).save(captor.capture());

            OutboxEvent saved = captor.getValue();
            assertThat(saved.getAggregateType()).isEqualTo("PRODUCT");
            assertThat(saved.getAggregateId()).isEqualTo(10L);
            assertThat(saved.getEventType()).isEqualTo("PRODUCT_LIKED");
            assertThat(saved.isPublished()).isFalse();
        }

        @DisplayName("ProductUnlikedEvent 수신 시 Outbox에 PRODUCT_UNLIKED 이벤트가 저장된다")
        @Test
        void saveToOutboxOnUnlikeSuccess() throws Exception {
            // given
            ProductUnlikedEvent event = ProductUnlikedEvent.from(1L, 10L);
            when(objectMapper.writeValueAsString(event))
                    .thenReturn("{\"memberId\":1,\"productId\":10}");

            // when
            handler.saveToOutboxOnUnlike(event);

            // then
            ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
            verify(outboxEventRepository).save(captor.capture());

            OutboxEvent saved = captor.getValue();
            assertThat(saved.getAggregateType()).isEqualTo("PRODUCT");
            assertThat(saved.getAggregateId()).isEqualTo(10L);
            assertThat(saved.getEventType()).isEqualTo("PRODUCT_UNLIKED");
            assertThat(saved.isPublished()).isFalse();
        }
    }

    @DisplayName("AFTER_COMMIT 핸들러 (비동기 처리)")
    @Nested
    class AfterCommitHandler {

        @DisplayName("좋아요 이벤트 수신 시 likeCount 증가 + 캐시 무효화")
        @Test
        void handleProductLikedIncreasesLikeCount() {
            // given
            ProductLikedEvent event = ProductLikedEvent.from(1L, 10L);

            // when
            handler.handleProductLiked(event);

            // then
            verify(productService).increaseLikeCount(10L);
            verify(productCacheService).evictProductDetail(10L);
            verify(productCacheService).evictProductList();
        }

        @DisplayName("좋아요 취소 이벤트 수신 시 likeCount 감소 + 캐시 무효화")
        @Test
        void handleProductUnlikedDecreasesLikeCount() {
            // given
            ProductUnlikedEvent event = ProductUnlikedEvent.from(1L, 10L);

            // when
            handler.handleProductUnliked(event);

            // then
            verify(productService).decreaseLikeCount(10L);
            verify(productCacheService).evictProductDetail(10L);
            verify(productCacheService).evictProductList();
        }
    }
}
