package com.loopers.batch.job.ranking.fixture;

import java.time.LocalDate;

/**
 * 시드 생성 파라미터.
 * - {@code totalProducts}: 전체 상품 수 (이 중 70% 는 Sleeping = 이벤트 0)
 * - {@code anchorDate}: anchor (= 어제). last30dStart = anchor - 29일
 * - {@code historyDays}: 시드를 만들 일수 (롤링 30일 검증엔 30 이상)
 * - {@code seed}: 결정적 재현을 위한 random seed
 *
 * <p>Zipf α=1.2 로 활동 상품(Hot+Warm+Normal+Cold = 30%) 의 일일 이벤트 양을 분포시킨다.
 * S/M/L 단계는 totalProducts 만 다르게 두어 선형성을 측정한다.</p>
 */
public record SeedSpec(int totalProducts, LocalDate anchorDate, int historyDays, long seed) {

    public static SeedSpec small(LocalDate anchorDate) {
        return new SeedSpec(1_000, anchorDate, 30, 42L);
    }

    public static SeedSpec medium(LocalDate anchorDate) {
        return new SeedSpec(5_000, anchorDate, 30, 42L);
    }

    public static SeedSpec large(LocalDate anchorDate) {
        return new SeedSpec(20_000, anchorDate, 30, 42L);
    }
}
