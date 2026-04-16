package com.loopers.job.monthly.step;

import com.loopers.batch.job.monthly.step.MonthlyRankingItemWriter;
import com.loopers.domain.ranking.MvProductRankRepository;
import com.loopers.domain.ranking.MvProductRankRow;
import com.loopers.domain.ranking.ProductMetricsAggregate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.item.Chunk;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@DisplayName("MonthlyRankingItemWriter 단위 테스트")
class MonthlyRankingItemWriterTest {

    private MvProductRankRepository mvProductRankRepository;
    private MonthlyRankingItemWriter writer;

    @BeforeEach
    void setUp() {
        mvProductRankRepository = mock(MvProductRankRepository.class);
        writer = new MonthlyRankingItemWriter(mvProductRankRepository);
        ReflectionTestUtils.setField(writer, "targetDate", LocalDate.of(2026, 4, 11));
    }

    @Test
    @DisplayName("write() 가 두 번 호출되면 rank 가 연속되고 replaceMonthlyRanking 은 afterStep() 에서 1회만 호출된다")
    void multipleWriteCallsProducesContinuousRanks() throws Exception {
        // given
        Chunk<ProductMetricsAggregate> chunk1 = new Chunk<>(List.of(
                new ProductMetricsAggregate(1L, 5.0),
                new ProductMetricsAggregate(2L, 4.0)
        ));
        Chunk<ProductMetricsAggregate> chunk2 = new Chunk<>(List.of(
                new ProductMetricsAggregate(3L, 3.0)
        ));

        // when
        writer.write(chunk1);
        writer.write(chunk2);
        writer.afterStep(mock(StepExecution.class));

        // then
        ArgumentCaptor<List<MvProductRankRow>> captor = ArgumentCaptor.forClass(List.class);
        verify(mvProductRankRepository, times(1))
                .replaceMonthlyRanking(org.mockito.ArgumentMatchers.eq(LocalDate.of(2026, 4, 10)), captor.capture());

        List<MvProductRankRow> rows = captor.getValue();
        assertAll(
                () -> assertThat(rows).hasSize(3),
                () -> assertThat(rows.get(0).rank()).isEqualTo(1),
                () -> assertThat(rows.get(1).rank()).isEqualTo(2),
                () -> assertThat(rows.get(2).rank()).isEqualTo(3)
        );
    }
}
