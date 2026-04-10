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
import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.product.ProductErrorCode;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.dto.ProductInfo;
import com.loopers.domain.user.UserService;
import com.loopers.support.error.CoreException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("AdminOrderService 단위 테스트")
@ExtendWith(MockitoExtension.class)
class AdminOrderServiceTest {

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
    private AdminOrderService adminOrderService;

    @DisplayName("주문을 생성할 때, ")
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
                    OrderItemModel.create(10L, 25000, 1, "상품A", "브랜드A")));
            when(orderService.createOrder(anyLong(), anyList())).thenReturn(order);

            OrderCriteria.Create criteria = new OrderCriteria.Create(List.of(
                    new OrderCriteria.Create.CreateItem(10L, 1, 25000)));

            // act
            OrderResult.OrderSummary result = adminOrderService.createOrder(1L, criteria);

            // assert
            assertAll(
                    () -> verify(productService).validateAndDeductStock(anyList()),
                    () -> verify(brandService).getNameMapByIds(List.of(brandId)),
                    () -> verify(orderService).createOrder(anyLong(), anyList()),
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
            assertThatThrownBy(() -> adminOrderService.createOrder(1L, criteria))
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
            assertThatThrownBy(() -> adminOrderService.createOrder(1L, criteria))
                    .isInstanceOf(CoreException.class);
        }
    }

    @DisplayName("쿠폰 적용 주문을 생성할 때, ")
    @Nested
    class CreateOrderWithCoupon {

        @DisplayName("정액(FIXED) 할인이 적용된다")
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
            when(orderService.createOrder(anyLong(), anyList())).thenReturn(order);
            doAnswer(invocation -> {
                ((OrderModel) invocation.getArgument(0)).applyDiscount(invocation.getArgument(1));
                return null;
            }).when(orderService).applyDiscount(any(OrderModel.class), anyInt());
            when(couponService.useAndCalculateDiscount(5L, 1L, 1L, 50000L)).thenReturn(5000L);

            OrderCriteria.Create criteria = new OrderCriteria.Create(List.of(
                    new OrderCriteria.Create.CreateItem(10L, 1, 50000)), 5L);

            // act
            OrderResult.OrderSummary result = adminOrderService.createOrder(1L, criteria);

            // assert
            assertAll(
                    () -> verify(couponService).useAndCalculateDiscount(5L, 1L, 1L, 50000L),
                    () -> assertThat(result.totalPrice()).isEqualTo(45000));
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
            when(orderService.createOrder(anyLong(), anyList())).thenReturn(order);

            OrderCriteria.Create criteria = new OrderCriteria.Create(List.of(
                    new OrderCriteria.Create.CreateItem(10L, 1, 50000)));

            // act
            OrderResult.OrderSummary result = adminOrderService.createOrder(1L, criteria);

            // assert
            assertAll(
                    () -> verify(couponService, never()).useAndCalculateDiscount(
                            anyLong(), anyLong(), anyLong(), anyLong()),
                    () -> assertThat(result.totalPrice()).isEqualTo(50000));
        }

        @DisplayName("이미 사용된 쿠폰으로 주문 시 실패한다")
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
            assertThatThrownBy(() -> adminOrderService.createOrder(1L, criteria))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorCode())
                            .isEqualTo(CouponErrorCode.ALREADY_USED));
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
