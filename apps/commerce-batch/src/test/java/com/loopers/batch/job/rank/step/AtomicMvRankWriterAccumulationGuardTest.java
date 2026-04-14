package com.loopers.batch.job.rank.step;

import com.loopers.domain.rank.MvProductRankRepository;
import com.loopers.domain.rank.RankPeriodType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.item.Chunk;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

@DisplayName("AtomicMvRankWriter accumulated 상한 가드")
class AtomicMvRankWriterAccumulationGuardTest {

    private static final int LIMIT = 10_000;

    private AtomicMvRankWriter newWriter() {
        return new AtomicMvRankWriter(
                mock(MvProductRankRepository.class),
                RankPeriodType.WEEKLY,
                "2026W15",
                mock(TransactionTemplate.class)
        );
    }

    private Chunk<AggregatedScoreRow> rows(int count) {
        List<AggregatedScoreRow> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(new AggregatedScoreRow((long) i, 1.0, 1L, 1L, BigDecimal.ZERO));
        }
        return new Chunk<>(list);
    }

    @Test
    @DisplayName("정상 범위 내 누적은 예외 없이 진행")
    void withinLimit_noException() {
        AtomicMvRankWriter writer = newWriter();
        assertThatCode(() -> writer.write(rows(100))).doesNotThrowAnyException();
        assertThatCode(() -> writer.write(rows(100))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("단일 chunk가 상한 초과 시 IllegalStateException")
    void singleChunkOverLimit_throws() {
        AtomicMvRankWriter writer = newWriter();
        assertThatThrownBy(() -> writer.write(rows(LIMIT + 1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("accumulated 상한 초과");
    }

    @Test
    @DisplayName("누적 합이 상한 초과하는 시점에 IllegalStateException")
    void cumulativeOverLimit_throws() {
        AtomicMvRankWriter writer = newWriter();
        writer.write(rows(LIMIT - 50));
        assertThatThrownBy(() -> writer.write(rows(100)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("current=" + (LIMIT - 50))
                .hasMessageContaining("incoming=100");
    }
}
