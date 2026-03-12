package com.loopers.application.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.loopers.application.order.dto.OrderCriteria;
import com.loopers.application.order.dto.OrderResult;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.coupon.CouponErrorCode;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.order.OrderErrorCode;
import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.order.dto.OrderInfo;
import com.loopers.domain.product.ProductErrorCode;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.dto.ProductInfo;
import com.loopers.domain.user.UserService;
import com.loopers.support.error.CoreException;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@DisplayName("OrderFacade 단위 테스트")
@ExtendWith(MockitoExtension.class)
class OrderFacadeTest {

    @Mock
    private ProductService productService;

    @Mock
    private BrandService brandService;

    @Mock
    private OrderService orderService;

    @Mock
    private CouponService couponService;

    @Mock
    private UserService userService;

    @InjectMocks
    private OrderFacade orderFacade;

    @DisplayName("주문을 생성할 때 (UC-O01), ")
    @Nested
    class CreateOrder {

        @DisplayName("검증+차감 → 브랜드 조회 → 주문 생성 순서를 수행한다")
        @Test
        void createOrder_success() {
            // arrange
            Long brandId = 1L;
            when(productService.validateAndDeductStock(anyList())).thenReturn(List.of(
                    new ProductInfo.StockDeduction(10L, "상품A", 25000, 1, brandId)));
            when(brandService.getNameMapByIds(List.of(brandId))).thenReturn(Map.of(brandId, "브랜드A"));

            OrderModel order = OrderModel.create(1L, List.of(
                    OrderItemModel.create(10L, 25000, 1, "상품A", ("브랜드A"))));
            when(orderService.createOrder(anyLong(), anyList())).thenReturn(order);

            OrderCriteria.Create criteria = new OrderCriteria.Create(List.of(
                    new OrderCriteria.Create.CreateItem(10L, 1, 25000)));

            // act
            OrderResult.OrderSummary result = orderFacade.createOrder(1L, criteria);

            // assert
            assertAll(
                    () -> verify(productService).validateAndDeductStock(anyList()),
                    () -> verify(brandService).getNameMapByIds(List.of(brandId)),
                    () -> verify(orderService).createOrder(anyLong(), anyList()),
                    () -> verify(userService).deductPoint(1L, 25000),
                    () -> assertThat(result.totalPrice()).isEqualTo(25000));
        }

        @DisplayName("상품이 존재하지 않으면 예외가 발생한다")
        @Test
        void createOrder_productNotFound_throwsException() {
            // arrange
            when(productService.validateAndDeductStock(anyList()))
                    .thenThrow(new CoreException(ProductErrorCode.NOT_FOUND));

            OrderCriteria.Create criteria = new OrderCriteria.Create(List.of(
                    new OrderCriteria.Create.CreateItem(999L, 1, 25000)));

            // act & assert
            assertThatThrownBy(() -> orderFacade.createOrder(1L, criteria))
                    .isInstanceOf(CoreException.class);
        }

        @DisplayName("expectedPrice와 현재 가격 불일치 시 예외가 발생한다")
        @Test
        void createOrder_priceMismatch_throwsException() {
            // arrange
            when(productService.validateAndDeductStock(anyList()))
                    .thenThrow(new CoreException(ProductErrorCode.PRICE_MISMATCH));

            OrderCriteria.Create criteria = new OrderCriteria.Create(List.of(
                    new OrderCriteria.Create.CreateItem(10L, 1, 30000)));

            // act & assert
            assertThatThrownBy(() -> orderFacade.createOrder(1L, criteria))
                    .isInstanceOf(CoreException.class);
        }

        @DisplayName("재고 부족 시 예외가 발생한다")
        @Test
        void createOrder_insufficientStock_throwsException() {
            // arrange
            when(productService.validateAndDeductStock(anyList()))
                    .thenThrow(new CoreException(ProductErrorCode.NOT_FOUND));

            OrderCriteria.Create criteria = new OrderCriteria.Create(List.of(
                    new OrderCriteria.Create.CreateItem(10L, 100, 25000)));

            // act & assert
            assertThatThrownBy(() -> orderFacade.createOrder(1L, criteria))
                    .isInstanceOf(CoreException.class);
        }
    }

