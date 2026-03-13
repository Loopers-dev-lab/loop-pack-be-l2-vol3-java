package com.loopers.application.order;

import com.loopers.domain.order.OrderDomainService;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.SortCondition;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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

    @BeforeEach
    void setUp() {
        fakeProductRepository = new FakeProductRepository();
        fakeOrderRepository = new FakeOrderRepository();
        OrderDomainService orderDomainService = new OrderDomainService(fakeProductRepository, fakeOrderRepository);
        orderService = new OrderService(orderDomainService);
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

            OrderService.OrderResult result = orderService.placeOrder(memberId, items);

            assertThat(result.orderId()).isNotNull();
            assertThat(result.status()).isEqualTo("ORDERED");
            assertThat(result.totalAmount()).isEqualTo(30_000L);
            assertThat(result.orderLines()).hasSize(1);
        }

        @DisplayName("재고가 부족하면 예외가 발생한다")
        @Test
        void failsWhenInsufficientStock() {
            Long memberId = 1L;
            fakeProductRepository.save(new Product(1L, "상품", 10_000L, 2));

            assertThatThrownBy(() -> orderService.placeOrder(memberId, List.of(
                new OrderDomainService.OrderLineRequest(1L, 5)
            )))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.INSUFFICIENT_STOCK);
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
        public List<Product> findAll(SortCondition sort) {
            return new ArrayList<>(store.values());
        }
    }

    static class FakeOrderRepository implements OrderRepository {
        private final List<com.loopers.domain.order.Order> store = new ArrayList<>();

        @Override
        public com.loopers.domain.order.Order save(com.loopers.domain.order.Order order) {
            store.add(order);
            return order;
        }

        @Override
        public Optional<com.loopers.domain.order.Order> findById(Long id) {
            return Optional.empty();
        }
    }
}
