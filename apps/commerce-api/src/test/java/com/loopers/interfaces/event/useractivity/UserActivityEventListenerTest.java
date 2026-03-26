package com.loopers.interfaces.event.useractivity;

import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.Collections;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.loopers.domain.like.LikeEvent;
import com.loopers.domain.order.OrderEvent;
import com.loopers.domain.product.ProductEvent;

class UserActivityEventListenerTest {

    private final UserActivityEventListener listener = new UserActivityEventListener();

    @DisplayName("유저 행동을 로깅할 때,")
    @Nested
    class Handle {

        @DisplayName("상품 조회 이벤트를 정상 로깅한다.")
        @Test
        void logsProductViewed() {
            // arrange
            ProductEvent.ProductViewed event = new ProductEvent.ProductViewed(UUID.randomUUID(), 1L);

            // act & assert
            assertThatCode(() -> listener.handle(event)).doesNotThrowAnyException();
        }

        @DisplayName("좋아요 이벤트를 정상 로깅한다.")
        @Test
        void logsLiked() {
            // arrange
            LikeEvent.Liked event = new LikeEvent.Liked(UUID.randomUUID(), 1L);

            // act & assert
            assertThatCode(() -> listener.handle(event)).doesNotThrowAnyException();
        }

        @DisplayName("좋아요 취소 이벤트를 정상 로깅한다.")
        @Test
        void logsUnliked() {
            // arrange
            LikeEvent.Unliked event = new LikeEvent.Unliked(UUID.randomUUID(), 1L);

            // act & assert
            assertThatCode(() -> listener.handle(event)).doesNotThrowAnyException();
        }

        @DisplayName("주문 완료 이벤트를 정상 로깅한다.")
        @Test
        void logsOrderCompleted() {
            // arrange
            OrderEvent.OrderCompleted event = new OrderEvent.OrderCompleted(UUID.randomUUID(), 1L, Collections.emptyList());

            // act & assert
            assertThatCode(() -> listener.handle(event)).doesNotThrowAnyException();
        }
    }
}