    @DisplayName("회원 주문 목록을 조회할 때 (UC-O02), ")
    @Nested
    class GetMyOrders {

        @DisplayName("기간 내 본인 주문 목록을 반환한다")
        @Test
        void getMyOrders_returnsOrders() {
            // arrange
            OrderModel order = OrderModel.create(1L, List.of(
                    OrderItemModel.create(10L, 50000, 1, "상품A", ("브랜드A"))));
            ZonedDateTime startAt = ZonedDateTime.now().minusDays(7);
            ZonedDateTime endAt = ZonedDateTime.now();

            when(orderService.getOrdersByUserIdAndPeriod(1L, startAt, endAt))
                    .thenReturn(List.of(order));

            OrderCriteria.ListByDate criteria = new OrderCriteria.ListByDate(startAt, endAt);

            // act
            List<OrderResult.OrderSummary> results = orderFacade.getMyOrders(1L, criteria);

            // assert
            assertAll(
                    () -> assertThat(results).hasSize(1),
                    () -> assertThat(results.get(0).totalPrice()).isEqualTo(50000));
        }
    }

    @DisplayName("회원 주문 상세를 조회할 때 (UC-O03), ")
    @Nested
    class GetMyOrderDetail {

        @DisplayName("주문 상세 + 주문 항목을 반환한다")
        @Test
        void getMyOrderDetail_returnsDetail() {
            // arrange
            OrderModel order = OrderModel.create(1L, List.of(
                    OrderItemModel.create(10L, 25000, 2, "상품A", ("브랜드A"))));

            when(orderService.getByIdAndUserId(1L, 1L)).thenReturn(order);

            // act
            OrderResult.OrderDetail result = orderFacade.getMyOrderDetail(1L, 1L);

            // assert
            assertAll(
                    () -> assertThat(result.totalPrice()).isEqualTo(50000),
                    () -> assertThat(result.items()).hasSize(1),
                    () -> assertThat(result.items().get(0).productName()).isEqualTo("상품A"));
        }

        @DisplayName("본인 주문이 아니면 예외가 발생한다")
        @Test
        void getMyOrderDetail_notOwner_throwsException() {
            // arrange
            when(orderService.getByIdAndUserId(1L, 1L))
                    .thenThrow(new CoreException(OrderErrorCode.FORBIDDEN));

            // act & assert
            assertThatThrownBy(() -> orderFacade.getMyOrderDetail(1L, 1L))
                    .isInstanceOf(CoreException.class);
        }
    }

    @DisplayName("관리자 주문 조회할 때, ")
    @Nested
    class AdminOrders {

        @DisplayName("전체 주문 페이지네이션을 반환한다")
        @Test
        void getAllOrders_returnsPage() {
            // arrange
            OrderModel order = OrderModel.create(1L, List.of(
                    OrderItemModel.create(10L, 50000, 1, "상품A", ("브랜드A"))));
            Page<OrderModel> page = new PageImpl<>(List.of(order), PageRequest.of(0, 10), 1);

            when(orderService.getAllOrders(any())).thenReturn(page);

            // act
            Page<OrderResult.OrderSummary> result = orderFacade.getAllOrders(PageRequest.of(0, 10));

            // assert
            assertAll(
                    () -> assertThat(result.getTotalElements()).isEqualTo(1),
                    () -> assertThat(result.getContent().get(0).totalPrice()).isEqualTo(50000));
        }

