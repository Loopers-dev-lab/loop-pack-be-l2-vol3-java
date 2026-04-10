package com.loopers.application.ranking;

import com.loopers.domain.metrics.ProductLikeMetricRepository;
import com.loopers.domain.metrics.ProductOrderMetricRepository;
import com.loopers.domain.metrics.ProductViewMetricRepository;
import com.loopers.domain.ranking.WeightConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class RankingAggregatorIntegrationTest {

    @Autowired
    private RankingAggregator aggregator;

    @Autowired
    private ProductViewMetricRepository viewMetricRepository;

    @Autowired
    private ProductLikeMetricRepository likeMetricRepository;

    @Autowired
    private ProductOrderMetricRepository orderMetricRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private static final LocalDateTime BUCKET = LocalDateTime.of(2026, 4, 9, 15, 0);
    private static final LocalDateTime FROM = LocalDateTime.of(2026, 4, 9, 15, 0);
    private static final LocalDateTime TO = LocalDateTime.of(2026, 4, 10, 15, 0);

    @BeforeEach
    void setUp() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    class 집계 {

        @Test
        void 조회_좋아요_주문을_가중합산하여_점수를_계산한다() {
            viewMetricRepository.upsert(1L, BUCKET, 500);
            likeMetricRepository.upsert(1L, BUCKET, 5);
            orderMetricRepository.upsert(1L, BUCKET, 1, 2, 20000);

            Map<Long, Double> result = aggregator.aggregate(FROM, TO);

            // 기본 가중치: 0.1*500 + 0.2*5 + 0.7*2 = 52.4
            assertThat(result.get(1L)).isCloseTo(52.4, within(0.001));
        }

        @Test
        void 여러_상품을_점수_내림차순으로_정렬한다() {
            viewMetricRepository.upsert(1L, BUCKET, 500);
            likeMetricRepository.upsert(1L, BUCKET, 5);
            orderMetricRepository.upsert(1L, BUCKET, 1, 2, 20000);

            viewMetricRepository.upsert(2L, BUCKET, 300);
            likeMetricRepository.upsert(2L, BUCKET, 20);
            orderMetricRepository.upsert(2L, BUCKET, 1, 10, 200000);

            Map<Long, Double> result = aggregator.aggregate(FROM, TO);

            assertAll(
                    () -> assertThat(result).hasSize(2),
                    () -> assertThat(result.keySet().stream().toList().get(0)).isEqualTo(1L),
                    () -> assertThat(result.get(1L)).isGreaterThan(result.get(2L))
            );
        }

        @Test
        void 커스텀_가중치로_점수를_계산한다() {
            viewMetricRepository.upsert(1L, BUCKET, 500);
            likeMetricRepository.upsert(1L, BUCKET, 5);
            orderMetricRepository.upsert(1L, BUCKET, 1, 2, 20000);

            WeightConfig viewHeavy = WeightConfig.create("exp", 0.5, 0.3, 0.2, 50);
            Map<Long, Double> result = aggregator.aggregate(FROM, TO, viewHeavy);

            // 0.5*500 + 0.3*5 + 0.2*2 = 251.9
            assertThat(result.get(1L)).isCloseTo(251.9, within(0.001));
        }

        @Test
        void 점수가_0인_상품은_제외한다() {
            // 모든 메트릭이 0인 상품은 결과에 포함되지 않음
            viewMetricRepository.upsert(1L, BUCKET, 100);

            Map<Long, Double> result = aggregator.aggregate(FROM, TO);

            assertThat(result).containsKey(1L);
            assertThat(result).doesNotContainKey(2L);
        }

        @Test
        void 범위_밖의_데이터는_집계에_포함되지_않는다() {
            LocalDateTime outOfRange = LocalDateTime.of(2026, 4, 8, 10, 0);
            viewMetricRepository.upsert(1L, outOfRange, 1000);
            viewMetricRepository.upsert(2L, BUCKET, 100);

            Map<Long, Double> result = aggregator.aggregate(FROM, TO);

            assertAll(
                    () -> assertThat(result).doesNotContainKey(1L),
                    () -> assertThat(result).containsKey(2L)
            );
        }

        @Test
        void 데이터가_없으면_빈_결과를_반환한다() {
            Map<Long, Double> result = aggregator.aggregate(FROM, TO);

            assertThat(result).isEmpty();
        }
    }
}
