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
class ViewHandlerTest {

    @InjectMocks
    private ViewHandler viewHandler;

    @Mock
    private ProductMetricsRepository productMetricsRepository;

    @DisplayName("supports를 호출할 때,")
    @Nested
    class Supports {

        @DisplayName("PRODUCT_VIEWED이면, true를 반환한다.")
        @Test
        void returnsTrue_whenProductViewed() {
            assertThat(viewHandler.supports(MetricsEventType.PRODUCT_VIEWED)).isTrue();
        }

        @DisplayName("다른 타입이면, false를 반환한다.")
        @Test
        void returnsFalse_whenOtherType() {
            assertThat(viewHandler.supports(MetricsEventType.LIKED)).isFalse();
            assertThat(viewHandler.supports(MetricsEventType.ORDER_COMPLETED)).isFalse();
        }
    }

    @DisplayName("handle을 호출할 때,")
    @Nested
    class Handle {

        @DisplayName("upsertViewCount를 호출한다.")
        @Test
        void upsertsViewCount() {
            // act
            viewHandler.handle(new MetricsPayload.View(1L));

            // assert
            then(productMetricsRepository).should().upsertViewCount(1L);
        }
    }
}