        @DisplayName("주문 상세를 반환한다 (소유권 검증 없음)")
        @Test
        void getOrderDetail_returnsDetail_withoutOwnerCheck() {
            // arrange
            OrderModel order = OrderModel.create(2L, List.of(
                    OrderItemModel.create(10L, 25000, 2, "상품A", ("브랜드A"))));

            when(orderService.getById(1L)).thenReturn(order);

            // act
            OrderResult.OrderDetail result = orderFacade.getOrderDetail(1L);

            // assert
            assertAll(
                    () -> assertThat(result.userId()).isEqualTo(2L),
                    () -> assertThat(result.items()).hasSize(1));
        }
    }

    @DisplayName("회원 아이템 취소할 때 (UC-O04), ")
    @Nested
    class CancelMyOrderItem {

        @DisplayName("소유자 검증 + 아이템 취소 + 재고 복구가 수행된다")
        @Test
        void cancelMyOrderItem_success() {
            // arrange
            when(orderService.getByIdAndUserId(1L, 1L)).thenReturn(
                    OrderModel.create(1L, List.of(
                            OrderItemModel.create(10L, 25000, 2, "상품A", "브랜드A"))));
            when(orderService.cancelItem(any(OrderModel.class), eq(100L)))
                    .thenReturn(new OrderInfo.CancelledItem(10L, 2, false));

            // act
            orderFacade.cancelMyOrderItem(1L, 1L, 100L);

            // assert
            assertAll(
                    () -> verify(orderService).getByIdAndUserId(1L, 1L),
                    () -> verify(orderService).cancelItem(any(OrderModel.class), eq(100L)),
                    () -> verify(productService).increaseStock(10L, 2));
        }

        @DisplayName("본인 주문이 아니면 예외가 발생한다")
        @Test
        void cancelMyOrderItem_notOwner_throwsException() {
            // arrange
            when(orderService.getByIdAndUserId(1L, 999L))
                    .thenThrow(new CoreException(OrderErrorCode.FORBIDDEN));

            // act & assert
            assertThatThrownBy(() -> orderFacade.cancelMyOrderItem(999L, 1L, 100L))
                    .isInstanceOf(CoreException.class);
        }
    }

    @DisplayName("쿠폰 적용 주문을 생성할 때, ")
    @Nested
    class CreateOrderWithCoupon {

        @DisplayName("★ 정액(FIXED) 할인이 적용된다")
        @Test
        void createOrder_withFixedCoupon() {
            // arrange
            Long brandId = 1L;
            when(productService.validateAndDeductStock(anyList())).thenReturn(List.of(
                    new ProductInfo.StockDeduction(10L, "상품A", 50000, 1, brandId)));
            when(brandService.getNameMapByIds(List.of(brandId))).thenReturn(Map.of(brandId, "브랜드A"));

            OrderModel order = OrderModel.create(1L, List.of(
                    OrderItemModel.create(10L, 50000, 1, "상품A", "브랜드A")));
            setId(order, 1L);
            when(orderService.createOrder(anyLong(), anyList()))
                    .thenReturn(order);
            doAnswer(invocation -> {
                ((OrderModel) invocation.getArgument(0)).applyDiscount(invocation.getArgument(1));
                return null;
            }).when(orderService).applyDiscount(any(OrderModel.class), anyInt());
            when(couponService.useAndCalculateDiscount(5L, 1L, 1L, 50000L))
                    .thenReturn(5000L);

            OrderCriteria.Create criteria = new OrderCriteria.Create(List.of(
                    new OrderCriteria.Create.CreateItem(10L, 1, 50000)), 5L);

            // act
            OrderResult.OrderSummary result = orderFacade.createOrder(1L, criteria);

            // assert
            assertAll(
                    () -> verify(couponService).useAndCalculateDiscount(5L, 1L, 1L, 50000L),
                    () -> verify(orderService).applyDiscount(any(OrderModel.class), eq(5000)),
                    () -> assertThat(result.totalPrice()).isEqualTo(45000));
        }

