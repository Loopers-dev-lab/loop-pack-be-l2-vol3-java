package com.loopers.application.order;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.cart.CartService;
import com.loopers.domain.order.OrderCartRestoreModel;
import com.loopers.domain.order.OrderItemCommand;
import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderItemSnapshot;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.StockService;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserService;
import com.loopers.support.enums.OrderStatus;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderFacade 단위 테스트")
class OrderFacadeTest {

    @Mock OrderService orderService;
    @Mock UserService userService;
    @Mock ProductService productService;
    @Mock BrandService brandService;
    @Mock StockService stockService;
    @Mock CartService cartService;

    @InjectMocks
    OrderFacade orderFacade;

    private UserModel mockAuthenticate() {
        UserModel user = mock(UserModel.class);
        when(user.getUserId()).thenReturn("user-1");
        when(userService.authenticate("login1", "pw1")).thenReturn(user);
        return user;
    }

    private ProductModel createTestProduct() {
        return ProductModel.create("테스트상품", "brand-id", BigDecimal.valueOf(10000),
                "설명", null, null, null, null, null, null);
    }

    private ProductModel createTestProduct(String productId) {
        return ProductModel.create("테스트상품-" + productId, "brand-id", BigDecimal.valueOf(10000),
                "설명", null, null, null, null, null, null);
    }

    private BrandModel createTestBrand() {
        return BrandModel.create("테스트브랜드", "설명", "서울");
    }

    private void setupOrderCreationMocks() {
        List<OrderItemCommand> merged = List.of(new OrderItemCommand("product-1", 2));
        when(orderService.validateAndPrepare("user-1", anyList())).thenReturn(merged);
        when(productService.findOrderableById("product-1")).thenReturn(createTestProduct());
        when(brandService.findById("brand-id")).thenReturn(createTestBrand());

        OrderModel savedOrder = OrderModel.create("user-1", OrderType.DIRECT, BigDecimal.valueOf(20000));
        when(orderService.createOrder(eq("user-1"), any(OrderType.class), any(BigDecimal.class), anyList()))
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
            mockAuthenticate();
            setupOrderCreationMocks();

            List<OrderItemCommand> items = List.of(new OrderItemCommand("product-1", 2));
            OrderInfo result = orderFacade.createDirectOrder("login1", "pw1", items);

            assertThat(result).isNotNull();
            verify(userService).authenticate("login1", "pw1");
            verify(productService).findOrderableById("product-1");
            verify(stockService).hold("product-1", 2);
            verify(orderService).createOrder(eq("user-1"), eq(OrderType.DIRECT), any(BigDecimal.class), anyList());
        }

        @Test
        @DisplayName("주문 불가 상품으로 주문 시 예외가 발생하고 hold가 호출되지 않는다")
        void createDirectOrder_ProductNotOrderable_ShouldThrow() {
            mockAuthenticate();
            List<OrderItemCommand> merged = List.of(new OrderItemCommand("product-1", 2));
            when(orderService.validateAndPrepare("user-1", anyList())).thenReturn(merged);
            when(productService.findOrderableById("product-1"))
                    .thenThrow(new CoreException(ErrorType.PRODUCT_NOT_ORDERABLE));

            assertThatThrownBy(() -> orderFacade.createDirectOrder("login1", "pw1",
                    List.of(new OrderItemCommand("product-1", 2))))
                    .isInstanceOf(CoreException.class)
                    .satisfies(ex -> assertThat(((CoreException) ex).getErrorType())
                            .isEqualTo(ErrorType.PRODUCT_NOT_ORDERABLE));
            verify(stockService, never()).hold(anyString(), anyInt());
        }

        @Test
        @DisplayName("재고 부족 시 STOCK_NOT_ENOUGH 예외가 발생한다")
        void createDirectOrder_InsufficientStock_ShouldThrow() {
            mockAuthenticate();
            List<OrderItemCommand> merged = List.of(new OrderItemCommand("product-1", 100));
            when(orderService.validateAndPrepare("user-1", anyList())).thenReturn(merged);
            when(productService.findOrderableById("product-1")).thenReturn(createTestProduct());
            when(brandService.findById("brand-id")).thenReturn(createTestBrand());
            doThrow(new CoreException(ErrorType.STOCK_NOT_ENOUGH))
                    .when(stockService).hold("product-1", 100);

            assertThatThrownBy(() -> orderFacade.createDirectOrder("login1", "pw1",
                    List.of(new OrderItemCommand("product-1", 100))))
                    .isInstanceOf(CoreException.class)
                    .satisfies(ex -> assertThat(((CoreException) ex).getErrorType())
                            .isEqualTo(ErrorType.STOCK_NOT_ENOUGH));
        }

