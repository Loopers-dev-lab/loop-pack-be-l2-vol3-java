package com.loopers.application.ranking;

import com.loopers.domain.ranking.ProductRankingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static org.mockito.AdditionalMatchers.eq;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class RankingScoreServiceTest {

    private ProductRankingRepository productRankingRepository;
    private RankingScoreService rankingScoreService;

    private final String todayKey = "ranking:all:" +
        LocalDate.now(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.ofPattern("yyyyMMdd"));

    @BeforeEach
    void setUp() {
        productRankingRepository = mock(ProductRankingRepository.class);
        rankingScoreService = new RankingScoreService(productRankingRepository);
    }

    @DisplayName("PRODUCT_VIEWED 이벤트는 가중치 0.1로 점수를 증가시킨다")
    @Test
    void updateScore_productViewed() {
        rankingScoreService.updateScore("PRODUCT_VIEWED", 10L, 0);

        verify(productRankingRepository).incrementScore(todayKey, 10L, 0.1);
        verify(productRankingRepository).setTtlIfAbsent(todayKey, 172_800);
    }

    @DisplayName("PRODUCT_LIKED 이벤트는 가중치 0.2로 점수를 증가시킨다")
    @Test
    void updateScore_productLiked() {
        rankingScoreService.updateScore("PRODUCT_LIKED", 10L, 0);

        verify(productRankingRepository).incrementScore(todayKey, 10L, 0.2);
        verify(productRankingRepository).setTtlIfAbsent(todayKey, 172_800);
    }

    @DisplayName("PRODUCT_UNLIKED 이벤트는 가중치 -0.2로 점수를 감소시킨다")
    @Test
    void updateScore_productUnliked() {
        rankingScoreService.updateScore("PRODUCT_UNLIKED", 10L, 0);

        verify(productRankingRepository).incrementScore(todayKey, 10L, -0.2);
        verify(productRankingRepository).setTtlIfAbsent(todayKey, 172_800);
    }

    @DisplayName("ORDER_PLACED 이벤트는 수량과 무관하게 건당 고정 0.7로 점수를 증가시킨다")
    @Test
    void updateScore_orderPlaced() {
        rankingScoreService.updateScore("ORDER_PLACED", 10L, 3);

        verify(productRankingRepository).incrementScore(todayKey, 10L, 0.7);
        verify(productRankingRepository).setTtlIfAbsent(todayKey, 172_800);
    }

    @DisplayName("ORDER_CANCELLED 이벤트는 수량과 무관하게 건당 고정 -0.7로 점수를 감소시킨다")
    @Test
    void updateScore_orderCancelled() {
        rankingScoreService.updateScore("ORDER_CANCELLED", 10L, 2);

        verify(productRankingRepository).incrementScore(todayKey, 10L, -0.7);
        verify(productRankingRepository).setTtlIfAbsent(todayKey, 172_800);
    }

    @DisplayName("PRODUCT_PRICE_CHANGED 이벤트는 랭킹 점수에 반영하지 않는다")
    @Test
    void updateScore_priceChanged_doesNothing() {
        rankingScoreService.updateScore("PRODUCT_PRICE_CHANGED", 10L, 0);

        verify(productRankingRepository, never()).incrementScore(anyString(), anyLong(), anyDouble());
    }

    @DisplayName("알 수 없는 이벤트 타입은 랭킹 점수에 반영하지 않는다")
    @Test
    void updateScore_unknownEventType_doesNothing() {
        rankingScoreService.updateScore("UNKNOWN_EVENT", 10L, 0);

        verify(productRankingRepository, never()).incrementScore(anyString(), anyLong(), anyDouble());
    }
}
