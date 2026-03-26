package com.loopers.application.order;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.UserCouponService;
import com.loopers.domain.member.MemberModel;
import com.loopers.domain.member.MemberService;
import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.order.event.OrderCreatedEvent;
import com.loopers.domain.point.PointService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.vo.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderFacadeUnitTest {

    @Mock
    private OrderService orderService;

    @Mock
    private MemberService memberService;

    @Mock
    private ProductService productService;

    @Mock
    private BrandService brandService;

    @Mock
    private PointService pointService;

    @Mock
    private CouponService couponService;

    @Mock
    private UserCouponService userCouponService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private OrderFacade orderFacade;

    @DisplayName("주문을 생성할 때,")
    @Nested
    class CreateOrder {

        @DisplayName("정상 흐름이면, 재고 차감 + 포인트 차감 + 주문 생성이 수행된다.")
        @Test
        void createOrderSuccess() {
            // given
            MemberModel member = new MemberModel("testuser", "password1!@", "홍길동",
                    LocalDate.of(2000, 6, 5), "test@example.com");
            ReflectionTestUtils.setField(member, "id", 1L);

            ProductModel product = new ProductModel(1L, "에어맥스", "러닝화", 129000, 100, null);
            ReflectionTestUtils.setField(product, "id", 10L);

            BrandModel brand = new BrandModel("나이키", "스포츠 브랜드", "https://example.com/nike.png");
            OrderItemModel savedItem = new OrderItemModel(10L, "에어맥스", "나이키", 129000, 2);
            OrderModel order = new OrderModel(1L, List.of(savedItem), null, 0);
            ReflectionTestUtils.setField(order, "id", 100L);

            // when
            when(memberService.getMyInfo("testuser", "password1!@")).thenReturn(member);
            when(productService.getById(10L)).thenReturn(product);
            when(brandService.getById(1L)).thenReturn(brand);

            when(orderService.createOrder(eq(1L), any(), any(), eq(0))).thenReturn(order);
            when(orderService.getOrderItems(100L)).thenReturn(List.of(savedItem));

            List<OrderFacade.OrderItemRequest> requests = List.of(
                    new OrderFacade.OrderItemRequest(10L, 2)
            );

            OrderInfo result = orderFacade.createOrder("testuser", "password1!@", requests, null);

            // then
            assertAll(
                    () -> assertThat(result.totalAmount()).isEqualTo(129000 * 2),
                    () -> assertThat(result.items()).hasSize(1),
                    () -> assertThat(result.items().get(0).productName()).isEqualTo("에어맥스"),
                    () -> assertThat(result.items().get(0).brandName()).isEqualTo("나이키")
            );

            verify(productService).decreaseStockWithLock(10L, 2);
            verify(pointService).use(eq(1L), eq(Money.of(129000 * 2)));
        }

        @DisplayName("포인트가 부족하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void failWithInsufficientPoint() {
            // given
            MemberModel member = new MemberModel("testuser", "password1!@", "홍길동",
                    LocalDate.of(2000, 6, 5), "test@example.com");
            ReflectionTestUtils.setField(member, "id", 1L);

            ProductModel product = new ProductModel(1L, "에어맥스", "러닝화", 129000, 100, null);
            ReflectionTestUtils.setField(product, "id", 10L);

            BrandModel brand = new BrandModel("나이키", "스포츠 브랜드", "https://example.com/nike.png");
            OrderItemModel savedItem = new OrderItemModel(10L, "에어맥스", "나이키", 129000, 2);
            OrderModel order = new OrderModel(1L, List.of(savedItem), null, 0);
            ReflectionTestUtils.setField(order, "id", 100L);

            when(memberService.getMyInfo("testuser", "password1!@")).thenReturn(member);
            when(productService.getById(10L)).thenReturn(product);
            when(brandService.getById(1L)).thenReturn(brand);
            when(orderService.createOrder(eq(1L), any(), any(), eq(0))).thenReturn(order);
            doThrow(new CoreException(ErrorType.BAD_REQUEST, "포인트가 부족합니다."))
                    .when(pointService).use(eq(1L), eq(Money.of(129000 * 2)));

            List<OrderFacade.OrderItemRequest> requests = List.of(
                    new OrderFacade.OrderItemRequest(10L, 2)
            );

            // when
            CoreException result = assertThrows(CoreException.class, () ->
                    orderFacade.createOrder("testuser", "password1!@", requests, null)
            );

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("재고가 부족하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void failWithInsufficientStock() {
            // given
            MemberModel member = new MemberModel("testuser", "password1!@", "홍길동",
                    LocalDate.of(2000, 6, 5), "test@example.com");
            ReflectionTestUtils.setField(member, "id", 1L);

            ProductModel product = new ProductModel(1L, "에어맥스", "러닝화", 129000, 2, null);
            ReflectionTestUtils.setField(product, "id", 10L);

            when(memberService.getMyInfo("testuser", "password1!@")).thenReturn(member);
            when(productService.getById(10L)).thenReturn(product);
            doThrow(new CoreException(ErrorType.BAD_REQUEST, "재고가 부족합니다."))
                    .when(productService).decreaseStockWithLock(10L, 10);

            List<OrderFacade.OrderItemRequest> requests = List.of(
                    new OrderFacade.OrderItemRequest(10L, 10)
            );


            // when
            CoreException result = assertThrows(CoreException.class, () ->
                    orderFacade.createOrder("testuser", "password1!@", requests, null)
            );

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("정상 흐름이면, 주문이 생성되고 OrderCreatedEvent가 발행된다.")
        @Test
        void createOrderPublishesEvent() {
            // given
            MemberModel member = new MemberModel("testuser", "password1!@", "홍길동",
                    LocalDate.of(2000, 6, 5), "test@example.com");
            ReflectionTestUtils.setField(member, "id", 1L);

            ProductModel product = new ProductModel(1L, "에어맥스", "러닝화", 129000, 100, null);
            ReflectionTestUtils.setField(product, "id", 10L);

            BrandModel brand = new BrandModel("나이키", "스포츠 브랜드", "https://example.com/nike.png");
            OrderItemModel savedItem = new OrderItemModel(10L, "에어맥스", "나이키", 129000, 2);
            OrderModel order = new OrderModel(1L, List.of(savedItem), null, 0);
            ReflectionTestUtils.setField(order, "id", 100L);

            when(memberService.getMyInfo("testuser", "password1!@")).thenReturn(member);
            when(productService.getById(10L)).thenReturn(product);
            when(brandService.getById(1L)).thenReturn(brand);
            when(orderService.createOrder(eq(1L), any(), any(), eq(0))).thenReturn(order);
            when(orderService.getOrderItems(100L)).thenReturn(List.of(savedItem));

            List<OrderFacade.OrderItemRequest> requests = List.of(
                    new OrderFacade.OrderItemRequest(10L, 2)
            );

            // when
            orderFacade.createOrder("testuser", "password1!@", requests, null);

            // then
            ArgumentCaptor<OrderCreatedEvent> captor =
                    ArgumentCaptor.forClass(OrderCreatedEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());
            assertThat(captor.getValue().orderId()).isEqualTo(100L);
            assertThat(captor.getValue().memberId()).isEqualTo(1L);
        }
    }

    @DisplayName("주문을 단건 조회할 때,")
    @Nested
    class GetById {

        @DisplayName("존재하는 주문이면, OrderInfo가 반환된다.")
        @Test
        void getByIdSuccess() {
            // given
            OrderItemModel item = new OrderItemModel(10L, "에어맥스", "나이키", 129000, 2);
            OrderModel order = new OrderModel(1L, List.of(item), null, 0);
            ReflectionTestUtils.setField(order, "id", 100L);

            when(orderService.getById(100L)).thenReturn(order);
            when(orderService.getOrderItems(100L)).thenReturn(List.of(item));

            // when
            OrderInfo result = orderFacade.getById(100L);

            // then
            assertAll(
                    () -> assertThat(result.id()).isEqualTo(100L),
                    () -> assertThat(result.memberId()).isEqualTo(1L),
                    () -> assertThat(result.totalAmount()).isEqualTo(129000 * 2),
                    () -> assertThat(result.items()).hasSize(1)
            );
        }

        @DisplayName("존재하지 않는 주문이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void failWithNotFound() {
            // given
            when(orderService.getById(999L))
                    .thenThrow(new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 주문입니다."));

            // when
            CoreException result = assertThrows(CoreException.class, () ->
                    orderFacade.getById(999L)
            );

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("내 주문 목록을 조회할 때,")
    @Nested
    class GetByMember {

        @DisplayName("정상 흐름이면, 해당 회원의 주문 목록이 반환된다.")
        @Test
        void getByMemberSuccess() {
            // given
            MemberModel member = new MemberModel("testuser", "password1!@", "홍길동",
                    LocalDate.of(2000, 6, 5), "test@example.com");
            ReflectionTestUtils.setField(member, "id", 1L);

            OrderItemModel item1 = new OrderItemModel(10L, "에어맥스", "나이키", 129000, 1);
            OrderModel order1 = new OrderModel(1L, List.of(item1), null, 0);
            ReflectionTestUtils.setField(order1, "id", 100L);

            OrderItemModel item2 = new OrderItemModel(20L, "슈퍼스타", "아디다스", 99000, 2);
            OrderModel order2 = new OrderModel(1L, List.of(item2), null, 0);
            ReflectionTestUtils.setField(order2, "id", 101L);

            when(memberService.getMyInfo("testuser", "password1!@")).thenReturn(member);
            when(orderService.getByMemberId(1L)).thenReturn(List.of(order1, order2));
            when(orderService.getOrderItems(100L)).thenReturn(List.of(item1));
            when(orderService.getOrderItems(101L)).thenReturn(List.of(item2));

            // when
            List<OrderInfo> result = orderFacade.getByMember("testuser", "password1!@");

            // then
            assertAll(
                    () -> assertThat(result).hasSize(2),
                    () -> assertThat(result.get(0).id()).isEqualTo(100L),
                    () -> assertThat(result.get(1).id()).isEqualTo(101L)
            );
        }

        @DisplayName("주문이 없으면, 빈 목록이 반환된다.")
        @Test
        void emptyList() {
            // given
            MemberModel member = new MemberModel("testuser", "password1!@", "홍길동",
                    LocalDate.of(2000, 6, 5), "test@example.com");
            ReflectionTestUtils.setField(member, "id", 1L);

            when(memberService.getMyInfo("testuser", "password1!@")).thenReturn(member);
            when(orderService.getByMemberId(1L)).thenReturn(List.of());

            // when
            List<OrderInfo> result = orderFacade.getByMember("testuser", "password1!@");

            // then
            assertThat(result).isEmpty();
        }
    }
}
