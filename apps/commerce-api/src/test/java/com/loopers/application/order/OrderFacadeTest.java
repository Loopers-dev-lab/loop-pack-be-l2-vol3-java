package com.loopers.application.order;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.cart.CartService;
import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.domain.order.OrderCartRestoreModel;
import com.loopers.domain.order.OrderItemCommand;
import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.StockService;
import com.loopers.support.enums.DiscountType;
import com.loopers.support.enums.OrderType;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderFacade 단위 테스트")
class OrderFacadeTest {

    private static final Long USER_ID = 1L;
    private static final Long USER_COUPON_ID = 100L;
    private static final Long COUPON_ID = 10L;

    @Mock OrderService orderService;
    @Mock ProductService productService;
    @Mock BrandService brandService;
    @Mock StockService stockService;
    @Mock CartService cartService;
    @Mock CouponService couponService;

    @InjectMocks
    OrderFacade orderFacade;

    private ProductModel createTestProduct() {
        return ProductModel.create("테스트상품", 1L, BigDecimal.valueOf(10000),
                "설명", null, null, null, null, null, null);
    }

    private ProductModel createTestProduct(Long productId) {
        return ProductModel.create("테스트상품-" + productId, 1L, BigDecimal.valueOf(10000),
                "설명", null, null, null, null, null, null);
    }

    private BrandModel createTestBrand() {
        return BrandModel.create("테스트브랜드", "설명", "서울");
    }

    private void setupOrderCreationMocks() {
        List<OrderItemCommand> merged = List.of(new OrderItemCommand(1L, 2));
        when(orderService.validateAndPrepare(eq(USER_ID), anyList())).thenReturn(merged);
        when(productService.findOrderableById(1L)).thenReturn(createTestProduct());
        when(brandService.findById(1L)).thenReturn(createTestBrand());

        OrderModel savedOrder = OrderModel.create(USER_ID, OrderType.DIRECT, BigDecimal.valueOf(20000));
        when(orderService.createOrder(eq(USER_ID), any(OrderType.class), any(BigDecimal.class), anyList()))
                .thenReturn(savedOrder);
        when(orderService.findOrderItems(any())).thenReturn(List.of());
    }

    // === 바로 주문 (DIRECT) ===

    @Nested
    @DisplayName("바로 주문 (DIRECT)")
    class DirectOrderTests {

        @Test
        @DisplayName("상품 검증 → 재고 hold → 주문 저장의 전체 플로우가 수행된다")
        void createDirectOrder_ShouldValidateProduct_ReserveStock_SaveOrder() {
            setupOrderCreationMocks();

            List<OrderItemCommand> items = List.of(new OrderItemCommand(1L, 2));
            OrderInfo result = orderFacade.createDirectOrder(USER_ID, items, null);

            assertThat(result).isNotNull();
            verify(productService).findOrderableById(1L);
            verify(stockService).hold(1L, 2);
            verify(orderService).createOrder(eq(USER_ID), eq(OrderType.DIRECT), any(BigDecimal.class), anyList());
        }

        @Test
        @DisplayName("주문 불가 상품으로 주문 시 예외가 발생하고 hold가 호출되지 않는다")
        void createDirectOrder_ProductNotOrderable_ShouldThrow() {
            List<OrderItemCommand> merged = List.of(new OrderItemCommand(1L, 2));
            when(orderService.validateAndPrepare(eq(USER_ID), anyList())).thenReturn(merged);
            when(productService.findOrderableById(1L))
                    .thenThrow(new CoreException(ErrorType.PRODUCT_NOT_ORDERABLE));

            assertThatThrownBy(() -> orderFacade.createDirectOrder(USER_ID,
                    List.of(new OrderItemCommand(1L, 2)), null))
                    .isInstanceOf(CoreException.class)
                    .satisfies(ex -> assertThat(((CoreException) ex).getErrorType())
                            .isEqualTo(ErrorType.PRODUCT_NOT_ORDERABLE));
            verify(stockService, never()).hold(anyLong(), anyInt());
        }

        @Test
        @DisplayName("재고 부족 시 STOCK_NOT_ENOUGH 예외가 발생한다")
        void createDirectOrder_InsufficientStock_ShouldThrow() {
            List<OrderItemCommand> merged = List.of(new OrderItemCommand(1L, 100));
            when(orderService.validateAndPrepare(eq(USER_ID), anyList())).thenReturn(merged);
            when(productService.findOrderableById(1L)).thenReturn(createTestProduct());
            when(brandService.findById(1L)).thenReturn(createTestBrand());
            doThrow(new CoreException(ErrorType.STOCK_NOT_ENOUGH))
                    .when(stockService).hold(1L, 100);

            assertThatThrownBy(() -> orderFacade.createDirectOrder(USER_ID,
                    List.of(new OrderItemCommand(1L, 100)), null))
                    .isInstanceOf(CoreException.class)
                    .satisfies(ex -> assertThat(((CoreException) ex).getErrorType())
                            .isEqualTo(ErrorType.STOCK_NOT_ENOUGH));
        }

