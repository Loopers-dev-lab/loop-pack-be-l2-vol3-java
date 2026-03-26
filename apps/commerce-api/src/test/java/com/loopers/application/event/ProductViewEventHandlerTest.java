package com.loopers.application.event;

import com.loopers.domain.product.event.ProductViewedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import static org.assertj.core.api.Assertions.assertThatCode;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductViewEventHandler 단위 테스트")
class ProductViewEventHandlerTest {

    @Mock
    KafkaTemplate<Object, Object> kafkaTemplate;

    @InjectMocks
    ProductViewEventHandler handler;

    @Test
    @DisplayName("상품 조회 이벤트 처리 시 예외가 발생하지 않는다")
    void handleProductViewed_ShouldNotThrow() {
        ProductViewedEvent event = new ProductViewedEvent(1L, 100L);

        assertThatCode(() -> handler.handleProductViewed(event))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("userId가 null이어도 예외가 발생하지 않는다")
    void handleProductViewed_WithNullUserId_ShouldNotThrow() {
        ProductViewedEvent event = new ProductViewedEvent(1L, null);

        assertThatCode(() -> handler.handleProductViewed(event))
                .doesNotThrowAnyException();
    }
}
