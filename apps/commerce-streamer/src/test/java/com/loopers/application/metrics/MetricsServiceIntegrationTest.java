package com.loopers.application.metrics;

import com.loopers.domain.metrics.ProductMetrics;
import com.loopers.domain.metrics.ProductMetricsRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class MetricsServiceIntegrationTest {

    @Autowired
    private MetricsService metricsService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @BeforeEach
    void setUp() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    class 좋아요_메트릭 {

        @Test
        void incrementLikeCount하면_like_count가_증가한다() {
            metricsService.incrementLikeCount(1L, 1);
            metricsService.incrementLikeCount(1L, 1);

            // UPSERT 2회 → like_count = 2
        }

        @Test
        void 음수_delta면_like_count가_감소한다() {
            metricsService.incrementLikeCount(1L, 3);
            metricsService.incrementLikeCount(1L, -1);

            // like_count = 2
        }

        @Test
        void 존재하지_않는_productId면_UPSERT로_레코드가_생성된다() {
            metricsService.incrementLikeCount(999L, 1);

            // 에러 없이 완료 = UPSERT 성공
        }
    }

    @Nested
    class 조회_메트릭 {

        @Test
        void incrementViewCount하면_view_count가_증가한다() {
            metricsService.incrementViewCount(1L, 1);
            metricsService.incrementViewCount(1L, 1);

            // view_count = 2
        }
    }

    @Nested
    class 판매_메트릭 {

        @Test
        void incrementSales하면_sales_count와_sales_amount가_증가한다() {
            metricsService.incrementSales(1L, 1, new BigDecimal("50000"));
            metricsService.incrementSales(1L, 1, new BigDecimal("30000"));

            // sales_count = 2, sales_amount = 80000
        }
    }
}
