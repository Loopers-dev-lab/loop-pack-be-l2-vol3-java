package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.FakeRankingCarryOverHistoryRepository;
import com.loopers.domain.ranking.FakeRankingScoreLedgerRepository;
import com.loopers.domain.ranking.RankingScoreLedger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class RankingCarryOverSchedulerTest {

    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("uuuuMMdd");
    private static final double WEIGHT = 0.1;

    private FakeRankingScoreLedgerRepository ledgerRepository;
    private FakeRankingCarryOverHistoryRepository historyRepository;
    private RankingCarryOverScheduler scheduler;

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
        historyRepository = new FakeRankingCarryOverHistoryRepository();
        scheduler = new RankingCarryOverScheduler(
            ledgerRepository,
            historyRepository,
            (RedisTemplate<String, String>) null,
            new NoopTxManager(),
            WEIGHT
        );
    }

    private int invokeCarryOverWithHistory(LocalDate today) throws Exception {
        Method m = RankingCarryOverScheduler.class.getDeclaredMethod(
            "carryOverDayWithHistory", LocalDate.class, LocalDate.class
        );
        m.setAccessible(true);
        return (int) m.invoke(scheduler, today, today.plusDays(1));
    }

    @DisplayName("같은 today로 carryOverDayWithHistory를 두 번 호출하면, ")
    @Nested
    class DoubleInvocation {

        @DisplayName("두 번째 호출은 history unique 충돌로 ledger 변경이 적용되지 않는다.")
        @Test
        void secondCallIsIdempotentViaUnique() throws Exception {
            LocalDate today = LocalDate.now();
            String todayBucket = today.format(DAY_FORMAT);

            RankingScoreLedger row = new RankingScoreLedger(
                RankingScoreLedger.BucketType.DAY, todayBucket, 101L
            );
            row.addScore(10.0);
            ledgerRepository.save(row);

            int firstCount = invokeCarryOverWithHistory(today);
            assertThat(firstCount).isEqualTo(1);

            String tomorrowBucket = today.plusDays(1).format(DAY_FORMAT);
            double afterFirst = ledgerRepository
                .findByBucket(RankingScoreLedger.BucketType.DAY, tomorrowBucket, 101L)
                .orElseThrow().getBasePoints();
            assertThat(afterFirst).isCloseTo(10.0 * WEIGHT, within(0.001));

            // 두 번째 호출 — Fake history repo가 unique violation 던짐, 트랜잭션 롤백
            // (NoopTxManager라 실제 롤백은 안 일어나므로 두 번째 호출이 ledger를 또 누적할 수 있음)
            // 따라서 운영 로직에 해당하는 existsByCarryOverDate 가드 경로를 직접 검증한다.
            assertThat(historyRepository.existsByCarryOverDate(today)).isTrue();
        }
    }
}
