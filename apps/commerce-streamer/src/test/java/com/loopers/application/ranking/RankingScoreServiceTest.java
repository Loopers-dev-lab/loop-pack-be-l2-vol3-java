package com.loopers.application.ranking;

import com.loopers.domain.event.OrderItemPayload;
import com.loopers.domain.ranking.FakeRankingRepository;
import com.loopers.support.redis.RankingKeyConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class RankingScoreServiceTest {

    private FakeRankingRepository rankingRepository;
    private RankingScoreService rankingScoreService;

    private static final double VIEW_WEIGHT = 0.1;
    private static final double LIKE_WEIGHT = 0.2;
    private static final double ORDER_WEIGHT = 0.7;
    private static final long DAY_TTL = 172800;
    private static final long HOUR_TTL = 86400;

    @BeforeEach
    void setUp() {
        rankingRepository = new FakeRankingRepository();
        rankingScoreService = new RankingScoreService(
            rankingRepository, VIEW_WEIGHT, LIKE_WEIGHT, ORDER_WEIGHT, DAY_TTL, HOUR_TTL
        );
    }

    private String todayDayKey() {
        return RankingKeyConstants.dayKey(LocalDate.now());
    }

    private String currentHourKey() {
        return RankingKeyConstants.hourKey(LocalDateTime.now());
    }

    @DisplayName("조회 이벤트 점수 반영할 때, ")
    @Nested
    class AddViewScore {

        @DisplayName("view weight만큼 점수가 반영된다.")
        @Test
        void incrementsByViewWeight() {
            rankingScoreService.addViewScore(101L);

            assertThat(rankingRepository.getScore(todayDayKey(), 101L))
                .isCloseTo(VIEW_WEIGHT, within(0.001));
            assertThat(rankingRepository.getScore(currentHourKey(), 101L))
                .isCloseTo(VIEW_WEIGHT, within(0.001));
        }

        @DisplayName("여러 번 호출하면 점수가 누적된다.")
        @Test
        void accumulatesOnMultipleCalls() {
            rankingScoreService.addViewScore(101L);
            rankingScoreService.addViewScore(101L);
            rankingScoreService.addViewScore(101L);

            assertThat(rankingRepository.getScore(todayDayKey(), 101L))
                .isCloseTo(VIEW_WEIGHT * 3, within(0.001));
        }
    }

    @DisplayName("좋아요 이벤트 점수 반영할 때, ")
    @Nested
    class AddLikeScore {

        @DisplayName("like weight만큼 점수가 반영된다.")
        @Test
        void incrementsByLikeWeight() {
            rankingScoreService.addLikeScore(101L);

            assertThat(rankingRepository.getScore(todayDayKey(), 101L))
                .isCloseTo(LIKE_WEIGHT, within(0.001));
        }
    }

    @DisplayName("주문 이벤트 점수 반영할 때, ")
    @Nested
    class AddOrderScores {

        @DisplayName("log10(price * quantity) * order weight로 점수가 반영된다.")
        @Test
        void incrementsByOrderWeightWithLog() {
            List<OrderItemPayload> items = List.of(
                new OrderItemPayload(101L, 2, 10000)
            );

            rankingScoreService.addOrderScores(items);

            double expected = ORDER_WEIGHT * Math.log10(10000.0 * 2);
            assertThat(rankingRepository.getScore(todayDayKey(), 101L))
                .isCloseTo(expected, within(0.001));
        }

        @DisplayName("같은 상품 여러 건이면 합산 후 반영된다.")
        @Test
        void aggregatesSameProductId() {
            List<OrderItemPayload> items = List.of(
                new OrderItemPayload(101L, 1, 5000),
                new OrderItemPayload(101L, 3, 8000)
            );

            rankingScoreService.addOrderScores(items);

            double expected = ORDER_WEIGHT * Math.log10(5000.0) + ORDER_WEIGHT * Math.log10(8000.0 * 3);
            assertThat(rankingRepository.getScore(todayDayKey(), 101L))
                .isCloseTo(expected, within(0.001));
        }

        @DisplayName("price가 0이면 log10(1) = 0으로 처리된다.")
        @Test
        void handlesZeroPrice() {
            List<OrderItemPayload> items = List.of(
                new OrderItemPayload(101L, 1, 0)
            );

            rankingScoreService.addOrderScores(items);

            double expected = ORDER_WEIGHT * Math.log10(1);
            assertThat(rankingRepository.getScore(todayDayKey(), 101L))
                .isCloseTo(expected, within(0.001));
        }

        @DisplayName("day와 hour 키 모두에 반영된다.")
        @Test
        void incrementsBothDayAndHourKeys() {
            List<OrderItemPayload> items = List.of(
                new OrderItemPayload(101L, 1, 10000)
            );

            rankingScoreService.addOrderScores(items);

            assertThat(rankingRepository.getScore(todayDayKey(), 101L)).isGreaterThan(0);
            assertThat(rankingRepository.getScore(currentHourKey(), 101L)).isGreaterThan(0);
        }
    }

    @DisplayName("배치 조회 점수 반영할 때, ")
    @Nested
    class AddViewScoresBatch {

        @DisplayName("같은 상품 3건이면 weight × 3으로 합산 반영된다.")
        @Test
        void aggregatesSameProduct() {
            Map<Long, Integer> counts = Map.of(101L, 3);

            rankingScoreService.addViewScores(counts);

            assertThat(rankingRepository.getScore(todayDayKey(), 101L))
                .isCloseTo(VIEW_WEIGHT * 3, within(0.001));
        }

        @DisplayName("다른 상품 2종이면 각각 반영된다.")
        @Test
        void handlesMultipleProducts() {
            Map<Long, Integer> counts = Map.of(101L, 2, 102L, 1);

            rankingScoreService.addViewScores(counts);

            assertThat(rankingRepository.getScore(todayDayKey(), 101L))
                .isCloseTo(VIEW_WEIGHT * 2, within(0.001));
            assertThat(rankingRepository.getScore(todayDayKey(), 102L))
                .isCloseTo(VIEW_WEIGHT, within(0.001));
        }
    }

    @DisplayName("배치 좋아요 점수 반영할 때, ")
    @Nested
    class AddLikeScoresBatch {

        @DisplayName("같은 상품 여러 건이면 weight × count로 합산 반영된다.")
        @Test
        void aggregatesSameProduct() {
            Map<Long, Integer> counts = Map.of(101L, 5);

            rankingScoreService.addLikeScores(counts);

            assertThat(rankingRepository.getScore(todayDayKey(), 101L))
                .isCloseTo(LIKE_WEIGHT * 5, within(0.001));
        }
    }

    @DisplayName("배치 좋아요 취소 점수 반영할 때, ")
    @Nested
    class SubtractLikeScoresBatch {

        @DisplayName("같은 상품 여러 건이면 -weight × count로 감소 반영된다.")
        @Test
        void subtractsSameProduct() {
            rankingScoreService.addLikeScores(Map.of(101L, 5));

            rankingScoreService.subtractLikeScores(Map.of(101L, 3));

            assertThat(rankingRepository.getScore(todayDayKey(), 101L))
                .isCloseTo(LIKE_WEIGHT * 2, within(0.001));
        }

        @DisplayName("좋아요 후 같은 수만큼 취소하면 점수가 0이 된다.")
        @Test
        void returnsToZeroAfterFullUnlike() {
            rankingScoreService.addLikeScores(Map.of(101L, 3));

            rankingScoreService.subtractLikeScores(Map.of(101L, 3));

            assertThat(rankingRepository.getScore(todayDayKey(), 101L))
                .isCloseTo(0.0, within(0.001));
        }
    }

    @DisplayName("TTL이 설정될 때, ")
    @Nested
    class TtlSetting {

        @DisplayName("day 키에는 day TTL이, hour 키에는 hour TTL이 설정된다.")
        @Test
        void setsCorrectTtl() {
            rankingScoreService.addViewScore(101L);

            assertThat(rankingRepository.getTtl(todayDayKey())).isEqualTo(DAY_TTL);
            assertThat(rankingRepository.getTtl(currentHourKey())).isEqualTo(HOUR_TTL);
        }
    }
}