        @DisplayName("★ 정률(RATE) 할인이 적용된다")
        @Test
        void createOrder_withRateCoupon() {
            // arrange
            Long brandId = 1L;
            when(productService.validateAndDeductStock(anyList())).thenReturn(List.of(
                    new ProductInfo.StockDeduction(10L, "상품A", 50000, 1, brandId)));
            when(brandService.getNameMapByIds(List.of(brandId))).thenReturn(Map.of(brandId, "브랜드A"));

            OrderModel order = OrderModel.create(1L, List.of(
                    OrderItemModel.create(10L, 50000, 1, "상품A", "브랜드A")));
            setId(order, 1L);
            when(orderService.createOrder(anyLong(), anyList()))
                    .thenReturn(order);
            doAnswer(invocation -> {
                ((OrderModel) invocation.getArgument(0)).applyDiscount(invocation.getArgument(1));
                return null;
            }).when(orderService).applyDiscount(any(OrderModel.class), anyInt());
            when(couponService.useAndCalculateDiscount(5L, 1L, 1L, 50000L))
                    .thenReturn(5000L);

            OrderCriteria.Create criteria = new OrderCriteria.Create(List.of(
                    new OrderCriteria.Create.CreateItem(10L, 1, 50000)), 5L);

            // act
            OrderResult.OrderSummary result = orderFacade.createOrder(1L, criteria);

            // assert
            assertAll(
                    () -> verify(couponService).useAndCalculateDiscount(5L, 1L, 1L, 50000L),
                    () -> verify(orderService).applyDiscount(any(OrderModel.class), eq(5000)),
                    () -> assertThat(result.totalPrice()).isEqualTo(45000));
        }

