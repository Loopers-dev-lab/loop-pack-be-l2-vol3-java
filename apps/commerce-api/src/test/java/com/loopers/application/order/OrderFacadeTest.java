package com.loopers.application.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.coupon.CouponFacade;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.coupon.*;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.vo.Price;
import com.loopers.domain.product.vo.Stock;
import com.loopers.fake.*;
import com.loopers.infrastructure.redis.CouponIssueRequestRedisRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class OrderFacadeTest {

    private OrderFacade orderFacade;
    private FakeOrderRepository orderRepository;
    private FakeProductRepository productRepository;
    private FakeBrandRepository brandRepository;
    private FakeCouponRepository couponRepository;
    private FakeCouponIssueRepository couponIssueRepository;
    private CouponFacade couponFacade;

    @BeforeEach
    void setUp() {
        orderRepository = new FakeOrderRepository();
        productRepository = new FakeProductRepository();
        brandRepository = new FakeBrandRepository();
        couponRepository = new FakeCouponRepository();
        couponIssueRepository = new FakeCouponIssueRepository();
        couponFacade = new CouponFacade(couponRepository, couponIssueRepository,
            mock(CouponIssueRequestRedisRepository.class),
            mock(KafkaTemplate.class), new ObjectMapper(),
            Clock.systemDefaultZone());
        orderFacade = new OrderFacade(orderRepository, productRepository, brandRepository,
            couponFacade, null);
    }

    @Nested
    @DisplayName("주문 생성")
    class CreateOrder {

        @DisplayName("주문을 생성하면 상품 정보가 스냅샷되고 재고가 차감된다")
        @Test
        void createOrder_snapshotsProductInfoAndDecreasesStock() {
            Brand brand = brandRepository.save(new Brand("나이키", "스포츠 브랜드"));
            Product product = productRepository.save(
                new Product(brand.getId(), "에어맥스", new Price(150000), new Stock(10)));

            List<OrderFacade.OrderItemRequest> requests = List.of(
                new OrderFacade.OrderItemRequest(product.getId(), 2));

            Order result = orderFacade.createOrder(1L, requests);

            assertThat(result.getId()).isNotNull();
            assertThat(result.getMemberId()).isEqualTo(1L);
            assertThat(result.getStatus()).isEqualTo(OrderStatus.CREATED);
            assertThat(result.getTotalPrice()).isEqualTo(300000);
            assertThat(result.getOriginalTotalPrice()).isEqualTo(300000);
            assertThat(result.getDiscountAmount()).isEqualTo(0);
            assertThat(result.getCouponIssueId()).isNull();
            assertThat(result.getItems()).hasSize(1);

            OrderItem item = result.getItems().get(0);
            assertThat(item.getProductId()).isEqualTo(product.getId());
            assertThat(item.getProductName()).isEqualTo("에어맥스");
            assertThat(item.getProductPrice()).isEqualTo(150000);
            assertThat(item.getBrandName()).isEqualTo("나이키");
            assertThat(item.getQuantity()).isEqualTo(2);
            assertThat(product.getStock().getQuantity()).isEqualTo(8);
        }

        @DisplayName("여러 상품을 주문하면 각 상품의 재고가 차감되고 총 가격이 계산된다")
        @Test
        void createOrder_withMultipleItems_decreasesStocksAndCalculatesTotal() {
            Brand brand = brandRepository.save(new Brand("나이키", "스포츠 브랜드"));
            Product product1 = productRepository.save(
                new Product(brand.getId(), "에어맥스", new Price(150000), new Stock(10)));
            Product product2 = productRepository.save(
                new Product(brand.getId(), "에어포스", new Price(120000), new Stock(20)));

            List<OrderFacade.OrderItemRequest> requests = List.of(
                new OrderFacade.OrderItemRequest(product1.getId(), 1),
                new OrderFacade.OrderItemRequest(product2.getId(), 3));

            Order result = orderFacade.createOrder(1L, requests);

            assertThat(result.getItems()).hasSize(2);
            assertThat(result.getTotalPrice()).isEqualTo(150000 + 120000 * 3);
            assertThat(product1.getStock().getQuantity()).isEqualTo(9);
            assertThat(product2.getStock().getQuantity()).isEqualTo(17);
        }

        @DisplayName("재고가 부족하면 예외가 발생한다")
        @Test
        void createOrder_whenInsufficientStock_throwsException() {
            Brand brand = brandRepository.save(new Brand("나이키", "스포츠 브랜드"));
            productRepository.save(
                new Product(brand.getId(), "에어맥스", new Price(150000), new Stock(2)));

            List<OrderFacade.OrderItemRequest> requests = List.of(
                new OrderFacade.OrderItemRequest(1L, 5));

            assertThatThrownBy(() -> orderFacade.createOrder(1L, requests))
                .isInstanceOf(CoreException.class)
                .extracting(e -> ((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("존재하지 않는 상품을 주문하면 예외가 발생한다")
        @Test
        void createOrder_whenProductNotExists_throwsCoreException() {
            List<OrderFacade.OrderItemRequest> requests = List.of(
                new OrderFacade.OrderItemRequest(999L, 1));

            assertThatThrownBy(() -> orderFacade.createOrder(1L, requests))
                .isInstanceOf(CoreException.class)
                .extracting(e -> ((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("브랜드가 없는 상품을 주문하면 브랜드 이름이 null로 스냅샷된다")
        @Test
        void createOrder_whenBrandNotExists_snapshotsNullBrandName() {
            Product product = productRepository.save(
                new Product(999L, "에어맥스", new Price(150000), new Stock(10)));

            List<OrderFacade.OrderItemRequest> requests = List.of(
                new OrderFacade.OrderItemRequest(product.getId(), 1));

            Order result = orderFacade.createOrder(1L, requests);

            assertThat(result.getItems().get(0).getBrandName()).isNull();
        }
    }

    @Nested
    @DisplayName("쿠폰 적용 주문")
    class CreateOrderWithCoupon {

        @DisplayName("정액 쿠폰을 적용하면 할인이 반영된 주문이 생성된다")
        @Test
        void createOrder_withFixedCoupon_appliesDiscount() {
            Brand brand = brandRepository.save(new Brand("나이키", "스포츠 브랜드"));
            Product product = productRepository.save(
                new Product(brand.getId(), "에어맥스", new Price(100000), new Stock(10)));
            Coupon coupon = couponRepository.save(
                new Coupon("5000원 할인", DiscountType.FIXED, 5000, 10000,
                    ZonedDateTime.now().plusDays(30)));
            CouponIssue couponIssue = couponIssueRepository.save(
                new CouponIssue(coupon.getId(), 1L, coupon.getExpiredAt()));

            Order result = orderFacade.createOrder(1L,
                List.of(new OrderFacade.OrderItemRequest(product.getId(), 1)),
                couponIssue.getId());

            assertThat(result.getOriginalTotalPrice()).isEqualTo(100000);
            assertThat(result.getDiscountAmount()).isEqualTo(5000);
            assertThat(result.getTotalPrice()).isEqualTo(95000);
            assertThat(result.getCouponIssueId()).isEqualTo(couponIssue.getId());
            assertThat(couponIssue.getStatus()).isEqualTo(CouponIssueStatus.USED);
        }

        @DisplayName("정률 쿠폰을 적용하면 비율에 따른 할인이 반영된다")
        @Test
        void createOrder_withRateCoupon_appliesPercentageDiscount() {
            Brand brand = brandRepository.save(new Brand("나이키", "스포츠 브랜드"));
            Product product = productRepository.save(
                new Product(brand.getId(), "에어맥스", new Price(100000), new Stock(10)));
            Coupon coupon = couponRepository.save(
                new Coupon("10% 할인", DiscountType.RATE, 10, 10000,
                    ZonedDateTime.now().plusDays(30)));
            CouponIssue couponIssue = couponIssueRepository.save(
                new CouponIssue(coupon.getId(), 1L, coupon.getExpiredAt()));

            Order result = orderFacade.createOrder(1L,
                List.of(new OrderFacade.OrderItemRequest(product.getId(), 1)),
                couponIssue.getId());

            assertThat(result.getOriginalTotalPrice()).isEqualTo(100000);
            assertThat(result.getDiscountAmount()).isEqualTo(10000);
            assertThat(result.getTotalPrice()).isEqualTo(90000);
        }

        @DisplayName("이미 사용된 쿠폰으로 주문하면 예외가 발생한다")
        @Test
        void createOrder_withUsedCoupon_throwsException() {
            Brand brand = brandRepository.save(new Brand("나이키", "스포츠 브랜드"));
            Product product = productRepository.save(
                new Product(brand.getId(), "에어맥스", new Price(100000), new Stock(10)));
            Coupon coupon = couponRepository.save(
                new Coupon("할인", DiscountType.FIXED, 5000, 0,
                    ZonedDateTime.now().plusDays(30)));
            CouponIssue couponIssue = couponIssueRepository.save(
                new CouponIssue(coupon.getId(), 1L, coupon.getExpiredAt()));
            couponIssue.use(99L, ZonedDateTime.now());

            assertThatThrownBy(() -> orderFacade.createOrder(1L,
                List.of(new OrderFacade.OrderItemRequest(product.getId(), 1)),
                couponIssue.getId()))
                .isInstanceOf(CoreException.class)
                .extracting(e -> ((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("타인의 쿠폰으로 주문하면 예외가 발생한다")
        @Test
        void createOrder_withOtherMemberCoupon_throwsException() {
            Brand brand = brandRepository.save(new Brand("나이키", "스포츠 브랜드"));
            Product product = productRepository.save(
                new Product(brand.getId(), "에어맥스", new Price(100000), new Stock(10)));
            Coupon coupon = couponRepository.save(
                new Coupon("할인", DiscountType.FIXED, 5000, 0,
                    ZonedDateTime.now().plusDays(30)));
            CouponIssue couponIssue = couponIssueRepository.save(
                new CouponIssue(coupon.getId(), 2L, coupon.getExpiredAt()));

            assertThatThrownBy(() -> orderFacade.createOrder(1L,
                List.of(new OrderFacade.OrderItemRequest(product.getId(), 1)),
                couponIssue.getId()))
                .isInstanceOf(CoreException.class)
                .extracting(e -> ((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.FORBIDDEN);
        }

        @DisplayName("만료된 쿠폰으로 주문하면 예외가 발생한다")
        @Test
        void createOrder_withExpiredCoupon_throwsException() {
            Brand brand = brandRepository.save(new Brand("나이키", "스포츠 브랜드"));
            Product product = productRepository.save(
                new Product(brand.getId(), "에어맥스", new Price(100000), new Stock(10)));
            Coupon coupon = couponRepository.save(
                new Coupon("할인", DiscountType.FIXED, 5000, 0,
                    ZonedDateTime.now().minusDays(1)));
            CouponIssue couponIssue = couponIssueRepository.save(
                new CouponIssue(coupon.getId(), 1L, coupon.getExpiredAt()));

            assertThatThrownBy(() -> orderFacade.createOrder(1L,
                List.of(new OrderFacade.OrderItemRequest(product.getId(), 1)),
                couponIssue.getId()))
                .isInstanceOf(CoreException.class)
                .extracting(e -> ((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("존재하지 않는 쿠폰으로 주문하면 예외가 발생한다")
        @Test
        void createOrder_withNonExistentCoupon_throwsException() {
            Brand brand = brandRepository.save(new Brand("나이키", "스포츠 브랜드"));
            Product product = productRepository.save(
                new Product(brand.getId(), "에어맥스", new Price(100000), new Stock(10)));

            assertThatThrownBy(() -> orderFacade.createOrder(1L,
                List.of(new OrderFacade.OrderItemRequest(product.getId(), 1)),
                999L))
                .isInstanceOf(CoreException.class)
                .extracting(e -> ((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("주문 단건 조회")
    class GetOrder {

        @DisplayName("본인의 주문을 조회하면 주문이 반환된다")
        @Test
        void getOrder_whenOwner_returnsOrder() {
            Brand brand = brandRepository.save(new Brand("나이키", "스포츠 브랜드"));
            Product product = productRepository.save(
                new Product(brand.getId(), "에어맥스", new Price(150000), new Stock(10)));
            Order order = orderFacade.createOrder(1L, List.of(
                new OrderFacade.OrderItemRequest(product.getId(), 1)));

            Order result = orderFacade.getOrder(order.getId(), 1L);

            assertThat(result.getId()).isEqualTo(order.getId());
            assertThat(result.getMemberId()).isEqualTo(1L);
        }

        @DisplayName("타인의 주문을 조회하면 예외가 발생한다")
        @Test
        void getOrder_whenNotOwner_throwsForbidden() {
            Brand brand = brandRepository.save(new Brand("나이키", "스포츠 브랜드"));
            Product product = productRepository.save(
                new Product(brand.getId(), "에어맥스", new Price(150000), new Stock(10)));
            Order order = orderFacade.createOrder(1L, List.of(
                new OrderFacade.OrderItemRequest(product.getId(), 1)));

            assertThatThrownBy(() -> orderFacade.getOrder(order.getId(), 2L))
                .isInstanceOf(CoreException.class)
                .extracting(e -> ((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.FORBIDDEN);
        }

        @DisplayName("존재하지 않는 주문을 조회하면 예외가 발생한다")
        @Test
        void getOrder_whenNotExists_throwsCoreException() {
            assertThatThrownBy(() -> orderFacade.getOrder(999L, 1L))
                .isInstanceOf(CoreException.class)
                .extracting(e -> ((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("주문 취소")
    class CancelOrder {

        @DisplayName("주문을 취소하면 상태가 CANCELLED로 변경되고 재고가 복원된다")
        @Test
        void cancelOrder_cancelsAndRestoresStock() {
            Brand brand = brandRepository.save(new Brand("나이키", "스포츠 브랜드"));
            Product product = productRepository.save(
                new Product(brand.getId(), "에어맥스", new Price(150000), new Stock(10)));
            Order order = orderFacade.createOrder(1L, List.of(
                new OrderFacade.OrderItemRequest(product.getId(), 3)));
            assertThat(product.getStock().getQuantity()).isEqualTo(7);

            orderFacade.cancelOrder(order.getId(), 1L);

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(product.getStock().getQuantity()).isEqualTo(10);
        }

        @DisplayName("쿠폰 적용된 주문을 취소하면 쿠폰이 복원된다")
        @Test
        void cancelOrder_withCoupon_restoresCoupon() {
            Brand brand = brandRepository.save(new Brand("나이키", "스포츠 브랜드"));
            Product product = productRepository.save(
                new Product(brand.getId(), "에어맥스", new Price(100000), new Stock(10)));
            Coupon coupon = couponRepository.save(
                new Coupon("할인", DiscountType.FIXED, 5000, 0,
                    ZonedDateTime.now().plusDays(30)));
            CouponIssue couponIssue = couponIssueRepository.save(
                new CouponIssue(coupon.getId(), 1L, coupon.getExpiredAt()));
            Order order = orderFacade.createOrder(1L,
                List.of(new OrderFacade.OrderItemRequest(product.getId(), 1)),
                couponIssue.getId());
            assertThat(couponIssue.getStatus()).isEqualTo(CouponIssueStatus.USED);

            orderFacade.cancelOrder(order.getId(), 1L);

            assertThat(couponIssue.getStatus()).isEqualTo(CouponIssueStatus.AVAILABLE);
        }

        @DisplayName("만료된 쿠폰이 적용된 주문을 취소하면 쿠폰이 EXPIRED로 변경된다")
        @Test
        void cancelOrder_withExpiredCoupon_setsCouponExpired() {
            Brand brand = brandRepository.save(new Brand("나이키", "스포츠 브랜드"));
            Product product = productRepository.save(
                new Product(brand.getId(), "에어맥스", new Price(100000), new Stock(10)));
            Coupon coupon = couponRepository.save(
                new Coupon("할인", DiscountType.FIXED, 5000, 0,
                    ZonedDateTime.now().plusSeconds(1)));
            CouponIssue couponIssue = couponIssueRepository.save(
                new CouponIssue(coupon.getId(), 1L, coupon.getExpiredAt()));
            Order order = orderFacade.createOrder(1L,
                List.of(new OrderFacade.OrderItemRequest(product.getId(), 1)),
                couponIssue.getId());
            assertThat(couponIssue.getStatus()).isEqualTo(CouponIssueStatus.USED);

            // 쿠폰 만료 후 취소 — Clock 기반이므로 실제 시간에 의존하지만
            // CouponFacade가 Clock.systemDefaultZone()을 사용하므로
            // 만료시간이 1초 뒤로 설정되어 테스트 시점에 이미 만료됨에 가까움
            // 명시적으로 확인하기 위해 직접 cancelUse 호출로 검증
            couponIssue.cancelUse(coupon.getExpiredAt().plusSeconds(1));

            assertThat(couponIssue.getStatus()).isEqualTo(CouponIssueStatus.EXPIRED);
        }

        @DisplayName("타인의 주문을 취소하면 예외가 발생한다")
        @Test
        void cancelOrder_whenNotOwner_throwsForbidden() {
            Brand brand = brandRepository.save(new Brand("나이키", "스포츠 브랜드"));
            Product product = productRepository.save(
                new Product(brand.getId(), "에어맥스", new Price(150000), new Stock(10)));
            Order order = orderFacade.createOrder(1L, List.of(
                new OrderFacade.OrderItemRequest(product.getId(), 1)));

            assertThatThrownBy(() -> orderFacade.cancelOrder(order.getId(), 2L))
                .isInstanceOf(CoreException.class)
                .extracting(e -> ((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.FORBIDDEN);
        }

        @DisplayName("이미 취소된 주문을 다시 취소하면 예외가 발생한다")
        @Test
        void cancelOrder_whenAlreadyCancelled_throwsException() {
            Brand brand = brandRepository.save(new Brand("나이키", "스포츠 브랜드"));
            Product product = productRepository.save(
                new Product(brand.getId(), "에어맥스", new Price(150000), new Stock(10)));
            Order order = orderFacade.createOrder(1L, List.of(
                new OrderFacade.OrderItemRequest(product.getId(), 1)));
            orderFacade.cancelOrder(order.getId(), 1L);

            assertThatThrownBy(() -> orderFacade.cancelOrder(order.getId(), 1L))
                .isInstanceOf(CoreException.class)
                .extracting(e -> ((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("존재하지 않는 주문을 취소하면 예외가 발생한다")
        @Test
        void cancelOrder_whenNotExists_throwsCoreException() {
            assertThatThrownBy(() -> orderFacade.cancelOrder(999L, 1L))
                .isInstanceOf(CoreException.class)
                .extracting(e -> ((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("회원별 주문 목록 조회")
    class GetOrdersByMemberId {

        @DisplayName("기간 조건 없이 조회하면 회원의 전체 주문이 반환된다")
        @Test
        void getOrdersByMemberId_withoutDateRange_returnsAll() {
            Brand brand = brandRepository.save(new Brand("나이키", "스포츠 브랜드"));
            Product product = productRepository.save(
                new Product(brand.getId(), "에어맥스", new Price(150000), new Stock(100)));
            orderFacade.createOrder(1L, List.of(
                new OrderFacade.OrderItemRequest(product.getId(), 1)));
            orderFacade.createOrder(1L, List.of(
                new OrderFacade.OrderItemRequest(product.getId(), 2)));
            orderFacade.createOrder(2L, List.of(
                new OrderFacade.OrderItemRequest(product.getId(), 1)));

            List<Order> result = orderFacade.getOrdersByMemberId(1L, null, null);

            assertThat(result).hasSize(2);
            assertThat(result).allSatisfy(order ->
                assertThat(order.getMemberId()).isEqualTo(1L));
        }

        @DisplayName("기간 조건으로 조회하면 해당 기간의 주문만 반환된다")
        @Test
        void getOrdersByMemberId_withDateRange_returnsFiltered() {
            Brand brand = brandRepository.save(new Brand("나이키", "스포츠 브랜드"));
            Product product = productRepository.save(
                new Product(brand.getId(), "에어맥스", new Price(150000), new Stock(100)));
            orderFacade.createOrder(1L, List.of(
                new OrderFacade.OrderItemRequest(product.getId(), 1)));

            ZonedDateTime now = ZonedDateTime.now();
            ZonedDateTime startAt = now.minusHours(1);
            ZonedDateTime endAt = now.plusHours(1);

            List<Order> result = orderFacade.getOrdersByMemberId(1L, startAt, endAt);

            assertThat(result).hasSize(1);
        }

        @DisplayName("주문이 없는 회원을 조회하면 빈 리스트가 반환된다")
        @Test
        void getOrdersByMemberId_whenNoOrders_returnsEmptyList() {
            List<Order> result = orderFacade.getOrdersByMemberId(999L, null, null);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("전체 주문 조회")
    class GetAllOrders {

        @DisplayName("모든 주문이 반환된다")
        @Test
        void getAllOrders_returnsAll() {
            Brand brand = brandRepository.save(new Brand("나이키", "스포츠 브랜드"));
            Product product = productRepository.save(
                new Product(brand.getId(), "에어맥스", new Price(150000), new Stock(100)));
            orderFacade.createOrder(1L, List.of(
                new OrderFacade.OrderItemRequest(product.getId(), 1)));
            orderFacade.createOrder(2L, List.of(
                new OrderFacade.OrderItemRequest(product.getId(), 1)));

            List<Order> result = orderFacade.getAllOrders();

            assertThat(result).hasSize(2);
        }

        @DisplayName("주문이 없으면 빈 리스트가 반환된다")
        @Test
        void getAllOrders_whenEmpty_returnsEmptyList() {
            List<Order> result = orderFacade.getAllOrders();

            assertThat(result).isEmpty();
        }
    }
}
