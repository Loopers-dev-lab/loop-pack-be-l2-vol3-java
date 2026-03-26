package com.loopers.application.product;

import com.loopers.infrastructure.product.LikeEventHandledRepository;
import com.loopers.infrastructure.product.ProductLikeCountRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductLikeCountConsumerServiceTest {

    @Test
    @DisplayName("같은 eventId가 처음이면 상품 like_count를 반영한다")
    void consume_newEvent_updatesLikeCount() {
        LikeEventHandledRepository handledRepository = mock(LikeEventHandledRepository.class);
        ProductLikeCountRepository productLikeCountRepository = mock(ProductLikeCountRepository.class);
        ProductLikeCountConsumerService service = new ProductLikeCountConsumerService(handledRepository, productLikeCountRepository);

        LikeCountChangedMessage message = new LikeCountChangedMessage(
                UUID.randomUUID(),
                "LIKE_REGISTER",
                "member-1",
                UUID.randomUUID(),
                1,
                Instant.now()
        );

        when(handledRepository.markHandledIfAbsent("like-consumer", message.eventId())).thenReturn(true);

        service.consume("like-consumer", message);

        verify(productLikeCountRepository, times(1)).updateLikeCount(message.productId(), 1);
    }

    @Test
    @DisplayName("같은 eventId가 이미 처리됐으면 상품 like_count를 건너뛴다")
    void consume_duplicateEvent_skipsLikeCountUpdate() {
        LikeEventHandledRepository handledRepository = mock(LikeEventHandledRepository.class);
        ProductLikeCountRepository productLikeCountRepository = mock(ProductLikeCountRepository.class);
        ProductLikeCountConsumerService service = new ProductLikeCountConsumerService(handledRepository, productLikeCountRepository);

        LikeCountChangedMessage message = new LikeCountChangedMessage(
                UUID.randomUUID(),
                "LIKE_CANCEL",
                "member-1",
                UUID.randomUUID(),
                -1,
                Instant.now()
        );

        when(handledRepository.markHandledIfAbsent("like-consumer", message.eventId())).thenReturn(false);

        service.consume("like-consumer", message);

        verify(productLikeCountRepository, never()).updateLikeCount(message.productId(), -1);
    }
}