        @Test
        @DisplayName("총액이 sum(unitPrice * quantity)와 일치한다")
        void createDirectOrder_ShouldCalculateTotalAmount() {
            List<OrderItemCommand> merged = List.of(new OrderItemCommand(1L, 3));
            when(orderService.validateAndPrepare(eq(USER_ID), anyList())).thenReturn(merged);
            when(productService.findOrderableById(1L)).thenReturn(createTestProduct());
            when(brandService.findById(1L)).thenReturn(createTestBrand());

            OrderModel savedOrder = OrderModel.create(USER_ID, OrderType.DIRECT, BigDecimal.valueOf(30000));
            when(orderService.createOrder(eq(USER_ID), eq(OrderType.DIRECT),
                    eq(BigDecimal.valueOf(30000)), anyList()))
                    .thenReturn(savedOrder);
            when(orderService.findOrderItems(any())).thenReturn(List.of());

            OrderInfo result = orderFacade.createDirectOrder(USER_ID,
                    List.of(new OrderItemCommand(1L, 3)), null);

            assertThat(result.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(30000));
        }

        @Test
        @DisplayName("유효한 쿠폰 적용 시 할인된 금액으로 주문이 생성된다")
        void createDirectOrder_WithValidCoupon_ShouldApplyDiscount() {
            List<OrderItemCommand> merged = List.of(new OrderItemCommand(1L, 1));
            when(orderService.validateAndPrepare(eq(USER_ID), anyList())).thenReturn(merged);
            when(productService.findOrderableById(1L)).thenReturn(createTestProduct()); // price: 10000
            when(brandService.findById(1L)).thenReturn(createTestBrand());

            UserCouponModel userCoupon = UserCouponModel.create(USER_ID, COUPON_ID);
            when(couponService.validateAndGetUserCoupon(USER_ID, USER_COUPON_ID)).thenReturn(userCoupon);

            CouponModel coupon = CouponModel.create("쿠폰", DiscountType.FIXED, BigDecimal.valueOf(2000),
                    null, LocalDateTime.now().plusDays(30));
            when(couponService.findByIdForAdmin(COUPON_ID)).thenReturn(coupon);

            OrderModel savedOrder = OrderModel.create(USER_ID, OrderType.DIRECT, BigDecimal.valueOf(8000));
            when(orderService.createOrder(eq(USER_ID), eq(OrderType.DIRECT),
                    eq(BigDecimal.valueOf(8000)), anyList()))
                    .thenReturn(savedOrder);
            when(orderService.findOrderItems(any())).thenReturn(List.of());

            OrderInfo result = orderFacade.createDirectOrder(USER_ID,
                    List.of(new OrderItemCommand(1L, 1)), USER_COUPON_ID);

            assertThat(result.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(8000));
            verify(couponService).markCouponAsUsed(userCoupon.getUserCouponId(), savedOrder.getOrderId());
        }

        @Test
        @DisplayName("쿠폰 검증 실패 시 재고 hold가 호출되지 않는다")
        void createDirectOrder_CouponValidationFails_ShouldNotHoldStocks() {
            List<OrderItemCommand> merged = List.of(new OrderItemCommand(1L, 2));
            when(orderService.validateAndPrepare(eq(USER_ID), anyList())).thenReturn(merged);
            when(couponService.validateAndGetUserCoupon(USER_ID, USER_COUPON_ID))
                    .thenThrow(new CoreException(ErrorType.COUPON_NOT_AVAILABLE));

            assertThatThrownBy(() -> orderFacade.createDirectOrder(USER_ID,
                    List.of(new OrderItemCommand(1L, 2)), USER_COUPON_ID))
                    .isInstanceOf(CoreException.class)
                    .satisfies(ex -> assertThat(((CoreException) ex).getErrorType())
                            .isEqualTo(ErrorType.COUPON_NOT_AVAILABLE));
            verify(stockService, never()).hold(anyLong(), anyInt());
        }

