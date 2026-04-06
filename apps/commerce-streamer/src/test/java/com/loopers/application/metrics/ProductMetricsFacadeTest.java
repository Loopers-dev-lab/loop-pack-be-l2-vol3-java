package com.loopers.application.metrics;

import com.loopers.domain.metrics.ProductMetricsRepository;
import com.loopers.infrastructure.eventhandled.EventHandledJpaRepository;
import com.loopers.interfaces.consumer.payload.CatalogEventPayload;
import com.loopers.interfaces.consumer.payload.OrderCreatedEventPayload;
import com.loopers.interfaces.consumer.payload.ProductViewEventPayload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductMetricsFacadeTest {

    ProductMetricsRepository productMetricsRepository = mock(ProductMetricsRepository.class);
    EventHandledJpaRepository eventHandledJpaRepository = mock(EventHandledJpaRepository.class);

    ProductMetricsFacade facade = new ProductMetricsFacade(productMetricsRepository, eventHandledJpaRepository);

    @DisplayName("applyLike() 를 호출할 때, ")
    @Nested
    class ApplyLike {

        @DisplayName("처음 수신된 이벤트면, likeCount 가 반영되고 event_handled 에 저장된다.")
        @Test
        void appliesLike_whenEventNotHandled() {
            // arrange
            CatalogEventPayload payload = new CatalogEventPayload("uuid-1", "LIKE_CREATED", 1L, 42L, 1);
            when(eventHandledJpaRepository.existsById("uuid-1")).thenReturn(false);

            // act
            facade.applyLike(payload);

            // assert
            verify(productMetricsRepository).upsertLike(42L, 1);
            verify(eventHandledJpaRepository).save(any());
        }

        @DisplayName("이미 처리된 이벤트면, likeCount 가 반영되지 않는다.")
        @Test
        void doesNotApplyLike_whenEventAlreadyHandled() {
            // arrange
            CatalogEventPayload payload = new CatalogEventPayload("uuid-1", "LIKE_CREATED", 1L, 42L, 1);
            when(eventHandledJpaRepository.existsById("uuid-1")).thenReturn(true);

            // act
            facade.applyLike(payload);

            // assert
            verify(productMetricsRepository, never()).upsertLike(anyLong(), anyInt());
        }
    }

    @DisplayName("applyOrder() 를 호출할 때, ")
    @Nested
    class ApplyOrder {

        @DisplayName("처음 수신된 이벤트면, 각 상품의 orderCount 가 반영된다.")
        @Test
        void appliesOrder_whenEventNotHandled() {
            // arrange
            List<OrderCreatedEventPayload.Item> items = List.of(
                    new OrderCreatedEventPayload.Item(42L, 2),
                    new OrderCreatedEventPayload.Item(99L, 1)
            );
            OrderCreatedEventPayload payload = new OrderCreatedEventPayload("uuid-2", "ORDER_CREATED", 1L, "ORDER-001", 90000L, items);
            when(eventHandledJpaRepository.existsById("uuid-2")).thenReturn(false);

            // act
            facade.applyOrder(payload);

            // assert
            verify(productMetricsRepository).upsertOrder(42L, 2);
            verify(productMetricsRepository).upsertOrder(99L, 1);
            verify(eventHandledJpaRepository).save(any());
        }

        @DisplayName("이미 처리된 이벤트면, orderCount 가 반영되지 않는다.")
        @Test
        void doesNotApplyOrder_whenEventAlreadyHandled() {
            // arrange
            OrderCreatedEventPayload payload = new OrderCreatedEventPayload("uuid-2", "ORDER_CREATED", 1L, "ORDER-001", 90000L, List.of());
            when(eventHandledJpaRepository.existsById("uuid-2")).thenReturn(true);

            // act
            facade.applyOrder(payload);

            // assert
            verify(productMetricsRepository, never()).upsertOrder(anyLong(), anyLong());
        }
    }

    @DisplayName("applyView() 를 호출할 때, ")
    @Nested
    class ApplyView {

        @DisplayName("viewCount 가 반영된다.")
        @Test
        void appliesView() {
            // arrange
            ProductViewEventPayload payload = new ProductViewEventPayload(42L);

            // act
            facade.applyView(payload);

            // assert
            verify(productMetricsRepository).upsertView(42L);
        }
    }
}
