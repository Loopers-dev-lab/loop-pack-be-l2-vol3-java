package com.loopers.domain.metrics.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.loopers.domain.metrics.MetricsEventType;
import com.loopers.domain.metrics.MetricsPayload;
import com.loopers.domain.metrics.ProductMetricsRepository;

@ExtendWith(MockitoExtension.class)
class LikeHandlerTest {

    @InjectMocks
    private LikeHandler likeHandler;

    @Mock
    private ProductMetricsRepository productMetricsRepository;

    @DisplayName("supports를 호출할 때,")
    @Nested
    class Supports {

        @DisplayName("LIKED이면, true를 반환한다.")
        @Test
        void returnsTrue_whenLiked() {
            assertThat(likeHandler.supports(MetricsEventType.LIKED)).isTrue();
        }

        @DisplayName("UNLIKED이면, true를 반환한다.")
        @Test
        void returnsTrue_whenUnliked() {
            assertThat(likeHandler.supports(MetricsEventType.UNLIKED)).isTrue();
        }

        @DisplayName("다른 타입이면, false를 반환한다.")
        @Test
        void returnsFalse_whenOtherType() {
            assertThat(likeHandler.supports(MetricsEventType.ORDER_COMPLETED)).isFalse();
            assertThat(likeHandler.supports(MetricsEventType.PRODUCT_VIEWED)).isFalse();
        }
    }

    @DisplayName("handle을 호출할 때,")
    @Nested
    class Handle {

        @DisplayName("liked=true이면, delta 1로 upsertLikeCount를 호출한다.")
        @Test
        void upsertsWithPositiveDelta_whenLiked() {
            // act
            likeHandler.handle(new MetricsPayload.Like(1L, true));

            // assert
            then(productMetricsRepository).should().upsertLikeCount(1L, 1L);
        }

        @DisplayName("liked=false이면, delta -1로 upsertLikeCount를 호출한다.")
        @Test
        void upsertsWithNegativeDelta_whenUnliked() {
            // act
            likeHandler.handle(new MetricsPayload.Like(1L, false));

            // assert
            then(productMetricsRepository).should().upsertLikeCount(1L, -1L);
        }
    }
}
