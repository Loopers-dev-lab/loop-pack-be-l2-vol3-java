package com.loopers.application.metrics;

import com.loopers.domain.metrics.ProductMetrics;
import com.loopers.domain.metrics.ProductMetricsRepository;
import com.loopers.infrastructure.eventhandled.EventHandledJpaRepository;
import com.loopers.interfaces.consumer.payload.CatalogEventPayload;
import com.loopers.interfaces.consumer.payload.OrderCreatedEventPayload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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
            ProductMetrics metrics = ProductMetrics.of(42L);
            when(eventHandledJpaRepository.existsById("uuid-1")).thenReturn(false);
            when(productMetricsRepository.findByProductId(42L)).thenReturn(Optional.of(metrics));
            when(productMetricsRepository.save(any())).thenReturn(metrics);

            // act
            facade.applyLike(payload);

            // assert
            assertThat(metrics.likeCount()).isEqualTo(1L);
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
            verify(productMetricsRepository, never()).save(any());
        }

        @DisplayName("product_metrics 가 없으면 새로 생성해서 반영된다.")
        @Test
        void createsMetrics_whenNotExists() {
            // arrange
            CatalogEventPayload payload = new CatalogEventPayload("uuid-1", "LIKE_CREATED", 1L, 42L, 1);
            when(eventHandledJpaRepository.existsById("uuid-1")).thenReturn(false);
            when(productMetricsRepository.findByProductId(42L)).thenReturn(Optional.empty());
            when(productMetricsRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            // act
            facade.applyLike(payload);

            // assert
            verify(productMetricsRepository).save(any());
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
            ProductMetrics metrics42 = ProductMetrics.of(42L);
            ProductMetrics metrics99 = ProductMetrics.of(99L);
            when(eventHandledJpaRepository.existsById("uuid-2")).thenReturn(false);
            when(productMetricsRepository.findByProductId(42L)).thenReturn(Optional.of(metrics42));
            when(productMetricsRepository.findByProductId(99L)).thenReturn(Optional.of(metrics99));
            when(productMetricsRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            // act
            facade.applyOrder(payload);

            // assert
            assertThat(metrics42.orderCount()).isEqualTo(2L);
            assertThat(metrics99.orderCount()).isEqualTo(1L);
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
            verify(productMetricsRepository, never()).save(any());
        }
    }
}
