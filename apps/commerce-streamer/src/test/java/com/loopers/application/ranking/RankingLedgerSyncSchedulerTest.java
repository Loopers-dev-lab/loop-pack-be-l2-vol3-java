package com.loopers.application.ranking;

import com.loopers.domain.ranking.FakeRankingRepository;
import com.loopers.domain.ranking.FakeRankingScoreLedgerRepository;
import com.loopers.domain.ranking.RankingScoreEncoder;
import com.loopers.domain.ranking.RankingScoreLedger;
import com.loopers.support.redis.RankingKeyConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;

class RankingLedgerSyncSchedulerTest {

    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private FakeRankingScoreLedgerRepository ledgerRepository;
    private FakeRankingRepository rankingRepository;
    private RankingLedgerSyncScheduler scheduler;

    private static class NoopTxManager implements PlatformTransactionManager {
        @Override public TransactionStatus getTransaction(TransactionDefinition def) {
            return new SimpleTransactionStatus();
        }
        @Override public void commit(TransactionStatus status) { }
        @Override public void rollback(TransactionStatus status) { }
    }

    @BeforeEach
    void setUp() {
        ledgerRepository = new FakeRankingScoreLedgerRepository();
        rankingRepository = new FakeRankingRepository();
        scheduler = new RankingLedgerSyncScheduler(
            ledgerRepository,
            rankingRepository,
            (RedisTemplate<String, String>) null, // not used in forceSyncDay path
            new NoopTxManager(),
            172800L, 86400L, 500
        );
    }

    @DisplayName("forceSyncDay 호출 시, ")
    @Nested
    class ForceSyncDay {

        @DisplayName("동일 base points 두 행이 있으면 더 늦게 갱신된 쪽이 ZSET 상위로 올라가도록 composite score가 더 크다.")
        @Test
        void laterUpdatedRanksHigher() throws InterruptedException {
            LocalDate today = LocalDate.now();
            String bucketKey = today.format(DAY_FORMAT);

            // 둘 다 base points 1.0, 갱신 시각만 다르게
            RankingScoreLedger first = new RankingScoreLedger(
                RankingScoreLedger.BucketType.DAY, bucketKey, 101L
            );
            first.addScore(1.0);
            ledgerRepository.save(first);

            Thread.sleep(1100); // 최소 1초 차이 (epochSecond 단위)

            RankingScoreLedger second = new RankingScoreLedger(
                RankingScoreLedger.BucketType.DAY, bucketKey, 102L
            );
            second.addScore(1.0);
            ledgerRepository.save(second);

            scheduler.forceSyncDay(today);

            String redisKey = RankingKeyConstants.dayKey(today);
            double scoreFirst = rankingRepository.getScore(redisKey, 101L);
            double scoreSecond = rankingRepository.getScore(redisKey, 102L);

            assertThat(scoreSecond).isGreaterThan(scoreFirst);
        }

        @DisplayName("동기화 후 모든 ledger 행이 dirty=false 가 된다.")
        @Test
        void marksRowsClean() {
            LocalDate today = LocalDate.now();
            String bucketKey = today.format(DAY_FORMAT);

            RankingScoreLedger row = new RankingScoreLedger(
                RankingScoreLedger.BucketType.DAY, bucketKey, 101L
            );
            row.addScore(1.0);
            ledgerRepository.save(row);
            assertThat(row.isDirty()).isTrue();

            scheduler.forceSyncDay(today);

            assertThat(
                ledgerRepository.findByBucket(RankingScoreLedger.BucketType.DAY, bucketKey, 101L)
                    .orElseThrow().isDirty()
            ).isFalse();
        }

        @DisplayName("composite score를 디코딩하면 base points와 근사하다.")
        @Test
        void compositeScoreDecodesToBase() {
            LocalDate today = LocalDate.now();
            String bucketKey = today.format(DAY_FORMAT);

            RankingScoreLedger row = new RankingScoreLedger(
                RankingScoreLedger.BucketType.DAY, bucketKey, 101L
            );
            row.addScore(12.34);
            ledgerRepository.save(row);

            scheduler.forceSyncDay(today);

            String redisKey = RankingKeyConstants.dayKey(today);
            double composite = rankingRepository.getScore(redisKey, 101L);
            assertThat(RankingScoreEncoder.decodeBase(composite)).isCloseTo(12.34, org.assertj.core.api.Assertions.within(0.001));
        }
    }
}
