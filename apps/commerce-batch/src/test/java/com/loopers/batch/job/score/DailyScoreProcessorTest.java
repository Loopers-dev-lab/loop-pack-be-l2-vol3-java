package com.loopers.batch.job.score;

import com.loopers.batch.job.score.step.DailyScoreProcessor;
import com.loopers.batch.job.score.step.MvProductScoreDailyRow;
import com.loopers.domain.rank.RankScoreCalculator;
import com.loopers.domain.rank.RankingWeightProperties;
import com.loopers.domain.signal.ProductDailySignalModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class DailyScoreProcessorTest {

    private final RankScoreCalculator calculator = new RankScoreCalculator(
            new RankingWeightProperties(0.1, 0.2, 0.7)
    );
    private final LocalDate targetDate = LocalDate.of(2026, 4, 11);
    private final DailyScoreProcessor processor = new DailyScoreProcessor(calculator, targetDate);

    @DisplayName("정상 입력 시 score가 계산된 Row를 반환한다")
    @Test
    void process_normalInput() {
        // arrange
        ProductDailySignalModel signal = createSignal(1L, 100, 50, BigDecimal.valueOf(1000));

        // act
        MvProductScoreDailyRow result = processor.process(signal);

        // assert
        assertThat(result).isNotNull();
        assertThat(result.productDbId()).isEqualTo(1L);
        assertThat(result.scoreDate()).isEqualTo(targetDate);
        assertThat(result.score()).isCloseTo(720.0, within(0.001));
        assertThat(result.viewCount()).isEqualTo(100);
        assertThat(result.likeCount()).isEqualTo(50);
        assertThat(result.orderAmount()).isEqualByComparingTo(BigDecimal.valueOf(1000));
    }

    @DisplayName("score가 0이면 null을 반환한다 (필터링)")
    @Test
    void process_zeroScore_returnsNull() {
        // arrange
        ProductDailySignalModel signal = createSignal(1L, 0, 0, BigDecimal.ZERO);

        // act
        MvProductScoreDailyRow result = processor.process(signal);

        // assert
        assertThat(result).isNull();
    }

    @DisplayName("targetDate가 Row에 정확히 전달된다")
    @Test
    void process_targetDatePropagated() {
        // arrange
        LocalDate customDate = LocalDate.of(2026, 1, 15);
        DailyScoreProcessor customProcessor = new DailyScoreProcessor(calculator, customDate);
        ProductDailySignalModel signal = createSignal(1L, 10, 5, BigDecimal.valueOf(100));

        // act
        MvProductScoreDailyRow result = customProcessor.process(signal);

        // assert
        assertThat(result).isNotNull();
        assertThat(result.scoreDate()).isEqualTo(customDate);
    }

    private ProductDailySignalModel createSignal(Long productDbId, long viewCount, long likeCount, BigDecimal orderAmount) {
        try {
            java.lang.reflect.Constructor<ProductDailySignalModel> constructor =
                    ProductDailySignalModel.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            ProductDailySignalModel signal = constructor.newInstance();
            ReflectionTestUtils.setField(signal, "productDbId", productDbId);
            ReflectionTestUtils.setField(signal, "signalDate", targetDate);
            ReflectionTestUtils.setField(signal, "viewCount", viewCount);
            ReflectionTestUtils.setField(signal, "likeCount", likeCount);
            ReflectionTestUtils.setField(signal, "orderAmount", orderAmount);
            return signal;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
