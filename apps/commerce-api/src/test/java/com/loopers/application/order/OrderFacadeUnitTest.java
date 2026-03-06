package com.loopers.application.order;

import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.DiscountType;
import com.loopers.domain.coupon.UserCoupon;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.product.Product;
import com.loopers.domain.user.User;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderFacadeUnitTest {

    private OrderFacade orderFacade;
    private FakeProductRepository fakeProductRepository;
    private FakeUserRepository fakeUserRepository;
    private FakeUserCouponRepository fakeUserCouponRepository;
    private FakeCouponTemplateRepository fakeCouponTemplateRepository;

    @BeforeEach
    void setUp() {
        fakeProductRepository = new FakeProductRepository();
        fakeUserRepository = new FakeUserRepository();
        fakeUserCouponRepository = new FakeUserCouponRepository();
        fakeCouponTemplateRepository = new FakeCouponTemplateRepository();
        com.loopers.domain.order.OrderRepository fakeOrderRepository =
            new com.loopers.domain.order.FakeOrderRepository();
        OrderService orderService = new OrderService(fakeOrderRepository);
        orderFacade = new OrderFacade(orderService, fakeProductRepository, fakeUserRepository,
            fakeUserCouponRepository, fakeCouponTemplateRepository);
    }

    @DisplayName("주문을 생성할 때,")
    @Nested
    class CreateOrder {

        @DisplayName("정상 요청이면, 주문이 생성되고 재고/포인트가 차감된다.")
        @Test
        void createsOrder_whenValidRequest() {
            // arrange
            Product product = new Product(1L, "상품A", 10000L, "설명", 10);
            fakeProductRepository.addProductWithId(1L, product);

            User user = new User("테스트유저", 100000L);
            fakeUserRepository.addUserWithId(1L, user);

            List<OrderFacade.OrderItemRequest> items = List.of(
                new OrderFacade.OrderItemRequest(1L, 2)
            );

            // act
            OrderInfo result = orderFacade.createOrder(1L, items);

            // assert
            assertAll(
                () -> assertThat(result).isNotNull(),
                () -> assertThat(result.userId()).isEqualTo(1L),
                () -> assertThat(result.totalPrice()).isEqualTo(20000L),
                () -> assertThat(result.items()).hasSize(1),
                () -> assertThat(result.items().get(0).productName()).isEqualTo("상품A"),
                () -> assertThat(result.items().get(0).quantity()).isEqualTo(2)
            );

            // 재고 차감 확인
            assertThat(product.getStockQuantity().value()).isEqualTo(8);
            // 포인트 차감 확인
            assertThat(user.getPoint()).isEqualTo(80000L);
        }

        @DisplayName("재고가 부족하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsException_whenStockInsufficient() {
            // arrange
            Product product = new Product(1L, "상품A", 10000L, "설명", 1);
            fakeProductRepository.addProductWithId(1L, product);

            User user = new User("테스트유저", 100000L);
            fakeUserRepository.addUserWithId(1L, user);

            List<OrderFacade.OrderItemRequest> items = List.of(
                new OrderFacade.OrderItemRequest(1L, 5)
            );

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                orderFacade.createOrder(1L, items));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            assertThat(result.getMessage()).contains("재고");
        }

        @DisplayName("포인트가 부족하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsException_whenPointInsufficient() {
            // arrange
            Product product = new Product(1L, "상품A", 50000L, "설명", 10);
            fakeProductRepository.addProductWithId(1L, product);

            User user = new User("테스트유저", 10000L);
            fakeUserRepository.addUserWithId(1L, user);

            List<OrderFacade.OrderItemRequest> items = List.of(
                new OrderFacade.OrderItemRequest(1L, 2)
            );

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                orderFacade.createOrder(1L, items));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            assertThat(result.getMessage()).contains("포인트");
        }
    }

    @DisplayName("쿠폰 적용 주문을 생성할 때,")
    @Nested
    class CreateOrderWithCoupon {

        @DisplayName("정상 쿠폰을 적용하면, 할인 금액이 반영되고 최종 금액 기준으로 포인트가 차감된다.")
        @Test
        void createsOrder_whenValidCouponApplied() {
            // arrange
            Product product = new Product(1L, "상품A", 10000L, "설명", 10);
            fakeProductRepository.addProductWithId(1L, product);

            User user = new User("테스트유저", 100000L);
            fakeUserRepository.addUserWithId(1L, user);

            CouponTemplate template = new CouponTemplate("1000원 할인", DiscountType.FIXED, 1000L, 0L, null, 30);
            fakeCouponTemplateRepository.addWithId(100L, template);

            UserCoupon coupon = new UserCoupon(1L, 100L, 30);
            fakeUserCouponRepository.addWithId(10L, coupon);

            List<OrderFacade.OrderItemRequest> items = List.of(
                new OrderFacade.OrderItemRequest(1L, 2)
            );

            // act
            OrderInfo result = orderFacade.createOrder(1L, 10L, items);

            // assert
            assertAll(
                () -> assertThat(result.totalPrice()).as("총 주문 금액").isEqualTo(20000L),
                () -> assertThat(result.discountAmount()).as("할인 금액").isEqualTo(1000L),
                () -> assertThat(result.finalPrice()).as("최종 결제 금액").isEqualTo(19000L),
                () -> assertThat(user.getPoint()).as("포인트 차감 (최종 금액 기준)").isEqualTo(81000L),
                () -> assertThat(coupon.getStatus()).as("쿠폰 상태").isEqualTo(CouponStatus.USED),
                () -> assertThat(product.getStockQuantity().value()).as("재고 차감").isEqualTo(8)
            );
        }
    }

    @DisplayName("이미 사용된 쿠폰으로 주문할 때,")
    @Nested
    class CreateOrderWithUsedCoupon {

        @DisplayName("CONFLICT 예외가 발생하고, 주문이 생성되지 않는다.")
        @Test
        void throwsException_whenCouponAlreadyUsed() {
            // arrange
            Product product = new Product(1L, "상품A", 10000L, "설명", 10);
            fakeProductRepository.addProductWithId(1L, product);

            User user = new User("테스트유저", 100000L);
            fakeUserRepository.addUserWithId(1L, user);

            CouponTemplate template = new CouponTemplate("1000원 할인", DiscountType.FIXED, 1000L, 0L, null, 30);
            fakeCouponTemplateRepository.addWithId(100L, template);

            UserCoupon coupon = new UserCoupon(1L, 100L, 30);
            coupon.use(); // 이미 사용 처리
            fakeUserCouponRepository.addWithId(10L, coupon);

            List<OrderFacade.OrderItemRequest> items = List.of(
                new OrderFacade.OrderItemRequest(1L, 1)
            );

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                orderFacade.createOrder(1L, 10L, items));

            // assert
            // 단위 테스트에서는 @Transactional 롤백이 없으므로 재고는 이미 차감된 상태
            // 실제 트랜잭션 환경에서의 롤백 검증은 OrderFacadeRollbackTest에서 수행
            assertAll(
                () -> assertThat(result.getErrorType()).as("에러 타입").isEqualTo(ErrorType.CONFLICT),
                () -> assertThat(result.getMessage()).as("에러 메시지").contains("이미 사용된 쿠폰"),
                () -> assertThat(user.getPoint()).as("포인트 미차감").isEqualTo(100000L)
            );
        }
    }

    @DisplayName("최소 주문 금액 미달로 쿠폰을 적용할 때,")
    @Nested
    class CreateOrderWithCouponMinOrderNotMet {

        @DisplayName("BAD_REQUEST 예외가 발생하고, 주문이 실패한다.")
        @Test
        void throwsException_whenMinOrderAmountNotMet() {
            // arrange
            Product product = new Product(1L, "상품A", 5000L, "설명", 10);
            fakeProductRepository.addProductWithId(1L, product);

            User user = new User("테스트유저", 100000L);
            fakeUserRepository.addUserWithId(1L, user);

            CouponTemplate template = new CouponTemplate(
                "최소 50000원 이상 주문 시 할인", DiscountType.FIXED, 3000L, 50000L, null, 30
            );
            fakeCouponTemplateRepository.addWithId(100L, template);

            UserCoupon coupon = new UserCoupon(1L, 100L, 30);
            fakeUserCouponRepository.addWithId(10L, coupon);

            List<OrderFacade.OrderItemRequest> items = List.of(
                new OrderFacade.OrderItemRequest(1L, 1) // 5000원 < 최소 50000원
            );

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                orderFacade.createOrder(1L, 10L, items));

            // assert
            assertAll(
                () -> assertThat(result.getErrorType()).as("에러 타입").isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(result.getMessage()).as("에러 메시지").contains("최소 주문 금액"),
                () -> assertThat(user.getPoint()).as("포인트 미차감").isEqualTo(100000L)
            );
        }
    }
}
