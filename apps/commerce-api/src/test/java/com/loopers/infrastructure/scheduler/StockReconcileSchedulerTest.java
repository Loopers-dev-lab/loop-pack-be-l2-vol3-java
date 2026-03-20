package com.loopers.infrastructure.scheduler;

import com.loopers.domain.product.Product;
import com.loopers.domain.product.vo.Price;
import com.loopers.domain.product.vo.Stock;
import com.loopers.fake.FakeProductRepository;
import com.loopers.fake.FakeStockReservationRedisRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StockReconcileSchedulerTest {

    private StockReconcileScheduler scheduler;
    private FakeProductRepository productRepository;
    private FakeStockReservationRedisRepository stockRedisRepository;

    @BeforeEach
    void setUp() {
        productRepository = new FakeProductRepository();
        stockRedisRepository = new FakeStockReservationRedisRepository();
        scheduler = new StockReconcileScheduler(productRepository, stockRedisRepository);
    }

    @DisplayName("Redis 재고 불일치 → DB 기준으로 보정")
    @Test
    void reconcile_mismatch_correctsRedisToDbValue() {
        Product product = productRepository.save(
            new Product(1L, "에어맥스", new Price(5000), new Stock(100)));
        stockRedisRepository.setStock(product.getId(), 50); // Redis: 50, DB: 100

        scheduler.reconcileStock();

        assertThat(stockRedisRepository.getStock(product.getId())).isEqualTo(100);
    }

    @DisplayName("Redis에 재고 키 없음 → DB 기준으로 초기화")
    @Test
    void reconcile_noRedisKey_initializesFromDb() {
        Product product = productRepository.save(
            new Product(1L, "에어맥스", new Price(5000), new Stock(100)));
        // Redis에 키 없음

        scheduler.reconcileStock();

        assertThat(stockRedisRepository.getStock(product.getId())).isEqualTo(100);
    }

    @DisplayName("Redis-DB 재고 일치 → 변경 없음")
    @Test
    void reconcile_match_noChange() {
        Product product = productRepository.save(
            new Product(1L, "에어맥스", new Price(5000), new Stock(100)));
        stockRedisRepository.setStock(product.getId(), 100); // 일치

        scheduler.reconcileStock();

        assertThat(stockRedisRepository.getStock(product.getId())).isEqualTo(100);
    }
}
