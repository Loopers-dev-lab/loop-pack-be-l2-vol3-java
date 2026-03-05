package com.loopers.domain.order;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderHistoryService 단위 테스트")
class OrderHistoryServiceTest {

    @Mock
    private OrderHistoryRepository orderHistoryRepository;

    @InjectMocks
    private OrderHistoryService orderHistoryService;

    @Nested
    @DisplayName("이력 기록")
    class RecordHistory {

        @Test
        @DisplayName("성공: 주문 이력을 기록한다")
        void recordHistory_Success() {
            // Given
            Long orderId = 1L;
            OrderStatus previousStatus = null;
            OrderStatus newStatus = OrderStatus.CREATED;
            String description = "주문 생성";

            given(orderHistoryRepository.save(any(OrderHistory.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            // When
            OrderHistory result = orderHistoryService.recordHistory(orderId, previousStatus, newStatus, description);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getOrderId()).isEqualTo(orderId);
            assertThat(result.getPreviousStatus()).isNull();
            assertThat(result.getNewStatus()).isEqualTo(OrderStatus.CREATED);
            assertThat(result.getDescription()).isEqualTo("주문 생성");

            then(orderHistoryRepository).should().save(any(OrderHistory.class));
        }
    }

    @Nested
    @DisplayName("이력 조회")
    class GetHistoriesByOrderId {

        @Test
        @DisplayName("성공: 주문 이력 목록을 조회한다")
        void getHistoriesByOrderId_Success() {
            // Given
            Long orderId = 1L;
            OrderHistory history = OrderHistory.create(orderId, null, OrderStatus.CREATED, "주문 생성");
            given(orderHistoryRepository.findAllByOrderId(orderId)).willReturn(List.of(history));

            // When
            List<OrderHistory> result = orderHistoryService.getHistoriesByOrderId(orderId);

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getOrderId()).isEqualTo(orderId);
            assertThat(result.get(0).getNewStatus()).isEqualTo(OrderStatus.CREATED);

            then(orderHistoryRepository).should().findAllByOrderId(orderId);
        }

        @Test
        @DisplayName("성공: 이력이 없으면 빈 목록을 반환한다")
        void getHistoriesByOrderId_Empty() {
            // Given
            Long orderId = 999L;
            given(orderHistoryRepository.findAllByOrderId(orderId)).willReturn(List.of());

            // When
            List<OrderHistory> result = orderHistoryService.getHistoriesByOrderId(orderId);

            // Then
            assertThat(result).isEmpty();
        }
    }
}
