package com.loopers.application.order;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductStatus;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderFacadeTest {

    @Mock
    private OrderService orderService;

    @Mock
    private ProductService productService;

    @Mock
    private UserService userService;

    private OrderFacade orderFacade;

    @BeforeEach
    void setUp() {
        orderFacade = new OrderFacade(orderService, productService, userService);
    }

    @DisplayName("주문 요청 시, ")
    @Nested
    class PlaceOrder {

        @DisplayName("정상적인 요청이면 재고 차감 후 주문이 생성된다.")
        @Test
        void placesOrder_whenValidRequestIsProvided() {
            // arrange
            UserModel user = UserModel.createWithEncodedPassword(
                "testuser",
                "encoded",
                "홍길동",
                LocalDate.of(1990, 1, 15),
                "test@example.com"
            );
            BrandModel brand = new BrandModel("나이키", "스포츠 의류 및 신발 브랜드");
            ProductModel product = new ProductModel(brand, "에어맥스", 150000L, "설명", 10, ProductStatus.ON_SALE);
            OrderModel savedOrder = new OrderModel(
                0L,
                List.of(new OrderItemModel(product.getId(), product.getName(), product.getPrice(), 2))
            );

            given(userService.getMyInfo("testuser", "Test1234!")).willReturn(user);
            given(productService.getProduct(1L)).willReturn(product);
            given(orderService.placeOrder(eq(0L), any())).willReturn(savedOrder);

            // act
            OrderDetailInfo result = orderFacade.placeOrder(
                "testuser",
                "Test1234!",
                List.of(new OrderFacade.PlaceOrderItem(1L, 2))
            );

            // assert
            assertThat(result.totalAmount()).isEqualTo(300000L);
            verify(productService).deductStock(1L, 2);
            verify(orderService).placeOrder(eq(0L), any());
        }

        @DisplayName("재고가 부족하면 주문 생성이 실패하고 주문 저장은 수행되지 않는다.")
        @Test
        void failsOrderAndDoesNotSave_whenStockIsInsufficient() {
            // arrange
            UserModel user = UserModel.createWithEncodedPassword(
                "testuser",
                "encoded",
                "홍길동",
                LocalDate.of(1990, 1, 15),
                "test@example.com"
            );
            BrandModel brand = new BrandModel("나이키", "스포츠 의류 및 신발 브랜드");
            ProductModel product = new ProductModel(brand, "에어맥스", 150000L, "설명", 1, ProductStatus.ON_SALE);

            given(userService.getMyInfo("testuser", "Test1234!")).willReturn(user);
            given(productService.getProduct(1L)).willReturn(product);
            willThrow(new CoreException(ErrorType.BAD_REQUEST, "재고가 부족합니다."))
                .given(productService)
                .deductStock(1L, 2);

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                orderFacade.placeOrder(
                    "testuser",
                    "Test1234!",
                    List.of(new OrderFacade.PlaceOrderItem(1L, 2))
                );
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            verify(orderService, never()).placeOrder(any(), any());
        }
    }

    @DisplayName("내 주문 목록 조회 시, ")
    @Nested
    class GetMyOrders {

        @DisplayName("정상적인 요청이면, 주문 목록이 반환된다.")
        @Test
        void returnsMyOrders_whenValidRequestIsProvided() {
            // arrange
            UserModel user = UserModel.createWithEncodedPassword(
                "testuser", "encoded", "홍길동",
                LocalDate.of(1990, 1, 15), "test@example.com"
            );
            OrderModel order = new OrderModel(
                0L,
                List.of(new OrderItemModel(1L, "에어맥스", 150000L, 1))
            );
            LocalDate startAt = LocalDate.of(2025, 1, 1);
            LocalDate endAt = LocalDate.of(2025, 12, 31);

            given(userService.getMyInfo("testuser", "Test1234!")).willReturn(user);
            given(orderService.getMyOrders(eq(0L), eq(startAt), eq(endAt))).willReturn(List.of(order));

            // act
            List<OrderSummaryInfo> result = orderFacade.getMyOrders("testuser", "Test1234!", startAt, endAt);

            // assert
            assertAll(
                () -> assertThat(result).hasSize(1),
                () -> assertThat(result.get(0).totalAmount()).isEqualTo(150000L)
            );
        }
    }

    @DisplayName("내 주문 상세 조회 시, ")
    @Nested
    class GetMyOrder {

        @DisplayName("정상적인 요청이면, 주문 상세가 반환된다.")
        @Test
        void returnsMyOrder_whenValidRequestIsProvided() {
            // arrange
            UserModel user = UserModel.createWithEncodedPassword(
                "testuser", "encoded", "홍길동",
                LocalDate.of(1990, 1, 15), "test@example.com"
            );
            OrderModel order = new OrderModel(
                0L,
                List.of(new OrderItemModel(1L, "에어맥스", 150000L, 2))
            );

            given(userService.getMyInfo("testuser", "Test1234!")).willReturn(user);
            given(orderService.getMyOrder(0L, 1L)).willReturn(order);

            // act
            OrderDetailInfo result = orderFacade.getMyOrder("testuser", "Test1234!", 1L);

            // assert
            assertAll(
                () -> assertThat(result.totalAmount()).isEqualTo(300000L),
                () -> assertThat(result.items()).hasSize(1)
            );
        }
    }

    @DisplayName("관리자 주문 목록 조회 시, ")
    @Nested
    class GetAll {

        @DisplayName("정상적인 요청이면, 페이징된 주문 목록이 반환된다.")
        @Test
        void returnsPagedOrders_whenValidRequestIsProvided() {
            // arrange
            Pageable pageable = PageRequest.of(0, 20);
            OrderModel order = new OrderModel(
                1L,
                List.of(new OrderItemModel(1L, "에어맥스", 150000L, 1))
            );
            Page<OrderModel> page = new PageImpl<>(List.of(order), pageable, 1);

            given(orderService.getAll(pageable)).willReturn(page);

            // act
            Page<OrderSummaryInfo> result = orderFacade.getAll(pageable);

            // assert
            assertAll(
                () -> assertThat(result.getTotalElements()).isEqualTo(1),
                () -> assertThat(result.getContent().get(0).totalAmount()).isEqualTo(150000L)
            );
        }
    }

    @DisplayName("관리자 주문 상세 조회 시, ")
    @Nested
    class GetOrder {

        @DisplayName("정상적인 요청이면, 주문 상세가 반환된다.")
        @Test
        void returnsOrder_whenValidRequestIsProvided() {
            // arrange
            OrderModel order = new OrderModel(
                1L,
                List.of(new OrderItemModel(1L, "에어맥스", 150000L, 3))
            );

            given(orderService.getOrder(1L)).willReturn(order);

            // act
            OrderDetailInfo result = orderFacade.getOrder(1L);

            // assert
            assertAll(
                () -> assertThat(result.totalAmount()).isEqualTo(450000L),
                () -> assertThat(result.items()).hasSize(1),
                () -> assertThat(result.items().get(0).productName()).isEqualTo("에어맥스")
            );
        }
    }
}