        @Test
        @DisplayName("쿠폰 없이 주문 시 할인 없이 전체 금액으로 주문된다")
        void createDirectOrder_NoCoupon_ShouldUseFullAmount() {
            List<OrderItemCommand> merged = List.of(new OrderItemCommand(1L, 2));
            when(orderService.validateAndPrepare(eq(USER_ID), anyList())).thenReturn(merged);
            when(productService.findOrderableById(1L)).thenReturn(createTestProduct());
            when(brandService.findById(1L)).thenReturn(createTestBrand());

            OrderModel savedOrder = OrderModel.create(USER_ID, OrderType.DIRECT, BigDecimal.valueOf(20000));
            when(orderService.createOrder(eq(USER_ID), eq(OrderType.DIRECT),
                    eq(BigDecimal.valueOf(20000)), anyList()))
                    .thenReturn(savedOrder);
            when(orderService.findOrderItems(any())).thenReturn(List.of());

            OrderInfo result = orderFacade.createDirectOrder(USER_ID,
                    List.of(new OrderItemCommand(1L, 2)), null);

            assertThat(result.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(20000));
            verify(couponService, never()).validateAndGetUserCoupon(anyLong(), anyLong());
            verify(couponService, never()).markCouponAsUsed(anyLong(), anyLong());
        }
    }

    // === 장바구니 주문 (CART) ===

    @Nested
    @DisplayName("장바구니 주문 (CART)")
    class CartOrderTests {

        @Test
        @DisplayName("모든 상품이 순서대로 검증되고 hold된다")
        void createCartOrder_ShouldValidateAllProducts_ReserveAllStocks() {
            setupOrderCreationMocks();

            OrderInfo result = orderFacade.createCartOrder(USER_ID,
                    List.of(new OrderItemCommand(1L, 2)), null);

            assertThat(result).isNotNull();
            verify(productService).findOrderableById(1L);
            verify(stockService).hold(1L, 2);
            verify(orderService).createOrder(eq(USER_ID), eq(OrderType.CART), any(BigDecimal.class), anyList());
        }

        @Test
        @DisplayName("2번째 상품 hold 실패 시 1번째 hold가 release된다 (부분 성공 금지)")
        void createCartOrder_PartialStockFailure_ShouldRollbackAllReservations() {
            List<OrderItemCommand> merged = List.of(
                    new OrderItemCommand(1L, 2),
                    new OrderItemCommand(2L, 3));
            when(orderService.validateAndPrepare(eq(USER_ID), anyList())).thenReturn(merged);
            when(productService.findOrderableById(1L)).thenReturn(createTestProduct(1L));
            when(productService.findOrderableById(2L)).thenReturn(createTestProduct(2L));
            when(brandService.findById(1L)).thenReturn(createTestBrand());
            doNothing().when(stockService).hold(1L, 2);
            doThrow(new CoreException(ErrorType.STOCK_NOT_ENOUGH))
                    .when(stockService).hold(2L, 3);

            assertThatThrownBy(() -> orderFacade.createCartOrder(USER_ID, List.of(
                    new OrderItemCommand(1L, 2),
                    new OrderItemCommand(2L, 3)), null))
                    .isInstanceOf(CoreException.class);

            verify(stockService).release(1L, 2);
        }

        @Test
        @DisplayName("hold 순서가 productId 오름차순이다 (데드락 방지)")
        void createCartOrder_ShouldHoldInProductIdAscOrder() {
            List<OrderItemCommand> merged = List.of(
                    new OrderItemCommand(1L, 1),
                    new OrderItemCommand(3L, 1));
            when(orderService.validateAndPrepare(eq(USER_ID), anyList())).thenReturn(merged);
            when(productService.findOrderableById(1L)).thenReturn(createTestProduct(1L));
            when(productService.findOrderableById(3L)).thenReturn(createTestProduct(3L));
            when(brandService.findById(1L)).thenReturn(createTestBrand());
            OrderModel savedOrder = OrderModel.create(USER_ID, OrderType.CART, BigDecimal.valueOf(20000));
            when(orderService.createOrder(eq(USER_ID), any(OrderType.class), any(BigDecimal.class), anyList()))
                    .thenReturn(savedOrder);
            when(orderService.findOrderItems(any())).thenReturn(List.of());

            orderFacade.createCartOrder(USER_ID, List.of(
                    new OrderItemCommand(3L, 1),
                    new OrderItemCommand(1L, 1)), null);

            var inOrder = inOrder(stockService);
            inOrder.verify(stockService).hold(1L, 1);
            inOrder.verify(stockService).hold(3L, 1);
        }
    }

    // === 주문 취소 ===

    @Nested
    @DisplayName("주문 취소")
    class CancelOrderTests {

