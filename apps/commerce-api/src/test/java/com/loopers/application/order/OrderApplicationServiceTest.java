package com.loopers.application.order;

import com.loopers.domain.common.Money;
import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.domain.coupon.IssuedCouponStatus;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.point.UserPoint;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.Stock;
import com.loopers.fake.FakeCouponTemplateRepository;
import com.loopers.fake.FakeIssuedCouponRepository;
import com.loopers.fake.FakeOrderRepository;
import com.loopers.fake.FakeProductRepository;
import com.loopers.fake.FakeUserPointRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("OrderApplicationService 테스트")
class OrderApplicationServiceTest {

    private FakeProductRepository fakeProductRepository;
    private FakeOrderRepository fakeOrderRepository;
    private FakeCouponTemplateRepository fakeCouponTemplateRepository;
    private FakeIssuedCouponRepository fakeIssuedCouponRepository;
    private FakeUserPointRepository fakeUserPointRepository;
    private OrderApplicationService orderApplicationService;

    @BeforeEach
    void setUp() {
        fakeProductRepository = new FakeProductRepository();
        fakeOrderRepository = new FakeOrderRepository();
        fakeCouponTemplateRepository = new FakeCouponTemplateRepository();
        fakeIssuedCouponRepository = new FakeIssuedCouponRepository();
        fakeUserPointRepository = new FakeUserPointRepository();
        orderApplicationService = new OrderApplicationService(
            fakeProductRepository,
            fakeOrderRepository,
            fakeCouponTemplateRepository,
            fakeIssuedCouponRepository,
            fakeUserPointRepository
        );
    }

    private Product createAndSaveProduct(String name, long price, int stock) {
        Product product = Product.create(1L, name, "설명",
            new Money(price), new Stock(stock), "http://image.url");
        return fakeProductRepository.save(product);
    }

    @Nested
    @DisplayName("주문 생성")
    class PlaceOrder {

        @Test
        @DisplayName("성공 - 단일 상품 주문")
        void 단일_상품_주문_성공() {
            // Arrange
            Product product = createAndSaveProduct("테스트 상품", 10000, 100);
            Long userId = 1L;
            List<OrderItemRequest> items = List.of(
                new OrderItemRequest(product.getId(), 2)
            );

            // Act
            OrderResult result = orderApplicationService.placeOrder(userId, items);

            // Assert
            assertThat(result.userId()).isEqualTo(userId);
            assertThat(result.items()).hasSize(1);
            assertThat(result.items().get(0).productId()).isEqualTo(product.getId());
            assertThat(result.items().get(0).quantity()).isEqualTo(2);
            assertThat(result.items().get(0).priceSnapshot()).isEqualTo(10000);
            assertThat(result.totalPrice()).isEqualTo(20000); // 10000 * 2
            assertThat(result.status()).isEqualTo(OrderStatus.CREATED);

            // 재고 차감 확인
            Product updatedProduct = fakeProductRepository.findById(product.getId()).orElseThrow();
            assertThat(updatedProduct.getStock().quantity()).isEqualTo(98); // 100 - 2
        }

        @Test
        @DisplayName("성공 - 복수 상품 주문")
        void 복수_상품_주문_성공() {
            // Arrange
            Product product1 = createAndSaveProduct("상품1", 10000, 100);
            Product product2 = createAndSaveProduct("상품2", 20000, 50);
            Long userId = 1L;
            List<OrderItemRequest> items = List.of(
                new OrderItemRequest(product1.getId(), 2),
                new OrderItemRequest(product2.getId(), 1)
            );

            // Act
            OrderResult result = orderApplicationService.placeOrder(userId, items);

            // Assert
            assertThat(result.items()).hasSize(2);
            assertThat(result.totalPrice()).isEqualTo(40000); // 10000*2 + 20000*1
        }