        @Test
        @DisplayName("총액이 sum(unitPrice * quantity)와 일치한다")
        void createDirectOrder_ShouldCalculateTotalAmount() {
            mockAuthenticate();
            List<OrderItemCommand> merged = List.of(new OrderItemCommand("product-1", 3));
            when(orderService.validateAndPrepare("user-1", anyList())).thenReturn(merged);
            when(productService.findOrderableById("product-1")).thenReturn(createTestProduct());
            when(brandService.findById("brand-id")).thenReturn(createTestBrand());

            OrderModel savedOrder = OrderModel.create("user-1", OrderType.DIRECT, BigDecimal.valueOf(30000));
            when(orderService.createOrder(eq("user-1"), eq(OrderType.DIRECT),
                    eq(BigDecimal.valueOf(30000)), anyList()))
                    .thenReturn(savedOrder);
            when(orderService.findOrderItems(any())).thenReturn(List.of());

            OrderInfo result = orderFacade.createDirectOrder("login1", "pw1",
                    List.of(new OrderItemCommand("product-1", 3)));

            assertThat(result.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(30000));
        }
    }

    // === 장바구니 주문 (CART) ===

    @Nested
    @DisplayName("장바구니 주문 (CART)")
    class CartOrderTests {

        @Test
        @DisplayName("모든 상품이 순서대로 검증되고 hold된다")
        void createCartOrder_ShouldValidateAllProducts_ReserveAllStocks() {
            mockAuthenticate();
            setupOrderCreationMocks();

            OrderInfo result = orderFacade.createCartOrder("login1", "pw1",
                    List.of(new OrderItemCommand("product-1", 2)));

            assertThat(result).isNotNull();
            verify(productService).findOrderableById("product-1");
            verify(stockService).hold("product-1", 2);
            verify(orderService).createOrder(eq("user-1"), eq(OrderType.CART), any(BigDecimal.class), anyList());
        }

        @Test
        @DisplayName("2번째 상품 hold 실패 시 1번째 hold가 release된다 (부분 성공 금지)")
        void createCartOrder_PartialStockFailure_ShouldRollbackAllReservations() {
            mockAuthenticate();
            List<OrderItemCommand> merged = List.of(
                    new OrderItemCommand("product-a", 2),
                    new OrderItemCommand("product-b", 3));
            when(orderService.validateAndPrepare("user-1", anyList())).thenReturn(merged);
            when(productService.findOrderableById("product-a")).thenReturn(createTestProduct("product-a"));
            when(productService.findOrderableById("product-b")).thenReturn(createTestProduct("product-b"));
            when(brandService.findById("brand-id")).thenReturn(createTestBrand());
            doNothing().when(stockService).hold("product-a", 2);
            doThrow(new CoreException(ErrorType.STOCK_NOT_ENOUGH))
                    .when(stockService).hold("product-b", 3);

            assertThatThrownBy(() -> orderFacade.createCartOrder("login1", "pw1", List.of(
                    new OrderItemCommand("product-a", 2),
                    new OrderItemCommand("product-b", 3))))
                    .isInstanceOf(CoreException.class);

            verify(stockService).release("product-a", 2);
        }

        @Test
        @DisplayName("hold 순서가 productId 오름차순이다 (데드락 방지)")
        void createCartOrder_ShouldHoldInProductIdAscOrder() {
            mockAuthenticate();
            List<OrderItemCommand> merged = List.of(
                    new OrderItemCommand("aaa-product", 1),
                    new OrderItemCommand("zzz-product", 1));
            when(orderService.validateAndPrepare("user-1", anyList())).thenReturn(merged);
            when(productService.findOrderableById("aaa-product")).thenReturn(createTestProduct("aaa-product"));
            when(productService.findOrderableById("zzz-product")).thenReturn(createTestProduct("zzz-product"));
            when(brandService.findById("brand-id")).thenReturn(createTestBrand());
            OrderModel savedOrder = OrderModel.create("user-1", OrderType.CART, BigDecimal.valueOf(20000));
            when(orderService.createOrder(eq("user-1"), any(OrderType.class), any(BigDecimal.class), anyList()))
                    .thenReturn(savedOrder);
            when(orderService.findOrderItems(any())).thenReturn(List.of());

            orderFacade.createCartOrder("login1", "pw1", List.of(
                    new OrderItemCommand("zzz-product", 1),
                    new OrderItemCommand("aaa-product", 1)));

            var inOrder = inOrder(stockService);
            inOrder.verify(stockService).hold("aaa-product", 1);
            inOrder.verify(stockService).hold("zzz-product", 1);
        }
    }

    // === 주문 취소 ===

    @Nested
    @DisplayName("주문 취소")
    class CancelOrderTests {

        @Test
        @DisplayName("취소 시 모든 주문 항목의 재고가 release된다")
        void cancelOrder_ShouldReleaseAllStocks() {
            mockAuthenticate();
            OrderModel order = OrderModel.create("user-1", OrderType.CART, BigDecimal.valueOf(10000));
            when(orderService.cancelOrder("user-1", "order-1")).thenReturn(Optional.of(order));
            OrderItemModel item = OrderItemModel.create("order-1", 1, "user-1", "product-1", 3,
                    "상품명", BigDecimal.valueOf(10000), "brand-id", "브랜드", null);
            when(orderService.findOrderItems("order-1")).thenReturn(List.of(item));

            orderFacade.cancelOrder("login1", "pw1", "order-1");

            verify(stockService).release("product-1", 3);
        }

        @Test
        @DisplayName("DIRECT 주문 취소 시 장바구니가 복원된다")
        void cancelOrder_DIRECT_ShouldRestoreToCart() {
            mockAuthenticate();
            OrderModel order = OrderModel.create("user-1", OrderType.DIRECT, BigDecimal.valueOf(10000));
            when(orderService.cancelOrder("user-1", "order-1")).thenReturn(Optional.of(order));
            OrderItemModel item = OrderItemModel.create("order-1", 1, "user-1", "product-1", 3,
                    "상품명", BigDecimal.valueOf(10000), "brand-id", "브랜드", null);
            when(orderService.findOrderItems("order-1")).thenReturn(List.of(item));

            orderFacade.cancelOrder("login1", "pw1", "order-1");

            verify(orderService).saveCartRestore(any(OrderCartRestoreModel.class));
            verify(cartService).restoreFromOrder(eq("user-1"), anyList());
        }

        @Test
        @DisplayName("CART 주문 취소 시 장바구니 변경 없음")
        void cancelOrder_CART_ShouldNotRestoreCart() {
            mockAuthenticate();
            OrderModel order = OrderModel.create("user-1", OrderType.CART, BigDecimal.valueOf(10000));
            when(orderService.cancelOrder("user-1", "order-1")).thenReturn(Optional.of(order));
            when(orderService.findOrderItems("order-1")).thenReturn(List.of());

            orderFacade.cancelOrder("login1", "pw1", "order-1");

            verify(cartService, never()).restoreFromOrder(anyString(), anyList());
            verify(orderService, never()).saveCartRestore(any());
        }

        @Test
        @DisplayName("이미 CANCELLED인 주문 취소 시 에러 없이 무시된다 (멱등)")
        void cancelOrder_WhenAlreadyCancelled_ShouldBeIdempotent() {
            mockAuthenticate();
            when(orderService.cancelOrder("user-1", "order-1")).thenReturn(Optional.empty());

            assertThatCode(() -> orderFacade.cancelOrder("login1", "pw1", "order-1"))
                    .doesNotThrowAnyException();
            verify(stockService, never()).release(anyString(), anyInt());
        }
    }

    // === 주문 만료 ===

    @Nested
    @DisplayName("주문 만료")
    class ExpireOrderTests {

        @Test
        @DisplayName("만료 시 재고가 release된다")
        void expireOrder_ShouldReleaseAllStocks() {
            OrderModel order = OrderModel.create("user-1", OrderType.CART, BigDecimal.valueOf(10000));
            when(orderService.expireOrder("order-1")).thenReturn(Optional.of(order));
            OrderItemModel item = OrderItemModel.create("order-1", 1, "user-1", "product-1", 3,
                    "상품명", BigDecimal.valueOf(10000), "brand-id", "브랜드", null);
            when(orderService.findOrderItems("order-1")).thenReturn(List.of(item));

            orderFacade.expireOrder("order-1");

            verify(stockService).release("product-1", 3);
        }

        @Test
        @DisplayName("DIRECT 주문 만료 시 장바구니가 복원된다")
        void expireOrder_DIRECT_ShouldRestoreToCart() {
            OrderModel order = OrderModel.create("user-1", OrderType.DIRECT, BigDecimal.valueOf(10000));
            when(orderService.expireOrder("order-1")).thenReturn(Optional.of(order));
            OrderItemModel item = OrderItemModel.create("order-1", 1, "user-1", "product-1", 2,
                    "상품명", BigDecimal.valueOf(10000), "brand-id", "브랜드", null);
            when(orderService.findOrderItems("order-1")).thenReturn(List.of(item));

            orderFacade.expireOrder("order-1");

            verify(orderService).saveCartRestore(any(OrderCartRestoreModel.class));
            verify(cartService).restoreFromOrder(eq("user-1"), anyList());
        }

        @Test
        @DisplayName("CAS 실패 시 skip된다 (멱등)")
        void expireOrder_AlreadyExpiredOrCancelled_ShouldSkip() {
            when(orderService.expireOrder("order-1")).thenReturn(Optional.empty());

            assertThatCode(() -> orderFacade.expireOrder("order-1"))
                    .doesNotThrowAnyException();
            verify(stockService, never()).release(anyString(), anyInt());
        }
    }

}
