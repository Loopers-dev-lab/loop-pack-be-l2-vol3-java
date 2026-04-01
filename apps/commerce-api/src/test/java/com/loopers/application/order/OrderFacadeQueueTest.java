package com.loopers.application.order;

import com.loopers.application.coupon.CouponService;
import com.loopers.application.coupon.IssuedCouponService;
import com.loopers.application.product.ProductInfo;
import com.loopers.application.product.ProductService;
import com.loopers.application.queue.EntryTokenService;
import com.loopers.application.queue.OrderQueueReader;
import com.loopers.domain.order.Order;
import com.loopers.domain.product.Product;
import com.loopers.domain.queue.InMemoryEntryTokenRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class OrderFacadeQueueTest {

    private OrderService orderService;
    private ProductService productService;
    private IssuedCouponService issuedCouponService;
    private CouponService couponService;
    private EntryTokenService entryTokenService;
    private OrderQueueReader orderQueueReader;
    private InMemoryEntryTokenRepository entryTokenRepository;
    private OrderFacade orderFacade;

    @BeforeEach
    void setUp() {
        orderService = mock(OrderService.class);
        productService = mock(ProductService.class);
        issuedCouponService = mock(IssuedCouponService.class);
        couponService = mock(CouponService.class);
        entryTokenRepository = new InMemoryEntryTokenRepository();
        entryTokenService = new EntryTokenService(entryTokenRepository);
        orderQueueReader = mock(OrderQueueReader.class);
        orderFacade = new OrderFacade(
                orderService, productService, issuedCouponService, couponService,
                entryTokenService, orderQueueReader
        );
    }

    private OrderCreateCommand createCommand(Long userId) {
        return new OrderCreateCommand(userId, List.of(new OrderItemCommand(1L, 1)), null);
    }

    private void stubOrderSuccess() {
        given(productService.getActiveProductsByIdsOrThrow(any()))
                .willReturn(List.of(new ProductInfo(
                        1L, new ProductInfo.BrandSummary(1L, "브랜드"),
                        "상품", "설명", 10000, 100, 0,
                        Product.Visibility.VISIBLE,
                        null, null, null
                )));
        given(orderService.placeOrder(anyLong(), any(), anyLong(), nullable(Long.class)))
                .willReturn(new OrderInfo(1L, 1L, Order.Status.ORDERED, 10000L, 0L, 10000L, null, null));
    }

    @DisplayName("대기열 활성화 상태에서, ")
    @Nested
    class QueueEnabled {

        @BeforeEach
        void setUp() {
            given(orderQueueReader.isEnabled()).willReturn(true);
            stubOrderSuccess();
        }

        @DisplayName("유효한 토큰이 있으면 주문이 성공하고 토큰이 소비된다.")
        @Test
        void createsOrder_whenValidToken() {
            // arrange
            entryTokenRepository.issueToken(1L, "valid-token", Duration.ofMinutes(5));

            // act
            orderFacade.createOrder(createCommand(1L), "valid-token");

            // assert
            verify(orderService).placeOrder(anyLong(), any(), anyLong(), nullable(Long.class));
            assertThat(entryTokenRepository.getToken(1L)).isEmpty();
        }

        @DisplayName("토큰이 null이면 예외가 발생한다.")
        @Test
        void throwsException_whenTokenIsNull() {
            // act & assert
            assertThatThrownBy(() -> orderFacade.createOrder(createCommand(1L), null))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(ErrorType.ENTRY_TOKEN_REQUIRED);
        }

        @DisplayName("토큰이 유효하지 않으면 예외가 발생한다.")
        @Test
        void throwsException_whenTokenInvalid() {
            // arrange
            entryTokenRepository.issueToken(1L, "valid-token", Duration.ofMinutes(5));

            // act & assert
            assertThatThrownBy(() -> orderFacade.createOrder(createCommand(1L), "wrong-token"))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(ErrorType.ENTRY_TOKEN_INVALID);

            verify(orderService, never()).placeOrder(anyLong(), any(), anyLong(), nullable(Long.class));
        }
    }

    @DisplayName("대기열 비활성화 상태에서, ")
    @Nested
    class QueueDisabled {

        @BeforeEach
        void setUp() {
            given(orderQueueReader.isEnabled()).willReturn(false);
            stubOrderSuccess();
        }

        @DisplayName("토큰 없이도 주문이 성공한다.")
        @Test
        void createsOrder_withoutToken() {
            // act & assert: 예외 없이 성공
            orderFacade.createOrder(createCommand(1L), null);

            verify(orderService).placeOrder(anyLong(), any(), anyLong(), nullable(Long.class));
        }

        @DisplayName("토큰이 있으면 예외가 발생한다.")
        @Test
        void throwsException_whenTokenProvided() {
            assertThatThrownBy(() -> orderFacade.createOrder(createCommand(1L), "unexpected-token"))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(ErrorType.ENTRY_TOKEN_NOT_ACCEPTED);

            verify(orderService, never()).placeOrder(anyLong(), any(), anyLong(), nullable(Long.class));
        }
    }
}
