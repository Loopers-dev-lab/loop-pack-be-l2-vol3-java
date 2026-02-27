package com.loopers.domain.order;

import com.loopers.domain.order.model.OrderProduct;
import com.loopers.domain.order.repository.OrderProductRepository;
import com.loopers.domain.order.service.OrderProductService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderProductServiceTest {

    @InjectMocks
    private OrderProductService orderProductService;

    @Mock
    private OrderProductRepository orderProductRepository;

    @DisplayName("주문 상품 저장")
    @Nested
    class SaveAll {

        @DisplayName("정상적으로 주문 상품 목록을 저장하고 반환한다")
        @Test
        void savesAll_andReturnsResult() {
            // arrange
            Long orderId = 1L;
            OrderProduct orderProduct1 = OrderProduct.create(1L, "운동화", 50000, 2);
            OrderProduct orderProduct2 = OrderProduct.create(2L, "슬리퍼", 20000, 1);
            List<OrderProduct> orderProducts = List.of(orderProduct1, orderProduct2);
            OrderProduct saved1 = OrderProduct.reconstruct(1L, 1L, "운동화", 50000, 2);
            OrderProduct saved2 = OrderProduct.reconstruct(2L, 2L, "슬리퍼", 20000, 1);
            List<OrderProduct> savedList = List.of(saved1, saved2);
            when(orderProductRepository.saveAll(orderId, orderProducts)).thenReturn(savedList);

            // act
            List<OrderProduct> result = orderProductService.saveAll(orderId, orderProducts);

            // assert
            verify(orderProductRepository).saveAll(orderId, orderProducts);
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getId()).isEqualTo(1L);
            assertThat(result.get(1).getId()).isEqualTo(2L);
        }
    }

    @DisplayName("주문 상품 조회")
    @Nested
    class FindByOrderId {

        @DisplayName("정상적으로 주문 ID에 해당하는 주문 상품 목록을 반환한다")
        @Test
        void returnsOrderProducts_forGivenOrderId() {
            // arrange
            Long orderId = 1L;
            OrderProduct orderProduct1 = OrderProduct.reconstruct(1L, 1L, "운동화", 50000, 2);
            OrderProduct orderProduct2 = OrderProduct.reconstruct(2L, 2L, "슬리퍼", 20000, 1);
            when(orderProductRepository.findByOrderId(orderId))
                    .thenReturn(List.of(orderProduct1, orderProduct2));

            // act
            List<OrderProduct> result = orderProductService.findByOrderId(orderId);

            // assert
            verify(orderProductRepository).findByOrderId(orderId);
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getProductId()).isEqualTo(1L);
            assertThat(result.get(1).getProductId()).isEqualTo(2L);
        }
    }
}
