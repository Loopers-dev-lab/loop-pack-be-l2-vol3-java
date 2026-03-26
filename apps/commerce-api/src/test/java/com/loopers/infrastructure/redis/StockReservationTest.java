package com.loopers.infrastructure.redis;

import com.loopers.fake.FakeStockReservationRedisRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StockReservationTest {

    private FakeStockReservationRedisRepository stockRepository;

    @BeforeEach
    void setUp() {
        stockRepository = new FakeStockReservationRedisRepository();
    }

    @Nested
    @DisplayName("재고 예약/복원")
    class StockReservation {

        @DisplayName("U3-3: Redis DECR → 재고 감소 확인")
        @Test
        void decrease_reducesStock() {
            stockRepository.setStock(1L, 100);

            Long remaining = stockRepository.decrease(1L, 3);

            assertThat(remaining).isEqualTo(97);
            assertThat(stockRepository.getStock(1L)).isEqualTo(97);
        }

        @DisplayName("U3-4: Redis INCR → 재고 복원 확인")
        @Test
        void increase_restoresStock() {
            stockRepository.setStock(1L, 97);

            Long restored = stockRepository.increase(1L, 3);

            assertThat(restored).isEqualTo(100);
            assertThat(stockRepository.getStock(1L)).isEqualTo(100);
        }

        @DisplayName("재고 조회 — 키 없으면 null 반환")
        @Test
        void getStock_keyNotExists_returnsNull() {
            assertThat(stockRepository.getStock(999L)).isNull();
        }

        @DisplayName("재고 초기화 — setStock으로 DB 동기화")
        @Test
        void setStock_initializesStock() {
            stockRepository.setStock(1L, 50);

            assertThat(stockRepository.getStock(1L)).isEqualTo(50);
        }
    }
}
