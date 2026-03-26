package com.loopers.application.productlike;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.event.ProductLikedEvent;
import com.loopers.domain.event.ProductUnlikedEvent;
import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxEventService;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.productlike.ProductLike;
import com.loopers.domain.productlike.ProductLikeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductLikeFacade 단위 테스트")
class ProductLikeFacadeTest {

    @Mock
    private ProductLikeService productLikeService;

    @Mock
    private ProductService productService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private OutboxEventService outboxEventService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private ProductLikeFacade productLikeFacade;

    @Nested
    @DisplayName("registerLike - 좋아요 등록")
    class RegisterLike {

        @Test
        @DisplayName("성공: 좋아요 등록 시 OutboxEvent를 저장한다")
        void registerLike_savesOutboxEvent() {
            // Given
            Long userId = 1L;
            Long productId = 100L;
            ProductLike productLike = ProductLike.create(userId, productId);

            given(productLikeService.registerLike(userId, productId)).willReturn(productLike);
            given(outboxEventService.save(any(OutboxEvent.class))).willAnswer(invocation -> invocation.getArgument(0));

            // When
            productLikeFacade.registerLike(userId, productId);

            // Then
            ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
            then(outboxEventService).should().save(captor.capture());

            OutboxEvent savedEvent = captor.getValue();
            assertThat(savedEvent.getEventType()).isEqualTo("PRODUCT_LIKED");
            assertThat(savedEvent.getAggregateId()).isEqualTo(String.valueOf(productId));

            then(eventPublisher).should().publishEvent(any(ProductLikedEvent.class));
        }
    }

    @Nested
    @DisplayName("cancelLike - 좋아요 취소")
    class CancelLike {

        @Test
        @DisplayName("성공: 좋아요 취소 시 OutboxEvent를 저장한다")
        void cancelLike_savesOutboxEvent() {
            // Given
            Long userId = 1L;
            Long productId = 100L;

            given(outboxEventService.save(any(OutboxEvent.class))).willAnswer(invocation -> invocation.getArgument(0));

            // When
            productLikeFacade.cancelLike(userId, productId);

            // Then
            ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
            then(outboxEventService).should().save(captor.capture());

            OutboxEvent savedEvent = captor.getValue();
            assertThat(savedEvent.getEventType()).isEqualTo("PRODUCT_UNLIKED");
            assertThat(savedEvent.getAggregateId()).isEqualTo(String.valueOf(productId));

            then(eventPublisher).should().publishEvent(any(ProductUnlikedEvent.class));
        }
    }
}