        @Test
        @DisplayName("실패 - 주문 항목이 비어있는 경우")
        void 주문항목_비어있음_예외() {
            // Arrange
            Long userId = 1L;

            // Act & Assert
            CoreException ex = assertThrows(CoreException.class,
                () -> orderApplicationService.placeOrder(userId, List.of()));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @Test
        @DisplayName("실패 - 주문 항목이 null인 경우")
        void 주문항목_null_예외() {
            // Arrange
            Long userId = 1L;

            // Act & Assert
            CoreException ex = assertThrows(CoreException.class,
                () -> orderApplicationService.placeOrder(userId, null));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @Test
        @DisplayName("실패 - 상품이 존재하지 않는 경우")
        void 상품_미존재_예외() {
            // Arrange
            Long userId = 1L;
            List<OrderItemRequest> items = List.of(
                new OrderItemRequest(999L, 1)
            );

            // Act & Assert
            CoreException ex = assertThrows(CoreException.class,
                () -> orderApplicationService.placeOrder(userId, items));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @Test
        @DisplayName("실패 - 재고 부족")
        void 재고_부족_예외() {
            // Arrange
            Product product = createAndSaveProduct("재고 적은 상품", 10000, 5);
            Long userId = 1L;
            List<OrderItemRequest> items = List.of(
                new OrderItemRequest(product.getId(), 10) // 재고 5인데 10개 주문
            );

            // Act & Assert
            CoreException ex = assertThrows(CoreException.class,
                () -> orderApplicationService.placeOrder(userId, items));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.INSUFFICIENT_STOCK);
        }
    }

    @Nested
    @DisplayName("주문 조회")
    class GetOrder {

        @Test
        @DisplayName("성공 - 주문 ID와 사용자 ID로 조회")
        void 주문_조회_성공() {
            // Arrange
            Product product = createAndSaveProduct("테스트 상품", 10000, 100);
            Long userId = 1L;
            List<OrderItemRequest> items = List.of(new OrderItemRequest(product.getId(), 2));
            OrderResult created = orderApplicationService.placeOrder(userId, items);

            // Act
            OrderResult result = orderApplicationService.getOrder(created.id(), userId);

            // Assert
            assertThat(result.id()).isEqualTo(created.id());
            assertThat(result.userId()).isEqualTo(userId);
        }

        @Test
        @DisplayName("실패 - 주문이 존재하지 않는 경우")
        void 주문_미존재_예외() {
            // Arrange
            Long userId = 1L;
            Long nonExistentOrderId = 999L;

            // Act & Assert
            CoreException ex = assertThrows(CoreException.class,
                () -> orderApplicationService.getOrder(nonExistentOrderId, userId));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @Test
        @DisplayName("실패 - 다른 사용자의 주문 조회 시")
        void 타인_주문_조회_예외() {
            // Arrange
            Product product = createAndSaveProduct("테스트 상품", 10000, 100);
            Long userId = 1L;
            Long otherUserId = 2L;
            List<OrderItemRequest> items = List.of(new OrderItemRequest(product.getId(), 2));
            OrderResult created = orderApplicationService.placeOrder(userId, items);

            // Act & Assert
            CoreException ex = assertThrows(CoreException.class,
                () -> orderApplicationService.getOrder(created.id(), otherUserId));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("주문 목록 조회")
    class GetOrders {

        @Test
        @DisplayName("성공 - 사용자의 주문 목록 조회")
        void 주문_목록_조회_성공() {
            // Arrange
            Product product = createAndSaveProduct("테스트 상품", 10000, 100);
            Long userId = 1L;
            orderApplicationService.placeOrder(userId, List.of(new OrderItemRequest(product.getId(), 1)));
            orderApplicationService.placeOrder(userId, List.of(new OrderItemRequest(product.getId(), 2)));

            // Act
            List<OrderResult> results = orderApplicationService.getOrders(userId, 0, 10);

            // Assert
            assertThat(results).hasSize(2);
        }

        @Test
        @DisplayName("성공 - 주문이 없는 경우 빈 목록 반환")
        void 주문_없음_빈목록() {
            // Arrange
            Long userId = 1L;

            // Act
            List<OrderResult> results = orderApplicationService.getOrders(userId, 0, 10);

            // Assert
            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("주문 수 조회")
    class CountOrders {

        @Test
        @DisplayName("성공 - 사용자의 주문 수 조회")
        void 주문_수_조회_성공() {
            // Arrange
            Product product = createAndSaveProduct("테스트 상품", 10000, 100);
            Long userId = 1L;
            orderApplicationService.placeOrder(userId, List.of(new OrderItemRequest(product.getId(), 1)));
            orderApplicationService.placeOrder(userId, List.of(new OrderItemRequest(product.getId(), 2)));

            // Act
            long count = orderApplicationService.countOrders(userId);

            // Assert
            assertThat(count).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("쿠폰/포인트 할인 주문")
    class PlaceOrderWithDiscount {

        @Test
        @DisplayName("성공 - 쿠폰만 적용")
        void 쿠폰만_적용_주문_성공() {
            // Arrange
            Product product = createAndSaveProduct("테스트 상품", 50000, 100);
            Long userId = 1L;

            // 쿠폰 설정
            CouponTemplate template = fakeCouponTemplateRepository.save(
                CouponTemplate.createFixed("5000원 할인", new Money(5000), new Money(10000), 100, ZonedDateTime.now().plusDays(7))
            );
            IssuedCoupon issued = fakeIssuedCouponRepository.save(
                IssuedCoupon.create(userId, template)
            );

            var request = new PlaceOrderWithDiscountRequest(
                List.of(new OrderItemRequest(product.getId(), 1)),
                issued.getId(),
                null
            );

            // Act
            OrderResult result = orderApplicationService.placeOrderWithDiscount(userId, request);

            // Assert
            assertThat(result.originalAmount()).isEqualTo(50000);
            assertThat(result.couponDiscount()).isEqualTo(5000);
            assertThat(result.pointDiscount()).isEqualTo(0);
            assertThat(result.totalPrice()).isEqualTo(45000);
            assertThat(result.couponId()).isEqualTo(issued.getId());

            // 쿠폰 사용 처리 확인
            IssuedCoupon usedCoupon = fakeIssuedCouponRepository.findById(issued.getId()).orElseThrow();
            assertThat(usedCoupon.getStatus()).isEqualTo(IssuedCouponStatus.USED);
        }

        @Test
        @DisplayName("성공 - 포인트만 적용")
        void 포인트만_적용_주문_성공() {
            // Arrange
            Product product = createAndSaveProduct("테스트 상품", 30000, 100);
            Long userId = 1L;

            // 포인트 설정
            fakeUserPointRepository.save(UserPoint.create(userId, 10000));

            var request = new PlaceOrderWithDiscountRequest(
                List.of(new OrderItemRequest(product.getId(), 1)),
                null,
                3000L
            );

            // Act
            OrderResult result = orderApplicationService.placeOrderWithDiscount(userId, request);

            // Assert
            assertThat(result.originalAmount()).isEqualTo(30000);
            assertThat(result.couponDiscount()).isEqualTo(0);
            assertThat(result.pointDiscount()).isEqualTo(3000);
            assertThat(result.totalPrice()).isEqualTo(27000);

            // 포인트 차감 확인
            UserPoint updatedPoint = fakeUserPointRepository.findByUserId(userId).orElseThrow();
            assertThat(updatedPoint.getBalance()).isEqualTo(7000);
        }

        @Test
        @DisplayName("성공 - 쿠폰과 포인트 모두 적용")
        void 쿠폰_포인트_동시_적용_성공() {
            // Arrange
            Product product = createAndSaveProduct("테스트 상품", 50000, 100);
            Long userId = 1L;

            // 쿠폰 설정
            CouponTemplate template = fakeCouponTemplateRepository.save(
                CouponTemplate.createFixed("5000원 할인", new Money(5000), Money.ZERO, 100, ZonedDateTime.now().plusDays(7))
            );
            IssuedCoupon issued = fakeIssuedCouponRepository.save(
                IssuedCoupon.create(userId, template)
            );

            // 포인트 설정
            fakeUserPointRepository.save(UserPoint.create(userId, 10000));

            var request = new PlaceOrderWithDiscountRequest(
                List.of(new OrderItemRequest(product.getId(), 1)),
                issued.getId(),
                2000L
            );

            // Act
            OrderResult result = orderApplicationService.placeOrderWithDiscount(userId, request);

            // Assert
            assertThat(result.originalAmount()).isEqualTo(50000);
            assertThat(result.couponDiscount()).isEqualTo(5000);
            assertThat(result.pointDiscount()).isEqualTo(2000);
            assertThat(result.totalPrice()).isEqualTo(43000);
        }

        @Test
        @DisplayName("실패 - 최소 주문 금액 미달")
        void 최소_주문_금액_미달_예외() {
            // Arrange
            Product product = createAndSaveProduct("저가 상품", 5000, 100);
            Long userId = 1L;

            // 최소 주문 금액 10000원 쿠폰
            CouponTemplate template = fakeCouponTemplateRepository.save(
                CouponTemplate.createFixed("3000원 할인", new Money(3000), new Money(10000), 100, ZonedDateTime.now().plusDays(7))
            );
            IssuedCoupon issued = fakeIssuedCouponRepository.save(
                IssuedCoupon.create(userId, template)
            );

            var request = new PlaceOrderWithDiscountRequest(
                List.of(new OrderItemRequest(product.getId(), 1)),
                issued.getId(),
                null
            );

            // Act & Assert
            CoreException ex = assertThrows(CoreException.class,
                () -> orderApplicationService.placeOrderWithDiscount(userId, request));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.ORDER_AMOUNT_TOO_LOW);
        }

        @Test
        @DisplayName("실패 - 타인 쿠폰 사용 시도")
        void 타인_쿠폰_사용_예외() {
            // Arrange
            Product product = createAndSaveProduct("테스트 상품", 50000, 100);
            Long userId = 1L;
            Long otherUserId = 2L;

            // 다른 사용자의 쿠폰
            CouponTemplate template = fakeCouponTemplateRepository.save(
                CouponTemplate.createFixed("5000원 할인", new Money(5000), Money.ZERO, 100, ZonedDateTime.now().plusDays(7))
            );
            IssuedCoupon issued = fakeIssuedCouponRepository.save(
                IssuedCoupon.create(otherUserId, template)
            );

            var request = new PlaceOrderWithDiscountRequest(
                List.of(new OrderItemRequest(product.getId(), 1)),
                issued.getId(),
                null
            );

            // Act & Assert
            CoreException ex = assertThrows(CoreException.class,
                () -> orderApplicationService.placeOrderWithDiscount(userId, request));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.COUPON_ACCESS_DENIED);
        }

        @Test
        @DisplayName("실패 - 포인트 부족")
        void 포인트_부족_예외() {
            // Arrange
            Product product = createAndSaveProduct("테스트 상품", 30000, 100);
            Long userId = 1L;

            // 포인트 부족
            fakeUserPointRepository.save(UserPoint.create(userId, 1000));

            var request = new PlaceOrderWithDiscountRequest(
                List.of(new OrderItemRequest(product.getId(), 1)),
                null,
                5000L  // 1000원밖에 없는데 5000원 사용 시도
            );

            // Act & Assert
            CoreException ex = assertThrows(CoreException.class,
                () -> orderApplicationService.placeOrderWithDiscount(userId, request));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.INSUFFICIENT_POINT);
        }
    }
}
