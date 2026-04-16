package com.loopers.domain.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RankingWeight 가중치 상수 테스트")
class RankingWeightTest {

    @Test
    @DisplayName("주문 점수 — 건수 기본값이 0.7이다")
    void orderScore_BaseIs07() {
        // amount=0일 때 log₁₀(1) = 0 → ORDER_BASE만 반환
        assertThat(RankingWeight.orderScore(0)).isEqualTo(0.7);
    }

    @Test
    @DisplayName("주문 점수 — 금액이 높을수록 보너스가 크다")
    void orderScore_HigherAmountHigherBonus() {
        double cheap = RankingWeight.orderScore(10_000);       // 1만원
        double expensive = RankingWeight.orderScore(1_000_000); // 100만원
        assertThat(expensive).isGreaterThan(cheap);
    }

    @Test
    @DisplayName("주문 점수 — 금액 보너스가 건수 1건 차이(0.7)를 넘지 않는다")
    void orderScore_BonusNeverExceedsBase() {
        // 1억원 상품 — 실용적 최대값
        double maxBonus = RankingWeight.orderScore(100_000_000) - RankingWeight.ORDER_BASE;
        assertThat(maxBonus).isLessThan(RankingWeight.ORDER_BASE);
    }

    @Test
    @DisplayName("건수 우선 — 저가 2건이 고가 1건보다 높다")
    void orderScore_TwoOrdersBeatOneExpensive() {
        double cheapTwice = RankingWeight.orderScore(1_000) * 2;      // 양말 2건
        double expensiveOnce = RankingWeight.orderScore(10_000_000);   // 명품백 1건
        assertThat(cheapTwice).isGreaterThan(expensiveOnce);
    }
}
