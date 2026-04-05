package com.loopers.infrastructure.product;

import com.loopers.domain.product.ViewDedupRepository;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("ViewDedupRedisRepository 통합 테스트 — Bitmap SETBIT 중복 방지")
class ViewDedupRedisRepositoryTest {

    @Autowired
    private ViewDedupRepository viewDedupRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @Test
    @DisplayName("최초 조회는 true 반환 (이벤트 발행 대상)")
    void firstViewReturnsTrue() {
        LocalDate date = LocalDate.of(2026, 4, 5);

        boolean firstView = viewDedupRepository.markIfFirstView(1L, 42L, date);

        assertThat(firstView).isTrue();
    }

    @Test
    @DisplayName("동일 유저 동일 상품 같은 날 재조회는 false 반환 (스킵)")
    void duplicateViewReturnsFalse() {
        LocalDate date = LocalDate.of(2026, 4, 5);

        viewDedupRepository.markIfFirstView(1L, 42L, date);
        boolean secondView = viewDedupRepository.markIfFirstView(1L, 42L, date);
        boolean thirdView = viewDedupRepository.markIfFirstView(1L, 42L, date);

        assertThat(secondView).isFalse();
        assertThat(thirdView).isFalse();
    }

    @Test
    @DisplayName("같은 상품이어도 다른 유저는 각자 최초 조회(true)")
    void differentUsersIndependentlyTracked() {
        LocalDate date = LocalDate.of(2026, 4, 5);

        assertThat(viewDedupRepository.markIfFirstView(1L, 100L, date)).isTrue();
        assertThat(viewDedupRepository.markIfFirstView(1L, 200L, date)).isTrue();
        assertThat(viewDedupRepository.markIfFirstView(1L, 300L, date)).isTrue();

        // 재조회는 모두 false
        assertThat(viewDedupRepository.markIfFirstView(1L, 100L, date)).isFalse();
        assertThat(viewDedupRepository.markIfFirstView(1L, 200L, date)).isFalse();
    }

    @Test
    @DisplayName("같은 유저여도 다른 상품은 각자 최초 조회(true)")
    void sameUserDifferentProducts() {
        LocalDate date = LocalDate.of(2026, 4, 5);

        assertThat(viewDedupRepository.markIfFirstView(1L, 42L, date)).isTrue();
        assertThat(viewDedupRepository.markIfFirstView(2L, 42L, date)).isTrue();
        assertThat(viewDedupRepository.markIfFirstView(3L, 42L, date)).isTrue();
    }

    @Test
    @DisplayName("같은 유저/상품이어도 날짜가 다르면 각자 최초 조회(true)")
    void sameUserSameProductDifferentDates() {
        LocalDate day1 = LocalDate.of(2026, 4, 5);
        LocalDate day2 = LocalDate.of(2026, 4, 6);

        assertThat(viewDedupRepository.markIfFirstView(1L, 42L, day1)).isTrue();
        assertThat(viewDedupRepository.markIfFirstView(1L, 42L, day2)).isTrue();
    }

    @Test
    @DisplayName("어뷰징 시나리오 — 10봇 × 1000회 호출해도 봇당 1회만 true")
    void abuseScenario() {
        LocalDate date = LocalDate.of(2026, 4, 5);
        int trueCount = 0;

        for (int bot = 0; bot < 10; bot++) {
            for (int iteration = 0; iteration < 1000; iteration++) {
                if (viewDedupRepository.markIfFirstView(1L, (long) bot, date)) {
                    trueCount++;
                }
            }
        }

        // 10봇 × 1000회 = 10,000 호출이지만 유효 카운트는 10
        assertThat(trueCount).isEqualTo(10);
    }

    @Test
    @DisplayName("Key TTL 설정 확인")
    void keyHasTtl() {
        LocalDate date = LocalDate.of(2026, 4, 5);
        viewDedupRepository.markIfFirstView(1L, 42L, date);

        Long ttl = redisTemplate.getExpire("view:bitmap:1:20260405");
        assertThat(ttl).isGreaterThan(0L);
    }
}
