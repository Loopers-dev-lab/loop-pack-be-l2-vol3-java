package com.loopers.application.order;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.coupon.UserCoupon;
import com.loopers.domain.coupon.UserCouponRepository;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.StockPolicy;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.vo.Price;
import com.loopers.domain.product.vo.Stock;
import com.loopers.domain.user.User;
import com.loopers.domain.user.UserRepository;
import com.loopers.domain.user.vo.Name;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.support.page.PageResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderFacadeTest {

    UserRepository userRepository = mock(UserRepository.class);
    ProductRepository productRepository = mock(ProductRepository.class);
    BrandRepository brandRepository = mock(BrandRepository.class);
    OrderRepository orderRepository = mock(OrderRepository.class);
    UserCouponRepository userCouponRepository = mock(UserCouponRepository.class);
    StockPolicy stockPolicy = new StockPolicy();
    OrderAssembler orderAssembler = new OrderAssembler();

    OrderFacade orderFacade = new OrderFacade(userRepository, productRepository, brandRepository, orderRepository, userCouponRepository, stockPolicy, orderAssembler);

    @DisplayName("주문 생성 시, ")
    @Nested
    class CreateOrder {

        @DisplayName("유효한 사용자와 상품이면, 재고가 차감되고 orderId 를 반환한다.")
        @Test
        void returnsOrderId_andDecreasesStock_whenValidUserAndProducts() {
            // arrange
            Long userId = 1L;
            Long productId = 0L; // BaseEntity id = 0L
            Long brandId = 0L;   // BaseEntity id = 0L

            User user = mock(User.class);
            Brand brand = mock(Brand.class);
            when(brand.getId()).thenReturn(brandId);
            when(brand.name()).thenReturn("나이키");

            Product product = Product.of("나이키 에어맥스", "설명", Stock.from(10), Price.from(150000), brandId);

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(productRepository.findAllByIdInWithLock(List.of(productId))).thenReturn(List.of(product));
            when(brandRepository.findAllByIdIn(any())).thenReturn(List.of(brand));
            when(orderRepository.save(any())).thenReturn(1L);

            OrderCommand command = new OrderCommand(List.of(new OrderCommand.Item(productId, 3)), null);

            // act
            String orderId = orderFacade.createOrder(userId, command);

            // assert
            assertThat(orderId).isNotBlank();
            assertThat(product.stock().value()).isEqualTo(7); // 10 - 3
        }

        @DisplayName("존재하지 않는 사용자이면, CoreException 이 발생한다.")
        @Test
        void throwsCoreException_whenUserNotFound() {
            // arrange
            Long userId = 999L;
            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            OrderCommand command = new OrderCommand(List.of(new OrderCommand.Item(1L, 1)), null);

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                orderFacade.createOrder(userId, command)
            );

            // assert
            assertThat(result.getCustomMessage()).isEqualTo("사용자 정보를 찾을 수 없습니다.");
        }

        @DisplayName("존재하지 않는 상품이 포함된 경우, CoreException 이 발생한다.")
        @Test
        void throwsCoreException_whenProductNotFound() {
            // arrange
            Long userId = 1L;
            User user = mock(User.class);
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(productRepository.findAllByIdInWithLock(List.of(999L))).thenReturn(List.of());

            OrderCommand command = new OrderCommand(List.of(new OrderCommand.Item(999L, 1)), null);

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                orderFacade.createOrder(userId, command)
            );

            // assert
            assertThat(result.getCustomMessage()).isEqualTo("등록되지 않은 상품입니다.");
        }

        @DisplayName("재고가 부족한 상품이 있으면, CoreException 이 발생한다.")
        @Test
        void throwsCoreException_whenStockIsInsufficient() {
            // arrange
            Long userId = 1L;
            Long productId = 0L; // BaseEntity id = 0L

            User user = mock(User.class);
            Product product = Product.of("나이키 에어맥스", "설명", Stock.from(2), Price.from(150000), 1L);

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(productRepository.findAllByIdInWithLock(List.of(productId))).thenReturn(List.of(product));

            OrderCommand command = new OrderCommand(List.of(new OrderCommand.Item(productId, 5)), null);

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                orderFacade.createOrder(userId, command)
            );

            // assert
            assertThat(result.getCustomMessage()).contains("재고 부족:\n상품: 나이키 에어맥스, 요청: 5, 재고: 2");
        }

        @DisplayName("유효한 쿠폰을 포함해 주문하면, 할인이 적용된 주문이 생성된다.")
        @Test
        void createsOrderWithDiscount_whenValidCouponProvided() {
            // arrange
            Long userId = 1L;
            Long productId = 0L;
            Long brandId = 0L;
            Long userCouponId = 10L;

            User user = mock(User.class);
            when(user.getId()).thenReturn(userId);

            Brand brand = mock(Brand.class);
            when(brand.getId()).thenReturn(brandId);
            when(brand.name()).thenReturn("나이키");

            Product product = Product.of("나이키 에어맥스", "설명", Stock.from(10), Price.from(100000), brandId);

            UserCoupon userCoupon = mock(UserCoupon.class);
            when(userCoupon.userId()).thenReturn(userId);
            when(userCoupon.calculateDiscount(100000L)).thenReturn(10000L); // 10% 할인

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(productRepository.findAllByIdInWithLock(List.of(productId))).thenReturn(List.of(product));
            when(brandRepository.findAllByIdIn(any())).thenReturn(List.of(brand));
            when(userCouponRepository.findById(userCouponId)).thenReturn(Optional.of(userCoupon));
            when(orderRepository.save(any())).thenAnswer(invocation -> {
                Order order = invocation.getArgument(0);
                assertThat(order.discountAmount()).isEqualTo(10000L);
                assertThat(order.paymentAmount()).isEqualTo(90000L);
                return 1L;
            });

            OrderCommand command = new OrderCommand(List.of(new OrderCommand.Item(productId, 1)), userCouponId);

            // act
            String orderId = orderFacade.createOrder(userId, command);

            // assert
            assertThat(orderId).isNotBlank();
        }

        @DisplayName("존재하지 않는 쿠폰 ID를 사용하면, CoreException 이 발생한다.")
        @Test
        void throwsCoreException_whenUserCouponNotFound() {
            // arrange
            Long userId = 1L;
            Long productId = 0L;
            Long userCouponId = 999L;

            User user = mock(User.class);
            Product product = Product.of("나이키 에어맥스", "설명", Stock.from(10), Price.from(100000), 0L);

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(productRepository.findAllByIdInWithLock(List.of(productId))).thenReturn(List.of(product));
            when(userCouponRepository.findById(userCouponId)).thenReturn(Optional.empty());

            OrderCommand command = new OrderCommand(List.of(new OrderCommand.Item(productId, 1)), userCouponId);

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                orderFacade.createOrder(userId, command)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }


    }

    @DisplayName("단일 주문 상세 조회 시, ")
    @Nested
    class GetDetail {

        @DisplayName("본인 주문이면, 주문 상세를 반환한다.")
        @Test
        void returnsOrderDetail_whenOwner() {
            // arrange
            Long userId = 1L;
            String orderId = "20260318-BASQZX";

            Order order = mock(Order.class);
            when(order.orderId()).thenReturn(orderId);
            when(order.isOwnedBy(userId)).thenReturn(true);

            when(orderRepository.findByOrderId(orderId)).thenReturn(Optional.of(order));

            // act
            OrderDetail result = orderFacade.getDetail(userId, orderId);

            // assert
            assertThat(result).isNotNull();
            assertThat(result.orderId()).isNotNull();
        }

        @DisplayName("존재하지 않는 주문이면, CoreException 이 발생한다.")
        @Test
        void throwsCoreException_whenOrderNotFound() {
            // arrange
            Long userId = 1L;
            String orderId = "20260318-BASQZX";

            when(orderRepository.findByOrderId(orderId)).thenReturn(Optional.empty());

            // act
            CoreException result = assertThrows(CoreException.class,
                    () -> orderFacade.getDetail(userId, orderId));

            // assert
            assertThat(result.getCustomMessage()).isEqualTo("존재하지 않는 주문입니다.");
        }

        @DisplayName("다른 사용자의 주문이면, CoreException 이 발생한다.")
        @Test
        void throwsCoreException_whenNotOwner() {
            // arrange
            Long userId = 1L;
            String orderId = "20260318-BASQZX";

            Order order = mock(Order.class);
            when(order.isOwnedBy(userId)).thenReturn(false);

            when(orderRepository.findByOrderId(orderId)).thenReturn(Optional.of(order));

            // act
            CoreException result = assertThrows(CoreException.class,
                    () -> orderFacade.getDetail(userId, orderId));

            // assert
            assertThat(result.getCustomMessage()).isEqualTo("접근 권한이 없습니다.");
        }
    }

    @DisplayName("관리자 단일 주문 상세 조회 시, ")
    @Nested
    class GetAdminDetail {

        @DisplayName("존재하는 주문이면, 주문자 정보를 포함한 주문 상세를 반환한다.")
        @Test
        void returnsAdminOrderDetail_whenOrderExists() {
            // arrange
            Long userId = 1L;
            Long orderId = 10L;

            Name name = mock(Name.class);
            when(name.value()).thenReturn("홍길동");

            User user = mock(User.class);
            when(user.getId()).thenReturn(userId);
            when(user.name()).thenReturn(name);

            Order order = mock(Order.class);
            when(order.getId()).thenReturn(orderId);
            when(order.paymentAmount()).thenReturn(150000L);
            when(order.items()).thenReturn(List.of());
            when(order.userId()).thenReturn(userId);

            when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            // act
            OrderAdminDetail result = orderFacade.getAdminDetail(orderId);

            // assert
            assertThat(result.orderId()).isEqualTo(orderId);
            assertThat(result.totalPrice()).isEqualTo(150000L);
            assertThat(result.userId()).isEqualTo(userId);
            assertThat(result.userName()).isEqualTo("홍길동");
        }

        @DisplayName("존재하지 않는 주문이면, CoreException 이 발생한다.")
        @Test
        void throwsCoreException_whenOrderNotFound() {
            // arrange
            Long orderId = 999L;
            when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

            // act
            CoreException result = assertThrows(CoreException.class,
                    () -> orderFacade.getAdminDetail(orderId));

            // assert
            assertThat(result.getCustomMessage()).isEqualTo("존재하지 않는 주문입니다.");
        }
    }

    @DisplayName("관리자 주문 목록 조회 시, ")
    @Nested
    class GetAdminList {

        @DisplayName("유효한 페이지 요청이면, 주문자 정보를 포함한 주문 목록을 반환한다.")
        @Test
        void returnsAdminOrderSummaryList_whenValidPageRequest() {
            // arrange
            Long userId = 1L;
            PageRequest pageRequest = PageRequest.of(0, 20);

            Name name = mock(Name.class);
            when(name.value()).thenReturn("홍길동");

            User user = mock(User.class);
            when(user.getId()).thenReturn(userId);
            when(user.name()).thenReturn(name);

            Order order = mock(Order.class);
            when(order.getId()).thenReturn(1L);
            when(order.getCreatedAt()).thenReturn(ZonedDateTime.now());
            when(order.paymentAmount()).thenReturn(150000L);
            when(order.itemCount()).thenReturn(1);
            when(order.userId()).thenReturn(userId);

            when(orderRepository.findAll(pageRequest)).thenReturn(new PageResponse<>(List.of(order), 0, 20, 1));
            when(userRepository.findAllByIdIn(Set.of(userId))).thenReturn(List.of(user));

            // act
            PageResponse<OrderAdminSummary> result = orderFacade.findAllOrders(pageRequest);

            // assert
            assertThat(result.content()).hasSize(1);
            assertThat(result.content().get(0).orderId()).isEqualTo(1L);
            assertThat(result.content().get(0).totalPrice()).isEqualTo(150000L);
            assertThat(result.content().get(0).userId()).isEqualTo(userId);
            assertThat(result.content().get(0).userName()).isEqualTo("홍길동");
        }
    }

    @DisplayName("주문 목록 조회 시, ")
    @Nested
    class GetList {

        @DisplayName("유효한 기간이면, 주문 요약 목록을 반환한다.")
        @Test
        void returnsOrderSummaryList_whenValidPeriod() {
            // arrange
            Long userId = 1L;
            ZonedDateTime startAt = ZonedDateTime.now().minusDays(7);
            ZonedDateTime endAt = ZonedDateTime.now();

            Order order = mock(Order.class);
            when(order.getId()).thenReturn(1L);
            when(order.orderId()).thenReturn("20260318-APZHCS");
            when(order.getCreatedAt()).thenReturn(ZonedDateTime.now());
            when(order.paymentAmount()).thenReturn(150000L);
            when(order.itemCount()).thenReturn(2);

            when(orderRepository.findAllByUserIdAndCreatedAtBetween(userId, startAt, endAt))
                    .thenReturn(List.of(order));

            // act
            List<OrderSummary> result = orderFacade.getList(userId, startAt, endAt);

            // assert
            assertThat(result).hasSize(1);
            assertThat(result.get(0).orderId()).isEqualTo("20260318-APZHCS");
            assertThat(result.get(0).totalPrice()).isEqualTo(150000L);
            assertThat(result.get(0).itemCount()).isEqualTo(2);
        }
    }
}
