package com.loopers.domain.ranking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.loopers.domain.ranking.dto.RankingInfo;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.data.domain.PageRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RankingScoreServiceTest {

    private RankingScoreService rankingScoreService;
    private FakeProductRankingScoreRepository fakeRepository;

    private static final LocalDate TODAY = LocalDate.of(2026, 4, 5);

    @BeforeEach
    void setUp() {
        fakeRepository = new FakeProductRankingScoreRepository();
        rankingScoreService = new RankingScoreService(fakeRepository);
    }

    @DisplayName("랭킹 점수를 갱신할 때, ")
    @Nested
    class UpdateScore {

        @DisplayName("동일 상품에 여러 이벤트가 발생하면 점수가 누적된다.")
        @Test
        void updateScore_whenMultipleEvents_thenAccumulated() {
            // act
            rankingScoreService.updateScore(1L, TODAY, RankingEventType.VIEW, 0);
            rankingScoreService.updateScore(1L, TODAY, RankingEventType.LIKE, 0);

            // assert
            ProductRankingScoreModel score = fakeRepository
                    .findByProductIdAndRankingDate(1L, TODAY).orElseThrow();
            assertThat(score.getScore()).isCloseTo(0.3, within(0.0001));
        }

        @DisplayName("서로 다른 상품의 이벤트는 각각 독립적으로 반영된다.")
        @Test
        void updateScore_whenDifferentProducts_thenIndependent() {
            // act
            rankingScoreService.updateScore(1L, TODAY, RankingEventType.VIEW, 0);
            rankingScoreService.updateScore(2L, TODAY, RankingEventType.LIKE, 0);

            // assert
            ProductRankingScoreModel score1 = fakeRepository
                    .findByProductIdAndRankingDate(1L, TODAY).orElseThrow();
            ProductRankingScoreModel score2 = fakeRepository
                    .findByProductIdAndRankingDate(2L, TODAY).orElseThrow();
            assertThat(score1.getScore()).isCloseTo(0.1, within(0.0001));
            assertThat(score2.getScore()).isCloseTo(0.2, within(0.0001));
        }

        @DisplayName("주문 1건이 좋아요 여러 건보다 높은 순위를 갖는다.")
        @Test
        void updateScore_whenOrderVsLikes_thenOrderWins() {
            // arrange - 상품1: 주문 1건 (10000원)
            rankingScoreService.updateScore(1L, TODAY, RankingEventType.ORDER, 10000);
            // arrange - 상품2: 좋아요 10건
            for (int i = 0; i < 10; i++) {
                rankingScoreService.updateScore(2L, TODAY, RankingEventType.LIKE, 0);
            }

            // assert
            ProductRankingScoreModel orderScore = fakeRepository
                    .findByProductIdAndRankingDate(1L, TODAY).orElseThrow();
            ProductRankingScoreModel likeScore = fakeRepository
                    .findByProductIdAndRankingDate(2L, TODAY).orElseThrow();
            assertThat(orderScore.getScore()).isGreaterThan(likeScore.getScore());
        }

        @DisplayName("날짜가 다르면 서로 다른 집계에 점수가 적재된다.")
        @Test
        void updateScore_whenDifferentDates_thenSeparateAggregation() {
            // arrange
            LocalDate yesterday = TODAY.minusDays(1);

            // act
            rankingScoreService.updateScore(1L, TODAY, RankingEventType.VIEW, 0);
            rankingScoreService.updateScore(1L, yesterday, RankingEventType.LIKE, 0);

            // assert
            ProductRankingScoreModel todayScore = fakeRepository
                    .findByProductIdAndRankingDate(1L, TODAY).orElseThrow();
            ProductRankingScoreModel yesterdayScore = fakeRepository
                    .findByProductIdAndRankingDate(1L, yesterday).orElseThrow();
            assertThat(todayScore.getScore()).isCloseTo(0.1, within(0.0001));
            assertThat(yesterdayScore.getScore()).isCloseTo(0.2, within(0.0001));
        }
    }

    @DisplayName("순위가 포함된 랭킹을 조회할 때, ")
    @Nested
    class GetTopRankedByDate {

        @DisplayName("동점 상품은 같은 순위를 갖는다.")
        @Test
        void getTopRankedByDate_whenTiedScores_thenSameRank() {
            // arrange
            rankingScoreService.updateScore(1L, TODAY, RankingEventType.ORDER, 10000);
            rankingScoreService.updateScore(2L, TODAY, RankingEventType.ORDER, 10000);
            rankingScoreService.updateScore(3L, TODAY, RankingEventType.VIEW, 0);

            // act
            List<RankingInfo.RankedScore> ranked = rankingScoreService
                    .getTopRankedByDate(TODAY, PageRequest.of(0, 10));

            // assert
            assertThat(ranked).hasSize(3);
            assertThat(ranked.get(0).rank()).isEqualTo(1);
            assertThat(ranked.get(1).rank()).isEqualTo(1);
            assertThat(ranked.get(2).rank()).isEqualTo(3);
        }
    }

    @DisplayName("콜드 스타트 이월을 실행할 때, ")
    @Nested
    class CarryOver {

        @DisplayName("전날 점수의 지정 비율이 오늘에 이월된다.")
        @Test
        void carryOver_whenYesterdayHasScores_thenCarriedToToday() {
            // arrange
            LocalDate yesterday = TODAY.minusDays(1);
            rankingScoreService.updateScore(1L, yesterday, RankingEventType.ORDER, 10000);

            // act
            rankingScoreService.carryOver(TODAY, 0.1);

            // assert
            ProductRankingScoreModel todayScore = fakeRepository
                    .findByProductIdAndRankingDate(1L, TODAY).orElseThrow();
            assertThat(todayScore.getScore()).isCloseTo(700.0, within(0.0001));
        }

        @DisplayName("이월 후 오늘 이벤트가 발생하면 이월 점수 위에 누적된다.")
        @Test
        void carryOver_whenTodayEventAfter_thenAccumulated() {
            // arrange
            LocalDate yesterday = TODAY.minusDays(1);
            rankingScoreService.updateScore(1L, yesterday, RankingEventType.ORDER, 10000);
            rankingScoreService.carryOver(TODAY, 0.1);

            // act
            rankingScoreService.updateScore(1L, TODAY, RankingEventType.VIEW, 0);

            // assert
            ProductRankingScoreModel todayScore = fakeRepository
                    .findByProductIdAndRankingDate(1L, TODAY).orElseThrow();
            assertThat(todayScore.getScore()).isCloseTo(700.1, within(0.0001));
        }

        @DisplayName("이월을 중복 실행해도 점수가 이중 반영되지 않는다.")
        @Test
        void carryOver_whenExecutedTwice_thenIdempotent() {
            // arrange
            LocalDate yesterday = TODAY.minusDays(1);
            rankingScoreService.updateScore(1L, yesterday, RankingEventType.ORDER, 10000);

            // act
            rankingScoreService.carryOver(TODAY, 0.1);
            rankingScoreService.carryOver(TODAY, 0.1);

            // assert
            ProductRankingScoreModel todayScore = fakeRepository
                    .findByProductIdAndRankingDate(1L, TODAY).orElseThrow();
            assertThat(todayScore.getScore()).isCloseTo(700.0, within(0.0001));
        }
    }
}
