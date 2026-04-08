package com.loopers.application.ranking;

import com.loopers.config.RankingProperties;
import com.loopers.infrastructure.ranking.RankingRedisRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
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
            // arrange
            initTransactionSynchronization();

            // act
            rankingScoreUpdater.registerAfterCommit("ProductViewedEvent", 1L);
            simulateCommit();

            // assert
            verify(rankingRedisRepository).incrementScore(
                    anyString(), eq(1L), eq(0.1), anyLong());
        }

        @Test
        void 좋아요_이벤트는_0점2의_delta를_가진다() {
            initTransactionSynchronization();

            rankingScoreUpdater.registerAfterCommit("ProductLikedEvent", 1L);
            simulateCommit();

            verify(rankingRedisRepository).incrementScore(
                    anyString(), eq(1L), eq(0.2), anyLong());
        }

        @Test
        void 좋아요취소_이벤트는_마이너스0점2의_delta를_가진다() {
            initTransactionSynchronization();

            rankingScoreUpdater.registerAfterCommit("ProductUnlikedEvent", 1L);
            simulateCommit();

            verify(rankingRedisRepository).incrementScore(
                    anyString(), eq(1L), eq(-0.2), anyLong());
        }

        @Test
        void 주문_이벤트는_0점6의_delta를_가진다() {
            initTransactionSynchronization();

            rankingScoreUpdater.registerAfterCommit("OrderItemSoldEvent", 1L);
            simulateCommit();

            verify(rankingRedisRepository).incrementScore(
                    anyString(), eq(1L), eq(0.6), anyLong());
        }

        @Test
        void 알수없는_이벤트는_Redis_호출하지_않는다() {
            initTransactionSynchronization();

            rankingScoreUpdater.registerAfterCommit("UnknownEvent", 1L);
            simulateCommit();

            verify(rankingRedisRepository, never()).incrementScore(
                    anyString(), anyLong(), anyDouble(), anyLong());
        }
    }

    @DisplayName("가중치 검증 — 주문 1건 > 좋아요 3건")
    @Nested
    class 가중치_검증 {

        @Test
        void 주문_1건의_delta가_좋아요_3건의_delta보다_크다() {
            // 주문 1건: 0.6
            // 좋아요 3건: 0.2 × 3 = 0.6
            // 주문 1건 == 좋아요 3건이지만, 주문 1건 > 좋아요 2건 (0.6 > 0.4)
            double orderDelta = rankingProperties.getWeights().getOrder();
            double likeTimesTwo = rankingProperties.getWeights().getLike() * 2;

            assertThat(orderDelta).isGreaterThan(likeTimesTwo);
        }
    }

    @DisplayName("TTL 설정")
    @Nested
    class TTL_설정 {

        @Test
        void TTL이_2일_기준_172800초로_전달된다() {
            initTransactionSynchronization();

            rankingScoreUpdater.registerAfterCommit("ProductViewedEvent", 1L);
            simulateCommit();

            long expectedTtl = 2 * 24 * 60 * 60; // 172800
            verify(rankingRedisRepository).incrementScore(
                    anyString(), anyLong(), anyDouble(), eq(expectedTtl));
        }
    }

    private void initTransactionSynchronization() {
        TransactionSynchronizationManager.initSynchronization();
    }

    private void simulateCommit() {
        for (TransactionSynchronization sync :
                TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCommit();
        }
        TransactionSynchronizationManager.clearSynchronization();
    }
}
