package com.loopers.domain.order;

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

class OrderDomainServiceTest {

    private OrderDomainService orderDomainService;
    private FakeProductRepository fakeProductRepository;
    private FakeOrderRepository fakeOrderRepository;

    @BeforeEach
    void setUp() {
        fakeProductRepository = new FakeProductRepository();
        fakeOrderRepository = new FakeOrderRepository();
        orderDomainService = new OrderDomainService(fakeProductRepository, fakeOrderRepository);
    }

    @DisplayName("주문 생성")
    @Nested
    class PlaceOrder {

        @DisplayName("재고가 충분하면 주문이 성공하고 재고가 차감된다")
        @Test
        void success() {
            Long memberId = 1L;
            Product product = fakeProductRepository.save(new Product(1L, "상품", 10_000L, 10));
            Long productId = 1L;

            Order order = orderDomainService.placeOrder(memberId, List.of(
                new OrderDomainService.OrderLineRequest(productId, 3)
            ));

            assertThat(order.getMemberId()).isEqualTo(memberId);
            assertThat(order.getOrderLines()).hasSize(1);
            assertThat(order.getTotalAmount()).isEqualTo(30_000L);

            Product updatedProduct = fakeProductRepository.findById(productId).orElseThrow();
            assertThat(updatedProduct.getStockQuantity()).isEqualTo(7);
        }

        @DisplayName("재고가 부족하면 INSUFFICIENT_STOCK 예외가 발생하고 주문이 생성되지 않는다")
        @Test
        void failsWhenInsufficientStock() {
            Long memberId = 1L;
            fakeProductRepository.save(new Product(1L, "상품", 10_000L, 5));
            Long productId = 1L;

            assertThatThrownBy(() -> orderDomainService.placeOrder(memberId, List.of(
                new OrderDomainService.OrderLineRequest(productId, 10)
            )))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.INSUFFICIENT_STOCK);

            assertThat(fakeOrderRepository.count()).isEqualTo(0);
        }

        @DisplayName("존재하지 않는 상품이 포함되면 NOT_FOUND 예외가 발생한다")
        @Test
        void failsWhenProductNotFound() {
            Long memberId = 1L;
            Long nonExistentProductId = 999L;

            assertThatThrownBy(() -> orderDomainService.placeOrder(memberId, List.of(
                new OrderDomainService.OrderLineRequest(nonExistentProductId, 1)
            )))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.NOT_FOUND);
        }
    }

    static class FakeProductRepository implements ProductRepository {
        private final Map<Long, Product> store = new ConcurrentHashMap<>();
        private long nextId = 1;

        @Override
        public Product save(Product product) {
            Product toSave = new Product(
                product.getBrandId(),
                product.getName(),
                product.getPrice(),
                product.getStockQuantity()
            );
            long id = nextId++;
            store.put(id, toSave);
            return toSave;
        }

        @Override
        public Optional<Product> findById(Long id) {
            Product product = store.get(id);
            if (product == null) return Optional.empty();
            return Optional.of(product);
        }

        @Override
        public List<Product> findAll(SortCondition sort) {
            return new ArrayList<>(store.values());
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

        int count() {
            return store.size();
        }
    }
}
