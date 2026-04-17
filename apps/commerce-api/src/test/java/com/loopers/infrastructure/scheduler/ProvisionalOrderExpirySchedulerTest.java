package com.loopers.infrastructure.scheduler;

import com.loopers.fake.FakeProvisionalOrderRedisRepository;
import com.loopers.fake.FakeStockReservationRedisRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProvisionalOrderExpirySchedulerTest {

    private ProvisionalOrderExpiryScheduler scheduler;
    private FakeProvisionalOrderRedisRepository provisionalOrderRedisRepository;
    private FakeStockReservationRedisRepository stockRedisRepository;

    @BeforeEach
    void setUp() {
        provisionalOrderRedisRepository = new FakeProvisionalOrderRedisRepository();
        stockRedisRepository = new FakeStockReservationRedisRepository();
        scheduler = new ProvisionalOrderExpiryScheduler(
            provisionalOrderRedisRepository, stockRedisRepository);
    }

    private void saveProvisionalOrder(Long orderId, Long productId, int quantity) {
        Map<String, Object> orderData = Map.of(
            "orderId", orderId,
            "memberId", 100L,
            "amount", 5000,
            "items", List.of(Map.of("productId", productId, "quantity", quantity))
        );
        provisionalOrderRedisRepository.save(orderId, orderData);
    }

    @DisplayName("TTL < 30초 가주문 → 재고 복원 + 삭제")
    @Test
    void cleanup_expiringOrder_restoresStockAndDeletes() {
        stockRedisRepository.setStock(1L, 90); // 이미 10개 예약됨
        saveProvisionalOrder(1L, 1L, 10);
        provisionalOrderRedisRepository.setTtl(1L, 15); // TTL 15초 (30초 미만)

        scheduler.cleanupExpiringOrders();

        // 재고 복원 확인
        assertThat(stockRedisRepository.getStock(1L)).isEqualTo(100);
        // 가주문 삭제 확인
        assertThat(provisionalOrderRedisRepository.exists(1L)).isFalse();
    }

    @DisplayName("TTL >= 30초 가주문 → 정리하지 않음")
    @Test
    void cleanup_healthyOrder_noChange() {
        stockRedisRepository.setStock(1L, 90);
        saveProvisionalOrder(1L, 1L, 10);
        provisionalOrderRedisRepository.setTtl(1L, 600); // TTL 600초 (충분)

        scheduler.cleanupExpiringOrders();

        // 재고 변경 없음
        assertThat(stockRedisRepository.getStock(1L)).isEqualTo(90);
        // 가주문 유지
        assertThat(provisionalOrderRedisRepository.exists(1L)).isTrue();
    }

    @DisplayName("여러 가주문 중 만료 임박한 것만 정리")
    @Test
    void cleanup_mixedOrders_onlyExpiringCleaned() {
        stockRedisRepository.setStock(1L, 80); // 20개 예약됨 (주문 2건)
        saveProvisionalOrder(1L, 1L, 10);
        saveProvisionalOrder(2L, 1L, 10);
        provisionalOrderRedisRepository.setTtl(1L, 10);  // 만료 임박
        provisionalOrderRedisRepository.setTtl(2L, 1200); // 충분

        scheduler.cleanupExpiringOrders();

        // 주문 1만 정리
        assertThat(provisionalOrderRedisRepository.exists(1L)).isFalse();
        assertThat(provisionalOrderRedisRepository.exists(2L)).isTrue();
        // 재고: 80 + 10(복원) = 90
        assertThat(stockRedisRepository.getStock(1L)).isEqualTo(90);
    }
}
