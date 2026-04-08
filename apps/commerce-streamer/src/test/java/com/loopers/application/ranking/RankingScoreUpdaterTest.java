package com.loopers.application.ranking;

import com.loopers.config.RankingProperties;
import com.loopers.infrastructure.ranking.RankingRedisRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class RankingScoreUpdaterTest {

    private RankingRedisRepository rankingRedisRepository;
    private RankingProperties rankingProperties;
    private RankingScoreUpdater rankingScoreUpdater;

    @BeforeEach
    void setUp() {
        rankingRedisRepository = Mockito.mock(RankingRedisRepository.class);
        rankingProperties = new RankingProperties();
        rankingProperties.setKeyPrefix("ranking:all");
        rankingProperties.setTtlDays(2);
        rankingProperties.setHourlyKeyPrefix("ranking:hourly");
        rankingProperties.setHourlyTtlHours(4);

        RankingProperties.Weights weights = new RankingProperties.Weights();
        weights.setView(0.1);
        weights.setLike(0.2);
        weights.setUnlike(-0.2);
        weights.setOrder(0.6);
        rankingProperties.setWeights(weights);

        rankingScoreUpdater = new RankingScoreUpdater(rankingRedisRepository, rankingProperties);
    }

    @DisplayName("이벤트 타입별 delta 계산")
    @Nested
    class Delta_계산 {

        @Test
        void 조회_이벤트는_0점1의_delta를_가진다() {
            assertThat(rankingScoreUpdater.calculateDelta("ProductViewedEvent")).isEqualTo(0.1);
        }

        @Test
        void 좋아요_이벤트는_0점2의_delta를_가진다() {
            assertThat(rankingScoreUpdater.calculateDelta("ProductLikedEvent")).isEqualTo(0.2);
        }

        @Test
        void 좋아요취소_이벤트는_마이너스0점2의_delta를_가진다() {
            assertThat(rankingScoreUpdater.calculateDelta("ProductUnlikedEvent")).isEqualTo(-0.2);
        }

        @Test
        void 주문_이벤트는_0점6의_delta를_가진다() {
            assertThat(rankingScoreUpdater.calculateDelta("OrderItemSoldEvent")).isEqualTo(0.6);
        }

        @Test
        void 알수없는_이벤트는_0점0을_반환한다() {
            assertThat(rankingScoreUpdater.calculateDelta("UnknownEvent")).isEqualTo(0.0);
        }
    }

    @DisplayName("가중치 검증 — 주문 1건 > 좋아요 2건")
    @Nested
    class 가중치_검증 {

        @Test
        void 주문_1건의_delta가_좋아요_2건의_delta보다_크다() {
            double orderDelta = rankingScoreUpdater.calculateDelta("OrderItemSoldEvent");
            double likeTimesTwo = rankingScoreUpdater.calculateDelta("ProductLikedEvent") * 2;

            assertThat(orderDelta).isGreaterThan(likeTimesTwo);
        }
    }

    @DisplayName("배치 flush")
    @Nested
    class 배치_flush {

        @Test
        @SuppressWarnings("unchecked")
        void 일간_키와_시간_키_둘_다에_적재한다() {
            // arrange
            Map<Long, Double> scores = Map.of(1L, 0.3, 2L, 0.6);

            // act
            rankingScoreUpdater.flushBatch(scores);

            // assert — incrementScoreBatch 2회 호출 (daily + hourly)
            ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
            verify(rankingRedisRepository, Mockito.times(2))
                    .incrementScoreBatch(keyCaptor.capture(), eq(scores), anyLong());

            assertThat(keyCaptor.getAllValues().get(0)).startsWith("ranking:all:");
            assertThat(keyCaptor.getAllValues().get(1)).startsWith("ranking:hourly:");
        }

        @Test
        void 빈_Map이면_Redis_호출하지_않는다() {
            rankingScoreUpdater.flushBatch(Map.of());

            verify(rankingRedisRepository, never())
                    .incrementScoreBatch(anyString(), anyMap(), anyLong());
        }

        @Test
        void 일간_TTL은_2일_기준_172800초다() {
            Map<Long, Double> scores = Map.of(1L, 0.1);

            rankingScoreUpdater.flushBatch(scores);

            long expectedDailyTtl = 2 * 24 * 60 * 60; // 172800
            ArgumentCaptor<Long> ttlCaptor = ArgumentCaptor.forClass(Long.class);
            ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
            verify(rankingRedisRepository, Mockito.times(2))
                    .incrementScoreBatch(keyCaptor.capture(), eq(scores), ttlCaptor.capture());

            // 첫 번째 호출 = daily → 172800초
            assertThat(ttlCaptor.getAllValues().get(0)).isEqualTo(expectedDailyTtl);
            // 두 번째 호출 = hourly → 14400초
            long expectedHourlyTtl = 4 * 60 * 60; // 14400
            assertThat(ttlCaptor.getAllValues().get(1)).isEqualTo(expectedHourlyTtl);
        }
    }
}
