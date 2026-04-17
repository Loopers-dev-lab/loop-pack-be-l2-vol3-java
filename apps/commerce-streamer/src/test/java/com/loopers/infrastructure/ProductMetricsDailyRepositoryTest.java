package com.loopers.infrastructure;

import com.loopers.domain.ProductMetricsDaily;
import com.loopers.domain.ProductMetricsDailyId;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
@EmbeddedKafka(
        partitions = 1,
        brokerProperties = {"listeners=PLAINTEXT://localhost:0"},
        topics = {"catalog-events", "order-events", "coupon-issue-requests"}
)
class ProductMetricsDailyRepositoryTest {

    @Autowired
    private ProductMetricsDailyRepository productMetricsDailyRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private static final Long PRODUCT_ID = 1L;
    private static final LocalDate TODAY = LocalDate.of(2026, 4, 14);
    private static final LocalDate YESTERDAY = LocalDate.of(2026, 4, 13);

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("같은 상품 + 같은 날짜에 viewCount를 여러 번 증가시키면 값이 누적된다")
    @Test
    void incrementViewCount_sameProductSameDate_accumulates() {
        // arrange & act
        transactionTemplate.executeWithoutResult(status -> {
            productMetricsDailyRepository.incrementViewCount(PRODUCT_ID, TODAY);
            productMetricsDailyRepository.incrementViewCount(PRODUCT_ID, TODAY);
            productMetricsDailyRepository.incrementViewCount(PRODUCT_ID, TODAY);
        });

        // assert
        ProductMetricsDaily result = findMetrics(PRODUCT_ID, TODAY);
        assertThat(result.getViewCount()).isEqualTo(3);
    }

    @DisplayName("같은 상품 + 다른 날짜는 별도 행으로 생성된다")
    @Test
    void incrementViewCount_sameProductDifferentDate_createsSeparateRows() {
        // arrange & act
        transactionTemplate.executeWithoutResult(status -> {
            productMetricsDailyRepository.incrementViewCount(PRODUCT_ID, TODAY);
            productMetricsDailyRepository.incrementViewCount(PRODUCT_ID, YESTERDAY);
        });

        // assert
        ProductMetricsDaily todayResult = findMetrics(PRODUCT_ID, TODAY);
        ProductMetricsDaily yesterdayResult = findMetrics(PRODUCT_ID, YESTERDAY);
        assertAll(
                () -> assertThat(todayResult.getViewCount()).isEqualTo(1),
                () -> assertThat(yesterdayResult.getViewCount()).isEqualTo(1)
        );
    }

    @DisplayName("likeCount를 증가시키면 해당 날짜의 좋아요 수가 누적된다")
    @Test
    void incrementLikeCount_accumulates() {
        // arrange & act
        transactionTemplate.executeWithoutResult(status -> {
            productMetricsDailyRepository.incrementLikeCount(PRODUCT_ID, TODAY);
            productMetricsDailyRepository.incrementLikeCount(PRODUCT_ID, TODAY);
        });

        // assert
        ProductMetricsDaily result = findMetrics(PRODUCT_ID, TODAY);
        assertThat(result.getLikeCount()).isEqualTo(2);
    }

    @DisplayName("decrementLikeCount는 음수를 허용한다 (delta 방식)")
    @Test
    void decrementLikeCount_allowsNegative() {
        // arrange & act
        transactionTemplate.executeWithoutResult(status ->
                productMetricsDailyRepository.decrementLikeCount(PRODUCT_ID, TODAY)
        );

        // assert
        ProductMetricsDaily result = findMetrics(PRODUCT_ID, TODAY);
        assertThat(result.getLikeCount()).isEqualTo(-1);
    }

    @DisplayName("like 후 unlike 하면 likeCount가 상쇄되어 0이 된다")
    @Test
    void likeAndUnlike_cancelsOut() {
        // arrange & act
        transactionTemplate.executeWithoutResult(status -> {
            productMetricsDailyRepository.incrementLikeCount(PRODUCT_ID, TODAY);
            productMetricsDailyRepository.decrementLikeCount(PRODUCT_ID, TODAY);
        });

        // assert
        ProductMetricsDaily result = findMetrics(PRODUCT_ID, TODAY);
        assertThat(result.getLikeCount()).isEqualTo(0);
    }

    @DisplayName("주문 라인 집계 시 orderLineCount와 orderAmount가 함께 누적된다")
    @Test
    void incrementOrderLineCountAndAmount_accumulates() {
        // arrange & act
        transactionTemplate.executeWithoutResult(status -> {
            productMetricsDailyRepository.incrementOrderLineCountAndAmount(PRODUCT_ID, TODAY, 50000);
            productMetricsDailyRepository.incrementOrderLineCountAndAmount(PRODUCT_ID, TODAY, 30000);
        });

        // assert
        ProductMetricsDaily result = findMetrics(PRODUCT_ID, TODAY);
        assertAll(
                () -> assertThat(result.getOrderLineCount()).isEqualTo(2),
                () -> assertThat(result.getOrderAmount()).isEqualTo(80000)
        );
    }

    @DisplayName("하나의 주문 라인에 상품 수량이 여러 개여도 orderLineCount는 1만 증가한다")
    @Test
    void incrementOrderLineCountAndAmount_countsOnePerOrderLine() {
        // arrange
        int unitPrice = 3000;
        int quantity = 3;
        long orderAmount = unitPrice * quantity;

        // act
        transactionTemplate.executeWithoutResult(status ->
                productMetricsDailyRepository.incrementOrderLineCountAndAmount(PRODUCT_ID, TODAY, orderAmount)
        );

        // assert
        ProductMetricsDaily result = findMetrics(PRODUCT_ID, TODAY);
        assertAll(
                () -> assertThat(result.getOrderLineCount()).isEqualTo(1),
                () -> assertThat(result.getOrderAmount()).isEqualTo(9000)
        );
    }

    @DisplayName("조회, 좋아요, 주문 라인을 같은 날짜에 집계하면 각 메트릭이 각각 반영된다")
    @Test
    void multipleMetrics_workIndependently() {
        // arrange & act
        transactionTemplate.executeWithoutResult(status -> {
            productMetricsDailyRepository.incrementViewCount(PRODUCT_ID, TODAY);
            productMetricsDailyRepository.incrementLikeCount(PRODUCT_ID, TODAY);
            productMetricsDailyRepository.incrementOrderLineCountAndAmount(PRODUCT_ID, TODAY, 10000);
        });

        // assert
        ProductMetricsDaily result = findMetrics(PRODUCT_ID, TODAY);
        assertAll(
                () -> assertThat(result.getViewCount()).isEqualTo(1),
                () -> assertThat(result.getLikeCount()).isEqualTo(1),
                () -> assertThat(result.getOrderLineCount()).isEqualTo(1),
                () -> assertThat(result.getOrderAmount()).isEqualTo(10000)
        );
    }

    private ProductMetricsDaily findMetrics(Long productId, LocalDate date) {
        Optional<ProductMetricsDaily> opt = productMetricsDailyRepository.findById(
                new ProductMetricsDailyId(date, productId)
        );
        assertThat(opt).isPresent();
        return opt.get();
    }
}
