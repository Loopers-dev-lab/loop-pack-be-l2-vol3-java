package com.loopers.application.order;

import com.loopers.domain.order.Order;
import com.loopers.fake.FakeOrderRepository;
import com.loopers.fake.FakeProductRepository;
import com.loopers.fake.FakeProvisionalOrderRedisRepository;
import com.loopers.fake.FakeStockReservationRedisRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.vo.Price;
import com.loopers.domain.product.vo.Stock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProvisionalOrderServiceTest {

    private ProvisionalOrderService service;
    private FakeProvisionalOrderRedisRepository redisRepository;
    private FakeStockReservationRedisRepository stockRedisRepository;
    private FakeOrderRepository orderRepository;
    private FakeProductRepository productRepository;

    @BeforeEach
    void setUp() {
        redisRepository = new FakeProvisionalOrderRedisRepository();
        stockRedisRepository = new FakeStockReservationRedisRepository();
        orderRepository = new FakeOrderRepository();
        productRepository = new FakeProductRepository();

        service = new ProvisionalOrderService(
            redisRepository, stockRedisRepository, orderRepository, productRepository);
    }

    private Product createProduct(int stockQuantity) {
        Product product = new Product(1L, "에어맥스", new Price(5000), new Stock(stockQuantity));
        return productRepository.save(product);
    }

    @Nested
    @DisplayName("가주문 생성")
    class SaveProvisionalOrder {

        @DisplayName("U3-1: Redis 정상 → 가주문 Redis 저장 + 재고 예약")
        @Test
        void save_success_storedInRedis() {
            Product product = createProduct(100);
            stockRedisRepository.setStock(product.getId(), 100);

            List<Order.ItemSnapshot> items = List.of(
                new Order.ItemSnapshot(product.getId(), "에어맥스", 5000, "나이키", 2)
            );

            ProvisionalOrderService.ProvisionalOrderResult result =
                service.saveProvisionalOrder(1L, 100L, 10000, "SAMSUNG", "1234", items);

            // Redis에 가주문 저장 확인
            assertThat(result.isDirect()).isFalse();
            assertThat(result.orderId()).isEqualTo(1L);
            assertThat(redisRepository.exists(1L)).isTrue();

            // Redis 재고 예약(DECR) 확인
            assertThat(stockRedisRepository.getStock(product.getId())).isEqualTo(98L);
        }

        @DisplayName("U3-2: Redis 장애 → DB 직접 주문 Fallback")
        @Test
        void save_redisFail_fallbackToDb() {
            Product product = createProduct(100);

            List<Order.ItemSnapshot> items = List.of(
                new Order.ItemSnapshot(product.getId(), "에어맥스", 5000, "나이키", 2)
            );

            // Fallback 메서드 직접 호출 (Spring AOP 없이 테스트)
            ProvisionalOrderService.ProvisionalOrderResult result =
                service.saveToDbFallback(1L, 100L, 10000, "SAMSUNG", "1234", items,
                    new RuntimeException("Redis 연결 실패"));

            // DB에 Order 직접 생성 확인
            assertThat(result.isDirect()).isTrue();
            assertThat(result.orderId()).isNotNull();

            // DB 재고 차감 확인
            Product updated = productRepository.findById(product.getId()).orElseThrow();
            assertThat(updated.getStock().getQuantity()).isEqualTo(98);

            // Redis에는 저장되지 않음
            assertThat(redisRepository.exists(1L)).isFalse();
        }
    }

    @Nested
    @DisplayName("가주문 조회/삭제")
    class QueryAndDelete {

        @DisplayName("가주문 조회 성공")
        @Test
        void getProvisionalOrder_exists_returnsData() {
            Product product = createProduct(100);
            stockRedisRepository.setStock(product.getId(), 100);

            List<Order.ItemSnapshot> items = List.of(
                new Order.ItemSnapshot(product.getId(), "에어맥스", 5000, "나이키", 1)
            );
            service.saveProvisionalOrder(1L, 100L, 5000, "SAMSUNG", "1234", items);

            assertThat(service.getProvisionalOrder(1L)).isPresent();
        }

        @DisplayName("가주문 삭제 후 조회 불가")
        @Test
        void deleteProvisionalOrder_thenNotFound() {
            Product product = createProduct(100);
            stockRedisRepository.setStock(product.getId(), 100);

            List<Order.ItemSnapshot> items = List.of(
                new Order.ItemSnapshot(product.getId(), "에어맥스", 5000, "나이키", 1)
            );
            service.saveProvisionalOrder(1L, 100L, 5000, "SAMSUNG", "1234", items);
            service.deleteProvisionalOrder(1L);

            assertThat(service.exists(1L)).isFalse();
        }
    }
}
