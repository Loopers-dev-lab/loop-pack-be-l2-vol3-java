package com.loopers.application.metrics;

import com.loopers.domain.metrics.ProductMetricsRepository;
import com.loopers.infrastructure.eventhandled.EventHandledJpaRepository;
import com.loopers.interfaces.consumer.payload.CatalogEventPayload;
import com.loopers.interfaces.consumer.payload.OrderCreatedEventPayload;
import com.loopers.interfaces.consumer.payload.ProductViewEventPayload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
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
            ZonedDateTime occurredAt = ZonedDateTime.of(2026, 4, 8, 10, 0, 0, 0, ZoneOffset.UTC);
            CatalogEventPayload payload = new CatalogEventPayload("uuid-1", "LIKE_CREATED", 1L, 42L, 1, occurredAt);
            when(eventHandledJpaRepository.existsById("uuid-1")).thenReturn(false);

            // act
            facade.applyLike(payload);

            // assert
            LocalDateTime expectedMetricHour = LocalDateTime.of(2026, 4, 8, 10, 0, 0);
            verify(productMetricsRepository).upsertLike(42L, 1, expectedMetricHour);
            verify(eventHandledJpaRepository).save(any());
        }

        @DisplayName("이미 처리된 이벤트면, likeCount 가 반영되지 않는다.")
        @Test
        void doesNotApplyLike_whenEventAlreadyHandled() {
            // arrange
            ZonedDateTime occurredAt = ZonedDateTime.of(2026, 4, 8, 10, 0, 0, 0, ZoneOffset.UTC);
            CatalogEventPayload payload = new CatalogEventPayload("uuid-1", "LIKE_CREATED", 1L, 42L, 1, occurredAt);
            when(eventHandledJpaRepository.existsById("uuid-1")).thenReturn(true);

            // act
            facade.applyLike(payload);

            // assert
            verify(productMetricsRepository, never()).upsertLike(anyLong(), anyInt(), any(LocalDateTime.class));
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
            ZonedDateTime occurredAt = ZonedDateTime.of(2026, 4, 8, 10, 0, 0, 0, ZoneOffset.UTC);
            OrderCreatedEventPayload payload = new OrderCreatedEventPayload("uuid-2", "ORDER_CREATED", 1L, "ORDER-001", 90000L, items, occurredAt);
            when(eventHandledJpaRepository.existsById("uuid-2")).thenReturn(false);

            // act
            facade.applyOrder(payload);

            // assert
            LocalDateTime expectedMetricHour = LocalDateTime.of(2026, 4, 8, 10, 0, 0);
            verify(productMetricsRepository).upsertOrder(42L, 2, expectedMetricHour);
            verify(productMetricsRepository).upsertOrder(99L, 1, expectedMetricHour);
            verify(eventHandledJpaRepository).save(any());
        }

        @DisplayName("이미 처리된 이벤트면, orderCount 가 반영되지 않는다.")
        @Test
        void doesNotApplyOrder_whenEventAlreadyHandled() {
            // arrange
            ZonedDateTime occurredAt = ZonedDateTime.of(2026, 4, 8, 10, 0, 0, 0, ZoneOffset.UTC);
            OrderCreatedEventPayload payload = new OrderCreatedEventPayload("uuid-2", "ORDER_CREATED", 1L, "ORDER-001", 90000L, List.of(), occurredAt);
            when(eventHandledJpaRepository.existsById("uuid-2")).thenReturn(true);

            // act
            facade.applyOrder(payload);

            // assert
            verify(productMetricsRepository, never()).upsertOrder(anyLong(), anyLong(), any(LocalDateTime.class));
        }
    }

    @DisplayName("applyView() 를 호출할 때, ")
    @Nested
    class ApplyView {

        @DisplayName("viewCount 가 반영된다.")
        @Test
        void appliesView() {
            // arrange
            ZonedDateTime occurredAt = ZonedDateTime.of(2026, 4, 8, 10, 0, 0, 0, ZoneOffset.UTC);
            ProductViewEventPayload payload = new ProductViewEventPayload(42L, occurredAt);

            // act
            facade.applyView(payload);

            // assert
            LocalDateTime expectedMetricHour = LocalDateTime.of(2026, 4, 8, 10, 0, 0);
            verify(productMetricsRepository).upsertView(42L, expectedMetricHour);
        }
    }
}
