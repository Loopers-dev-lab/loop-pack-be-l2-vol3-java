package com.loopers.application.product;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.loopers.domain.product.ProductViewLogRepository;
import com.loopers.domain.product.ProductViewLogModel;
import com.loopers.domain.product.event.ProductViewedEvent;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductViewCountHandlerTest {

    @Mock ProductViewLogRepository productViewLogRepository;
    ProductViewCountHandler handler;

    ProductViewedEvent event;

    @BeforeEach
    void setUp() {
        handler = new ProductViewCountHandler(productViewLogRepository);
        event = new ProductViewedEvent(1L, 100L, ZonedDateTime.now());
    }

    @DisplayName("상품 조회 이벤트를 수신할 때, ")
    @Nested
    class Handle {

        @DisplayName("조회 로그를 저장한다.")
        @Test
        void handle_savesViewLog() {
            // act
            handler.handle(event);

            // assert
            verify(productViewLogRepository).save(any(ProductViewLogModel.class));
        }

        @DisplayName("예외 발생 시 로그만 남기고 전파하지 않는다.")
        @Test
        void handle_whenExceptionOccurs_doesNotPropagate() {
            // arrange
            doThrow(new RuntimeException("DB 장애"))
                    .when(productViewLogRepository).save(any(ProductViewLogModel.class));

            // act & assert
            assertThatCode(() -> handler.handle(event)).doesNotThrowAnyException();
        }
    }
}
