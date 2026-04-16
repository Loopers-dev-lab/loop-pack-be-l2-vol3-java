package com.loopers.application.order;

import com.loopers.application.queue.QueueService;
import com.loopers.application.queue.QueueSseRegistry;
import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponTemplateRepository;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.domain.coupon.IssuedCouponRepository;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderDomainService;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.SortCondition;
import com.loopers.domain.queue.QueueRepository;
import com.loopers.domain.queue.QueueToken;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderServiceTest {

    private OrderService orderService;
    private FakeProductRepository fakeProductRepository;
    private FakeOrderRepository fakeOrderRepository;
    private FakeIssuedCouponRepository fakeIssuedCouponRepository;
    private FakeCouponTemplateRepository fakeCouponTemplateRepository;

    @BeforeEach
    void setUp() {
        fakeProductRepository = new FakeProductRepository();
        fakeOrderRepository = new FakeOrderRepository();
        fakeIssuedCouponRepository = new FakeIssuedCouponRepository();
        fakeCouponTemplateRepository = new FakeCouponTemplateRepository();
        OrderDomainService orderDomainService = new OrderDomainService(fakeProductRepository, fakeOrderRepository);
        // FakeQueueRepository: isEntered()가 항상 true → 테스트에서 대기열 검증 통과
        QueueService queueService = new QueueService(new FakeQueueRepository(), new QueueSseRegistry());
        com.loopers.domain.outbox.OutboxEventRepository fakeOutboxEventRepository = new com.loopers.domain.outbox.OutboxEventRepository() {
            @Override public com.loopers.domain.outbox.OutboxEvent save(com.loopers.domain.outbox.OutboxEvent event) { return event; }
            @Override public List<com.loopers.domain.outbox.OutboxEvent> findPending() { return List.of(); }
        };
        orderService = new OrderService(orderDomainService, fakeIssuedCouponRepository, fakeCouponTemplateRepository, queueService, fakeOutboxEventRepository, new com.fasterxml.jackson.databind.ObjectMapper());
    }

    @DisplayName("주문 생성")
    @Nested
    class PlaceOrder {

        @DisplayName("재고가 충분하면 주문이 성공한다")
        @Test
        void success() {
            Long memberId = 1L;
            fakeProductRepository.save(new Product(1L, "상품", 10_000L, 10));
            List<OrderDomainService.OrderLineRequest> items = List.of(
                new OrderDomainService.OrderLineRequest(1L, 3)
            );

            OrderService.OrderResult result = orderService.placeOrder(memberId, items, null);

            assertThat(result.orderId()).isNotNull();
            assertThat(result.status()).isEqualTo("ORDERED");
            assertThat(result.totalAmount()).isEqualTo(30_000L);
            assertThat(result.originalAmount()).isEqualTo(30_000L);
            assertThat(result.discountAmount()).isEqualTo(0L);
            assertThat(result.orderLines()).hasSize(1);
        }

        @DisplayName("재고가 부족하면 예외가 발생한다")
        @Test
        void failsWhenInsufficientStock() {
            Long memberId = 1L;
            fakeProductRepository.save(new Product(1L, "상품", 10_000L, 2));

            assertThatThrownBy(() -> orderService.placeOrder(memberId, List.of(
                new OrderDomainService.OrderLineRequest(1L, 5)
            ), null))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.INSUFFICIENT_STOCK);
        }
    }

    @DisplayName("쿠폰 적용 주문")
    @Nested
    class PlaceOrderWithCoupon {

        @DisplayName("정액 쿠폰 적용 시 할인이 반영된다")
        @Test
        void fixedCouponDiscount() {
            Long memberId = 1L;
            fakeProductRepository.save(new Product(1L, "상품", 10_000L, 10));

            CouponTemplate template = fakeCouponTemplateRepository.save(
                new CouponTemplate("1000원 할인", CouponType.FIXED, 1000, null, ZonedDateTime.now().plusDays(30))
            );
            IssuedCoupon issued = fakeIssuedCouponRepository.save(new IssuedCoupon(1L, memberId));

            OrderService.OrderResult result = orderService.placeOrder(memberId, List.of(
                new OrderDomainService.OrderLineRequest(1L, 3)
            ), 1L);

            assertThat(result.originalAmount()).isEqualTo(30_000L);
            assertThat(result.discountAmount()).isEqualTo(1_000L);
            assertThat(result.totalAmount()).isEqualTo(29_000L);
        }

        @DisplayName("정률 쿠폰 적용 시 할인이 반영된다")
        @Test
        void rateCouponDiscount() {
            Long memberId = 1L;
            fakeProductRepository.save(new Product(1L, "상품", 10_000L, 10));

            fakeCouponTemplateRepository.save(
                new CouponTemplate("10% 할인", CouponType.RATE, 10, null, ZonedDateTime.now().plusDays(30))
            );
            fakeIssuedCouponRepository.save(new IssuedCoupon(1L, memberId));

            OrderService.OrderResult result = orderService.placeOrder(memberId, List.of(
                new OrderDomainService.OrderLineRequest(1L, 3)
            ), 1L);

            assertThat(result.originalAmount()).isEqualTo(30_000L);
            assertThat(result.discountAmount()).isEqualTo(3_000L);
            assertThat(result.totalAmount()).isEqualTo(27_000L);
        }

        @DisplayName("이미 사용된 쿠폰은 사용할 수 없다")
        @Test
        void failsWhenCouponAlreadyUsed() {
            Long memberId = 1L;
            fakeProductRepository.save(new Product(1L, "상품", 10_000L, 10));
            fakeCouponTemplateRepository.save(
                new CouponTemplate("할인", CouponType.FIXED, 1000, null, ZonedDateTime.now().plusDays(30))
            );
            IssuedCoupon issued = fakeIssuedCouponRepository.save(new IssuedCoupon(1L, memberId));
            issued.use();

            assertThatThrownBy(() -> orderService.placeOrder(memberId, List.of(
                new OrderDomainService.OrderLineRequest(1L, 1)
            ), 1L))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.COUPON_UNAVAILABLE);
        }

        @DisplayName("타인 쿠폰은 사용할 수 없다")
        @Test
        void failsWhenNotOwned() {
            Long memberId = 1L;
            Long otherMemberId = 2L;
            fakeProductRepository.save(new Product(1L, "상품", 10_000L, 10));
            fakeCouponTemplateRepository.save(
                new CouponTemplate("할인", CouponType.FIXED, 1000, null, ZonedDateTime.now().plusDays(30))
            );
            fakeIssuedCouponRepository.save(new IssuedCoupon(1L, otherMemberId));

            assertThatThrownBy(() -> orderService.placeOrder(memberId, List.of(
                new OrderDomainService.OrderLineRequest(1L, 1)
            ), 1L))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.COUPON_NOT_OWNED);
        }
    }

    static class FakeProductRepository implements ProductRepository {
        private final Map<Long, Product> store = new ConcurrentHashMap<>();
        private long nextId = 1;

        @Override
        public Product save(Product product) {
            long id = nextId++;
            store.put(id, product);
            return product;
        }

        @Override
        public Optional<Product> findById(Long id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public Optional<Product> findByIdForUpdate(Long id) {
            return findById(id);
        }

        @Override
        public List<Product> findAll(SortCondition sort) {
            return new ArrayList<>(store.values());
        }

        @Override
        public boolean existsById(Long id) {
            return store.containsKey(id);
        }

        @Override
        public List<Product> findAllByIds(List<Long> ids) {
            return ids.stream()
                .map(store::get)
                .filter(java.util.Objects::nonNull)
                .toList();
        }
    }

    static class FakeOrderRepository implements OrderRepository {
        private final List<Order> store = new ArrayList<>();

        @Override
        public Order save(Order order) {
            store.add(order);
            return order;
        }

        @Override
        public Optional<Order> findById(Long id) {
            return Optional.empty();
        }
    }

    static class FakeIssuedCouponRepository implements IssuedCouponRepository {
        private final Map<Long, IssuedCoupon> store = new ConcurrentHashMap<>();
        private long nextId = 1;

        @Override
        public IssuedCoupon save(IssuedCoupon issuedCoupon) {
            long id = nextId++;
            store.put(id, issuedCoupon);
            return issuedCoupon;
        }

        @Override
        public Optional<IssuedCoupon> findById(Long id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public Optional<IssuedCoupon> findByIdForUpdate(Long id) {
            return findById(id);
        }

        @Override
        public List<IssuedCoupon> findByMemberId(Long memberId) {
            return store.values().stream().filter(c -> c.getMemberId().equals(memberId)).toList();
        }

        @Override
        public boolean existsByMemberIdAndCouponTemplateId(Long memberId, Long couponTemplateId) {
            return store.values().stream()
                .anyMatch(c -> c.getMemberId().equals(memberId) && c.getCouponTemplateId().equals(couponTemplateId));
        }

        @Override
        public List<IssuedCoupon> findByCouponTemplateId(Long couponTemplateId, int page, int size) {
            return store.values().stream().filter(c -> c.getCouponTemplateId().equals(couponTemplateId)).toList();
        }

        @Override
        public long countByCouponTemplateId(Long couponTemplateId) {
            return store.values().stream().filter(c -> c.getCouponTemplateId().equals(couponTemplateId)).count();
        }
    }

    /**
     * 테스트용 QueueRepository: isEntered()가 항상 true → validateEntry() 통과.
     * OrderService 단위 테스트에서 대기열 로직을 격리하기 위해 사용.
     */
    static class FakeQueueRepository implements QueueRepository {
        @Override public void enter(QueueToken token) {}
        @Override public java.util.Optional<Long> getUserIdByToken(String token) { return java.util.Optional.empty(); }
        @Override public java.util.Optional<Long> getRank(String queueId, Long userId) { return java.util.Optional.of(0L); }
        @Override public long getTotalSize(String queueId) { return 0L; }
        @Override public boolean admit(String queueId, Long userId, long threshold) { return true; }
        @Override public boolean isEntered(Long userId) { return true; } // 항상 입장 허가 상태
        @Override public java.util.List<Long> admitBatch(String queueId, long batchSize) { return java.util.List.of(); }
        @Override public void deleteEntered(Long userId) {}
    }

    static class FakeCouponTemplateRepository implements CouponTemplateRepository {
        private final Map<Long, CouponTemplate> store = new ConcurrentHashMap<>();
        private long nextId = 1;

        @Override
        public CouponTemplate save(CouponTemplate couponTemplate) {
            long id = nextId++;
            store.put(id, couponTemplate);
            return couponTemplate;
        }

        @Override
        public Optional<CouponTemplate> findById(Long id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public List<CouponTemplate> findAll(int page, int size) {
            return new ArrayList<>(store.values());
        }

        @Override
        public long count() {
            return store.size();
        }
    }
}