        @Test
        @DisplayName("취소 시 모든 주문 항목의 재고가 release되고 쿠폰이 복원된다")
        void cancelOrder_ShouldReleaseAllStocks() {
            OrderModel order = OrderModel.create(USER_ID, OrderType.CART, BigDecimal.valueOf(10000));
            when(orderService.cancelOrder(USER_ID, 1L)).thenReturn(Optional.of(order));
            OrderItemModel item = OrderItemModel.create(1L, 1, USER_ID, 1L, 3,
                    "상품명", BigDecimal.valueOf(10000), "brand-id", "브랜드", null);
            when(orderService.findOrderItems(1L)).thenReturn(List.of(item));

            orderFacade.cancelOrder(USER_ID, 1L);

            verify(stockService).release(1L, 3);
            verify(couponService).restoreCoupon(order.getOrderId());
        }

        @Test
        @DisplayName("DIRECT 주문 취소 시 장바구니가 복원된다")
        void cancelOrder_DIRECT_ShouldRestoreToCart() {
            OrderModel order = OrderModel.create(USER_ID, OrderType.DIRECT, BigDecimal.valueOf(10000));
            when(orderService.cancelOrder(USER_ID, 1L)).thenReturn(Optional.of(order));
            OrderItemModel item = OrderItemModel.create(1L, 1, USER_ID, 1L, 3,
                    "상품명", BigDecimal.valueOf(10000), "brand-id", "브랜드", null);
            when(orderService.findOrderItems(1L)).thenReturn(List.of(item));

            orderFacade.cancelOrder(USER_ID, 1L);

            verify(orderService).saveCartRestore(any(OrderCartRestoreModel.class));
            verify(cartService).restoreFromOrder(eq(USER_ID), anyList());
        }

        @Test
        @DisplayName("CART 주문 취소 시 장바구니 변경 없음")
        void cancelOrder_CART_ShouldNotRestoreCart() {
            OrderModel order = OrderModel.create(USER_ID, OrderType.CART, BigDecimal.valueOf(10000));
            when(orderService.cancelOrder(USER_ID, 1L)).thenReturn(Optional.of(order));
            when(orderService.findOrderItems(1L)).thenReturn(List.of());

            orderFacade.cancelOrder(USER_ID, 1L);

            verify(cartService, never()).restoreFromOrder(anyLong(), anyList());
            verify(orderService, never()).saveCartRestore(any());
        }

        @Test
        @DisplayName("이미 CANCELLED인 주문 취소 시 에러 없이 무시된다 (멱등)")
        void cancelOrder_WhenAlreadyCancelled_ShouldBeIdempotent() {
            when(orderService.cancelOrder(USER_ID, 1L)).thenReturn(Optional.empty());

            assertThatCode(() -> orderFacade.cancelOrder(USER_ID, 1L))
                    .doesNotThrowAnyException();
            verify(stockService, never()).release(anyLong(), anyInt());
        }
    }

    // === 주문 만료 ===

    @Nested
    @DisplayName("주문 만료")
    class ExpireOrderTests {

        @Test
        @DisplayName("만료 시 재고가 release되고 쿠폰이 복원된다")
        void expireOrder_ShouldReleaseAllStocks() {
            OrderModel order = OrderModel.create(USER_ID, OrderType.CART, BigDecimal.valueOf(10000));
            when(orderService.expireOrder(1L)).thenReturn(Optional.of(order));
            OrderItemModel item = OrderItemModel.create(1L, 1, USER_ID, 1L, 3,
                    "상품명", BigDecimal.valueOf(10000), "brand-id", "브랜드", null);
            when(orderService.findOrderItems(1L)).thenReturn(List.of(item));

            orderFacade.expireOrder(1L);

            verify(stockService).release(1L, 3);
            verify(couponService).restoreCoupon(order.getOrderId());
        }

        @Test
        @DisplayName("DIRECT 주문 만료 시 장바구니가 복원된다")
        void expireOrder_DIRECT_ShouldRestoreToCart() {
            OrderModel order = OrderModel.create(USER_ID, OrderType.DIRECT, BigDecimal.valueOf(10000));
            when(orderService.expireOrder(1L)).thenReturn(Optional.of(order));
            OrderItemModel item = OrderItemModel.create(1L, 1, USER_ID, 1L, 2,
                    "상품명", BigDecimal.valueOf(10000), "brand-id", "브랜드", null);
            when(orderService.findOrderItems(1L)).thenReturn(List.of(item));

            orderFacade.expireOrder(1L);

            verify(orderService).saveCartRestore(any(OrderCartRestoreModel.class));
            verify(cartService).restoreFromOrder(eq(USER_ID), anyList());
        }

        @Test
        @DisplayName("CAS 실패 시 skip된다 (멱등)")
        void expireOrder_AlreadyExpiredOrCancelled_ShouldSkip() {
            when(orderService.expireOrder(1L)).thenReturn(Optional.empty());

            assertThatCode(() -> orderFacade.expireOrder(1L))
                    .doesNotThrowAnyException();
            verify(stockService, never()).release(anyLong(), anyInt());
        }
    }
}
