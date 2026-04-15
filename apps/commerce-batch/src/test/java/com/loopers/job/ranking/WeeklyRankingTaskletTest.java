package com.loopers.job.ranking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.test.util.ReflectionTestUtils;

import com.loopers.batch.job.ranking.step.WeeklyRankingTasklet;
import com.loopers.domain.metrics.ProductMetricsRepository;
import com.loopers.domain.metrics.ProductScoreProjection;
import com.loopers.domain.ranking.ProductRankingWeekly;
import com.loopers.domain.ranking.ProductRankingWeeklyRepository;

@ExtendWith(MockitoExtension.class)
class WeeklyRankingTaskletTest {

    @InjectMocks
    private WeeklyRankingTasklet tasklet;

    @Mock
    private ProductMetricsRepository productMetricsRepository;

    @Mock
    private ProductRankingWeeklyRepository productRankingWeeklyRepository;

    @DisplayName("주간 랭킹 집계를 수행할 때,")
    @Nested
    class Execute {

        @DisplayName("date 파라미터가 null이면, 예외가 발생한다.")
        @Test
        void throwsException_whenDateIsNull() {
            // arrange
            ReflectionTestUtils.setField(tasklet, "date", null);

            // act & assert
            assertThatThrownBy(() -> tasklet.execute(
                    mock(StepContribution.class), mock(ChunkContext.class)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("date");
        }

        @DisplayName("DB에서 조회한 Top N 스코어를 주간 랭킹으로 저장한다.")
        @Test
        @SuppressWarnings("unchecked")
        void savesTopNScoresAsWeeklyRanking() throws Exception {
            // arrange
            ReflectionTestUtils.setField(tasklet, "date", "20260413");

            List<ProductScoreProjection> topScores = List.of(
                    new ProductScoreProjection(1L, 46.5),
                    new ProductScoreProjection(2L, 8.1)
            );

            given(productMetricsRepository.findTopScores(any(), any(), eq(100)))
                    .willReturn(topScores);

            // act
            tasklet.execute(mock(StepContribution.class), mock(ChunkContext.class));

            // assert
            ArgumentCaptor<List<ProductRankingWeekly>> captor = ArgumentCaptor.forClass(List.class);
            verify(productRankingWeeklyRepository).saveAll(captor.capture());

            List<ProductRankingWeekly> rankings = captor.getValue();

            assertAll(
                    () -> assertThat(rankings).hasSize(2),
                    () -> {
                        ProductRankingWeekly product1 = rankings.stream()
                                .filter(r -> r.getProductId().equals(1L))
                                .findFirst().orElseThrow();
                        assertThat(product1.getScore()).isEqualTo(46.5);
                        assertThat(product1.getScoreDate()).isEqualTo(LocalDate.of(2026, 4, 13));
                    },
                    () -> {
                        ProductRankingWeekly product2 = rankings.stream()
                                .filter(r -> r.getProductId().equals(2L))
                                .findFirst().orElseThrow();
                        assertThat(product2.getScore()).isEqualTo(8.1);
                    }
            );
        }

        @DisplayName("집계 범위를 올바르게 계산한다 (date 기준 7일 전 ~ 전날).")
        @Test
        void calculatesCorrectDateRange() throws Exception {
            // arrange
            ReflectionTestUtils.setField(tasklet, "date", "20260413");

            given(productMetricsRepository.findTopScores(any(), any(), eq(100)))
                    .willReturn(List.of());

            // act
            tasklet.execute(mock(StepContribution.class), mock(ChunkContext.class));

            // assert
            ArgumentCaptor<LocalDate> startCaptor = ArgumentCaptor.forClass(LocalDate.class);
            ArgumentCaptor<LocalDate> endCaptor = ArgumentCaptor.forClass(LocalDate.class);
            verify(productMetricsRepository).findTopScores(startCaptor.capture(), endCaptor.capture(), eq(100));

            assertAll(
                    () -> assertThat(startCaptor.getValue()).isEqualTo(LocalDate.of(2026, 4, 6)),
                    () -> assertThat(endCaptor.getValue()).isEqualTo(LocalDate.of(2026, 4, 12))
            );
        }
    }
}