        @DisplayName("★ 이미 사용된 쿠폰으로 주문 시 실패한다")
        @Test
        void createOrder_withAlreadyUsedCoupon() {
            // arrange
            Long brandId = 1L;
            when(productService.validateAndDeductStock(anyList())).thenReturn(List.of(
                    new ProductInfo.StockDeduction(10L, "상품A", 50000, 1, brandId)));
            when(brandService.getNameMapByIds(List.of(brandId))).thenReturn(Map.of(brandId, "브랜드A"));

            OrderModel order = OrderModel.create(1L, List.of(
                    OrderItemModel.create(10L, 50000, 1, "상품A", "브랜드A")));
            setId(order, 1L);
            when(orderService.createOrder(anyLong(), anyList())).thenReturn(order);
            when(couponService.useAndCalculateDiscount(eq(5L), eq(1L), anyLong(), eq(50000L)))
                    .thenThrow(new CoreException(CouponErrorCode.ALREADY_USED));

            OrderCriteria.Create criteria = new OrderCriteria.Create(List.of(
                    new OrderCriteria.Create.CreateItem(10L, 1, 50000)), 5L);

            // act & assert
            assertThatThrownBy(() -> orderFacade.createOrder(1L, criteria))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorCode())
                            .isEqualTo(CouponErrorCode.ALREADY_USED));
        }

        @DisplayName("쿠폰 없이 주문하면 기존 흐름 그대로 동작한다")
        @Test
        void createOrder_withoutCoupon() {
            // arrange
            Long brandId = 1L;
            when(productService.validateAndDeductStock(anyList())).thenReturn(List.of(
                    new ProductInfo.StockDeduction(10L, "상품A", 50000, 1, brandId)));
            when(brandService.getNameMapByIds(List.of(brandId))).thenReturn(Map.of(brandId, "브랜드A"));

            OrderModel order = OrderModel.create(1L, List.of(
                    OrderItemModel.create(10L, 50000, 1, "상품A", "브랜드A")));
            when(orderService.createOrder(anyLong(), anyList()))
                    .thenReturn(order);

            OrderCriteria.Create criteria = new OrderCriteria.Create(List.of(
                    new OrderCriteria.Create.CreateItem(10L, 1, 50000)));

            // act
            OrderResult.OrderSummary result = orderFacade.createOrder(1L, criteria);

            // assert
            assertAll(
                    () -> verify(couponService, never()).useAndCalculateDiscount(
                            anyLong(), anyLong(), anyLong(), anyLong()),
                    () -> assertThat(result.totalPrice()).isEqualTo(50000));
        }

        @DisplayName("존재하지 않는 쿠폰으로 주문 시 실패한다")
        @Test
        void createOrder_withNotFoundCoupon() {
            // arrange
            Long brandId = 1L;
            when(productService.validateAndDeductStock(anyList())).thenReturn(List.of(
                    new ProductInfo.StockDeduction(10L, "상품A", 50000, 1, brandId)));
            when(brandService.getNameMapByIds(List.of(brandId))).thenReturn(Map.of(brandId, "브랜드A"));

            OrderModel order = OrderModel.create(1L, List.of(
                    OrderItemModel.create(10L, 50000, 1, "상품A", "브랜드A")));
            setId(order, 1L);
            when(orderService.createOrder(anyLong(), anyList())).thenReturn(order);
            when(couponService.useAndCalculateDiscount(eq(999L), eq(1L), anyLong(), eq(50000L)))
                    .thenThrow(new CoreException(CouponErrorCode.NOT_FOUND));

            OrderCriteria.Create criteria = new OrderCriteria.Create(List.of(
                    new OrderCriteria.Create.CreateItem(10L, 1, 50000)), 999L);

            // act & assert
            assertThatThrownBy(() -> orderFacade.createOrder(1L, criteria))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorCode())
                            .isEqualTo(CouponErrorCode.NOT_FOUND));
        }

        @DisplayName("본인 소유가 아닌 쿠폰으로 주문 시 실패한다")
        @Test
        void createOrder_withNotOwnedCoupon() {
            // arrange
            Long brandId = 1L;
            when(productService.validateAndDeductStock(anyList())).thenReturn(List.of(
                    new ProductInfo.StockDeduction(10L, "상품A", 50000, 1, brandId)));
            when(brandService.getNameMapByIds(List.of(brandId))).thenReturn(Map.of(brandId, "브랜드A"));

            OrderModel order = OrderModel.create(1L, List.of(
                    OrderItemModel.create(10L, 50000, 1, "상품A", "브랜드A")));
            setId(order, 1L);
            when(orderService.createOrder(anyLong(), anyList())).thenReturn(order);
            when(couponService.useAndCalculateDiscount(eq(5L), eq(1L), anyLong(), eq(50000L)))
                    .thenThrow(new CoreException(CouponErrorCode.NOT_OWNED));

            OrderCriteria.Create criteria = new OrderCriteria.Create(List.of(
                    new OrderCriteria.Create.CreateItem(10L, 1, 50000)), 5L);

            // act & assert
            assertThatThrownBy(() -> orderFacade.createOrder(1L, criteria))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorCode())
                            .isEqualTo(CouponErrorCode.NOT_OWNED));
        }

        @DisplayName("최소 주문 금액 미달 시 주문 실패한다")
        @Test
        void createOrder_withMinOrderAmountNotMet() {
            // arrange
            Long brandId = 1L;
            when(productService.validateAndDeductStock(anyList())).thenReturn(List.of(
                    new ProductInfo.StockDeduction(10L, "상품A", 10000, 1, brandId)));
            when(brandService.getNameMapByIds(List.of(brandId))).thenReturn(Map.of(brandId, "브랜드A"));

            OrderModel order = OrderModel.create(1L, List.of(
                    OrderItemModel.create(10L, 10000, 1, "상품A", "브랜드A")));
            setId(order, 1L);
            when(orderService.createOrder(anyLong(), anyList())).thenReturn(order);
            when(couponService.useAndCalculateDiscount(eq(5L), eq(1L), anyLong(), eq(10000L)))
                    .thenThrow(new CoreException(CouponErrorCode.MIN_ORDER_AMOUNT_NOT_MET));

            OrderCriteria.Create criteria = new OrderCriteria.Create(List.of(
                    new OrderCriteria.Create.CreateItem(10L, 1, 10000)), 5L);

            // act & assert
            assertThatThrownBy(() -> orderFacade.createOrder(1L, criteria))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorCode())
                            .isEqualTo(CouponErrorCode.MIN_ORDER_AMOUNT_NOT_MET));
        }
    }

    @DisplayName("쿠폰 적용 주문을 취소할 때, ")
    @Nested
    class CancelOrderWithCoupon {

        @DisplayName("전체 취소 시 쿠폰이 복원된다")
        @Test
        void cancelOrderItem_whenAllCancelled_restoresCoupon() {
            // arrange
            when(orderService.getByIdAndUserId(1L, 1L)).thenReturn(
                    OrderModel.create(1L, List.of(
                            OrderItemModel.create(10L, 50000, 1, "상품A", "브랜드A")), 5000));
            when(orderService.cancelItem(any(OrderModel.class), eq(100L)))
                    .thenReturn(new OrderInfo.CancelledItem(10L, 1, true));

            // act
            orderFacade.cancelMyOrderItem(1L, 1L, 100L);

            // assert
            assertAll(
                    () -> verify(couponService).restoreByOrderId(1L),
                    () -> verify(productService).increaseStock(10L, 1));
        }

        @DisplayName("부분 취소 시 쿠폰은 복원하지 않는다")
        @Test
        void cancelOrderItem_whenPartiallyCancelled_doesNotRestoreCoupon() {
            // arrange
            when(orderService.getByIdAndUserId(1L, 1L)).thenReturn(
                    OrderModel.create(1L, List.of(
                            OrderItemModel.create(10L, 30000, 1, "상품A", "브랜드A"),
                            OrderItemModel.create(20L, 20000, 1, "상품B", "브랜드A")), 5000));
            when(orderService.cancelItem(any(OrderModel.class), eq(100L)))
                    .thenReturn(new OrderInfo.CancelledItem(10L, 1, false));

            // act
            orderFacade.cancelMyOrderItem(1L, 1L, 100L);

            // assert
            assertAll(
                    () -> verify(couponService, never()).restoreByOrderId(anyLong()),
                    () -> verify(productService).increaseStock(10L, 1));
        }
    }

    @DisplayName("관리자 아이템 취소할 때, ")
    @Nested
    class CancelOrderItem {

        @DisplayName("소유권 검증 없이 아이템 취소 + 재고 복구가 수행된다")
        @Test
        void cancelOrderItem_admin_success() {
            // arrange
            when(orderService.getById(1L)).thenReturn(
                    OrderModel.create(1L, List.of(
                            OrderItemModel.create(10L, 25000, 2, "상품A", "브랜드A"))));
            when(orderService.cancelItem(any(OrderModel.class), eq(100L)))
                    .thenReturn(new OrderInfo.CancelledItem(10L, 2, false));

            // act
            orderFacade.cancelOrderItem(1L, 100L);

            // assert
            assertAll(
                    () -> verify(orderService).getById(1L),
                    () -> verify(orderService).cancelItem(any(OrderModel.class), eq(100L)),
                    () -> verify(productService).increaseStock(10L, 2));
        }

        @DisplayName("전체 취소 시 쿠폰이 복원된다")
        @Test
        void cancelOrderItem_admin_allCancelled_restoresCoupon() {
            // arrange
            when(orderService.getById(1L)).thenReturn(
                    OrderModel.create(1L, List.of(
                            OrderItemModel.create(10L, 50000, 1, "상품A", "브랜드A")), 5000));
            when(orderService.cancelItem(any(OrderModel.class), eq(100L)))
                    .thenReturn(new OrderInfo.CancelledItem(10L, 1, true));

            // act
            orderFacade.cancelOrderItem(1L, 100L);

            // assert
            assertAll(
                    () -> verify(couponService).restoreByOrderId(1L),
                    () -> verify(productService).increaseStock(10L, 1));
        }
    }

    private static void setId(Object target, Long id) {
        try {
            var field = target.getClass().getSuperclass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(target, id);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
