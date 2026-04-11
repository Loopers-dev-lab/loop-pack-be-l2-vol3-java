package com.loopers.application.ranking;

import com.loopers.domain.ranking.ProductDailySignalModel;
import com.loopers.domain.ranking.ProductDailySignalRepository;
import com.loopers.domain.ranking.RankingRepository;
import com.loopers.domain.ranking.RankingWeightProperties;
import com.loopers.domain.ranking.ScoreAggregator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("RankingRecalculationApp 단위 테스트")
class RankingRecalculationAppTest {

    private ProductDailySignalRepository productDailySignalRepository;
    private RankingRepository rankingRepository;
    private RankingRecalculationApp recalculationApp;

    private static final LocalDate DATE = LocalDate.of(2026, 4, 8);

    @BeforeEach
    void setUp() {
        productDailySignalRepository = mock(ProductDailySignalRepository.class);
        rankingRepository = mock(RankingRepository.class);
        RankingWeightProperties weights = new RankingWeightProperties(0.1, 0.2, 0.7);
        ScoreAggregator scoreAggregator = new ScoreAggregator(weights);
        recalculationApp = new RankingRecalculationApp(productDailySignalRepository, scoreAggregator, rankingRepository);
    }

    @Nested
    @DisplayName("recalculate()")
    class Recalculate {

        @Test
        @DisplayName("신호 데이터가 없으면 0을 반환하고 Shadow ZSET을 생성하지 않는다")
        void returnsZeroWhenNoSignals() {
            when(productDailySignalRepository.findBySignalDate(DATE)).thenReturn(Collections.emptyList());

            long result = recalculationApp.recalculate(DATE);

            assertThat(result).isEqualTo(0L);
            verify(rankingRepository, never()).addAllToShadow(any(), any());
            verify(rankingRepository, never()).renameShadowToMain(any());
        }

        @Test
        @DisplayName("DB 신호 기반으로 새 가중치로 점수를 재계산하여 Shadow → RENAME한다")
        @SuppressWarnings("unchecked")
        void recalculatesFromDbSignals() {
            ProductDailySignalModel signal = createSignal(42L, 100, 10, 5000.0);
            when(productDailySignalRepository.findBySignalDate(DATE)).thenReturn(List.of(signal));

            long result = recalculationApp.recalculate(DATE);

            assertThat(result).isEqualTo(1L);

            ArgumentCaptor<Map<Long, Double>> scoresCaptor = ArgumentCaptor.forClass(Map.class);
            verify(rankingRepository).addAllToShadow(eq(DATE), scoresCaptor.capture());
            verify(rankingRepository).renameShadowToMain(DATE);

            Map<Long, Double> scores = scoresCaptor.getValue();
            assertThat(scores).containsKey(42L);
            double expected = 0.1 * 100 + 0.2 * 10 + 0.7 * 5000.0;
            assertThat(scores.get(42L)).isCloseTo(expected, org.assertj.core.data.Offset.offset(0.001));
        }

        @Test
        @DisplayName("여러 상품의 점수를 각각 계산한다")
        @SuppressWarnings("unchecked")
        void recalculatesMultipleProducts() {
            ProductDailySignalModel signal1 = createSignal(1L, 50, 5, 10000.0);
            ProductDailySignalModel signal2 = createSignal(2L, 200, 20, 0.0);
            when(productDailySignalRepository.findBySignalDate(DATE)).thenReturn(List.of(signal1, signal2));

            long result = recalculationApp.recalculate(DATE);

            assertThat(result).isEqualTo(2L);

            ArgumentCaptor<Map<Long, Double>> scoresCaptor = ArgumentCaptor.forClass(Map.class);
            verify(rankingRepository).addAllToShadow(eq(DATE), scoresCaptor.capture());

            Map<Long, Double> scores = scoresCaptor.getValue();
            assertThat(scores).hasSize(2);
            assertThat(scores.get(1L)).isCloseTo(0.1 * 50 + 0.2 * 5 + 0.7 * 10000.0, org.assertj.core.data.Offset.offset(0.001));
            assertThat(scores.get(2L)).isCloseTo(0.1 * 200 + 0.2 * 20, org.assertj.core.data.Offset.offset(0.001));
        }
    }

    private ProductDailySignalModel createSignal(Long productDbId, long viewCount, long likeCount, double orderAmount) {
        try {
            java.lang.reflect.Constructor<ProductDailySignalModel> constructor =
                    ProductDailySignalModel.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            ProductDailySignalModel model = constructor.newInstance();
            java.lang.reflect.Field productDbIdField = ProductDailySignalModel.class.getDeclaredField("productDbId");
            productDbIdField.setAccessible(true);
            productDbIdField.set(model, productDbId);
            java.lang.reflect.Field viewCountField = ProductDailySignalModel.class.getDeclaredField("viewCount");
            viewCountField.setAccessible(true);
            viewCountField.set(model, viewCount);
            java.lang.reflect.Field likeCountField = ProductDailySignalModel.class.getDeclaredField("likeCount");
            likeCountField.setAccessible(true);
            likeCountField.set(model, likeCount);
            java.lang.reflect.Field orderAmountField = ProductDailySignalModel.class.getDeclaredField("orderAmount");
            orderAmountField.setAccessible(true);
            orderAmountField.set(model, java.math.BigDecimal.valueOf(orderAmount));
            java.lang.reflect.Field signalDateField = ProductDailySignalModel.class.getDeclaredField("signalDate");
            signalDateField.setAccessible(true);
            signalDateField.set(model, DATE);
            return model;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
