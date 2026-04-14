package com.loopers.job.ranking;

import com.loopers.batch.job.ranking.RankingAggregationJobConfig;
import com.loopers.domain.ranking.ProductRankSnapshot;
import com.loopers.domain.ranking.RankingType;
import com.loopers.infrastructure.ranking.ProductRankSnapshotJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
@SpringBatchTest
@TestPropertySource(properties = {
        "spring.batch.job.name=" + RankingAggregationJobConfig.JOB_NAME,
        "spring.batch.job.enabled=false"
})
class RankingAggregationJobE2ETest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    @Qualifier(RankingAggregationJobConfig.JOB_NAME)
    private Job job;

    @Autowired
    private ProductRankSnapshotJpaRepository productRankSnapshotJpaRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private static final LocalDate END_DATE = LocalDate.of(2026, 4, 14);

    @BeforeEach
    void setUp() {
        jobLauncherTestUtils.setJob(job);
        createExternalTables();
    }

    @AfterEach
    void tearDown() {
        truncateExternalTables();
        databaseCleanUp.truncateAllTables();
        cleanUpBatchMetadata();
    }

    private void createExternalTables() {
        transactionTemplate.executeWithoutResult(status -> {
            entityManager.createNativeQuery("""
                    CREATE TABLE IF NOT EXISTS brands (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        name VARCHAR(255) NOT NULL,
                        description VARCHAR(255),
                        created_at DATETIME(6) NOT NULL,
                        updated_at DATETIME(6) NOT NULL,
                        deleted_at DATETIME(6)
                    )
                    """).executeUpdate();

            entityManager.createNativeQuery("""
                    CREATE TABLE IF NOT EXISTS products (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        brand_id BIGINT NOT NULL,
                        name VARCHAR(255) NOT NULL,
                        description VARCHAR(255),
                        price INT NOT NULL,
                        stock_quantity INT NOT NULL DEFAULT 0,
                        visibility VARCHAR(20) NOT NULL,
                        created_at DATETIME(6) NOT NULL,
                        updated_at DATETIME(6) NOT NULL,
                        deleted_at DATETIME(6)
                    )
                    """).executeUpdate();

            entityManager.createNativeQuery("""
                    CREATE TABLE IF NOT EXISTS product_metrics_daily (
                        product_id BIGINT NOT NULL,
                        metric_date DATE NOT NULL,
                        view_count BIGINT NOT NULL DEFAULT 0,
                        like_count BIGINT NOT NULL DEFAULT 0,
                        order_line_count BIGINT NOT NULL DEFAULT 0,
                        order_amount BIGINT NOT NULL DEFAULT 0,
                        PRIMARY KEY (metric_date, product_id)
                    )
                    """).executeUpdate();
        });
    }

    private void truncateExternalTables() {
        transactionTemplate.executeWithoutResult(status -> {
            entityManager.createNativeQuery("SET FOREIGN_KEY_CHECKS = 0").executeUpdate();
            entityManager.createNativeQuery("TRUNCATE TABLE product_metrics_daily").executeUpdate();
            entityManager.createNativeQuery("TRUNCATE TABLE products").executeUpdate();
            entityManager.createNativeQuery("TRUNCATE TABLE brands").executeUpdate();
            entityManager.createNativeQuery("SET FOREIGN_KEY_CHECKS = 1").executeUpdate();
        });
    }

    private void cleanUpBatchMetadata() {
        transactionTemplate.executeWithoutResult(status -> {
            entityManager.createNativeQuery("DELETE FROM BATCH_STEP_EXECUTION_CONTEXT").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM BATCH_STEP_EXECUTION").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM BATCH_JOB_EXECUTION_CONTEXT").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM BATCH_JOB_EXECUTION_PARAMS").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM BATCH_JOB_EXECUTION").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM BATCH_JOB_INSTANCE").executeUpdate();
        });
    }

    @DisplayName("WEEKLY 타입: 최근 7일 범위의 일별 메트릭을 집계하여 랭킹을 생성한다")
    @Test
    void weeklyAggregation_aggregates7Days() throws Exception {
        // arrange
        seedTestData();

        // act
        var jobExecution = jobLauncherTestUtils.launchJob(
                new JobParametersBuilder()
                        .addString("rankingType", "WEEKLY")
                        .addLocalDate("endDate", END_DATE)
                        .toJobParameters()
        );

        // assert
        assertThat(jobExecution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode());

        List<ProductRankSnapshot> results = productRankSnapshotJpaRepository.findAll();
        assertAll(
                () -> assertThat(results).isNotEmpty(),
                () -> assertThat(results).allMatch(r -> r.getRankingType() == RankingType.WEEKLY),
                () -> assertThat(results).allMatch(r -> r.getRankDate().equals(END_DATE)),
                () -> assertThat(results.get(0).getRankPosition()).isEqualTo(1),
                () -> assertThat(results.get(0).getScore()).isGreaterThan(results.get(1).getScore())
        );
    }

    @DisplayName("MONTHLY 타입: 최근 30일 범위의 일별 메트릭을 집계하여 랭킹을 생성한다")
    @Test
    void monthlyAggregation_aggregates30Days() throws Exception {
        // arrange
        seedTestDataForMonthly();

        // act
        var jobExecution = jobLauncherTestUtils.launchJob(
                new JobParametersBuilder()
                        .addString("rankingType", "MONTHLY")
                        .addLocalDate("endDate", END_DATE)
                        .toJobParameters()
        );

        // assert
        assertThat(jobExecution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode());

        List<ProductRankSnapshot> results = productRankSnapshotJpaRepository.findAll();
        assertAll(
                () -> assertThat(results).isNotEmpty(),
                () -> assertThat(results).allMatch(r -> r.getRankingType() == RankingType.MONTHLY),
                () -> assertThat(results).allMatch(r -> r.getRankDate().equals(END_DATE))
        );
    }

    @DisplayName("멱등성: 같은 파라미터로 2회 실행해도 중복 데이터 없이 동일 결과를 반환한다")
    @Test
    void idempotency_sameParamsTwice_noDuplicates() throws Exception {
        // arrange
        seedTestData();

        // act - 1차 실행
        jobLauncherTestUtils.launchJob(
                new JobParametersBuilder()
                        .addString("rankingType", "WEEKLY")
                        .addLocalDate("endDate", END_DATE)
                        .toJobParameters()
        );
        List<ProductRankSnapshot> firstRunResults = productRankSnapshotJpaRepository.findAll()
                .stream()
                .sorted(Comparator.comparing(ProductRankSnapshot::getRankPosition))
                .toList();

        // 2차 실행을 위해 배치 메타데이터 초기화 (같은 파라미터로 재실행 허용)
        cleanUpBatchMetadata();

        // act - 2차 실행
        var jobExecution = jobLauncherTestUtils.launchJob(
                new JobParametersBuilder()
                        .addString("rankingType", "WEEKLY")
                        .addLocalDate("endDate", END_DATE)
                        .toJobParameters()
        );

        // assert
        assertAll(
                () -> assertThat(jobExecution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode()),
                () -> assertThat(productRankSnapshotJpaRepository.findAll()
                        .stream()
                        .sorted(Comparator.comparing(ProductRankSnapshot::getRankPosition))
                        .toList())
                        .usingRecursiveFieldByFieldElementComparatorIgnoringFields("id")
                        .containsExactlyElementsOf(firstRunResults)
        );
    }

    @DisplayName("삭제된 상품은 랭킹에서 제외된다")
    @Test
    void excludesDeletedProducts() throws Exception {
        // arrange
        seedTestDataWithDeletedProduct();

        // act
        var jobExecution = jobLauncherTestUtils.launchJob(
                new JobParametersBuilder()
                        .addString("rankingType", "WEEKLY")
                        .addLocalDate("endDate", END_DATE)
                        .toJobParameters()
        );

        // assert
        assertThat(jobExecution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode());

        List<ProductRankSnapshot> results = productRankSnapshotJpaRepository.findAll();
        assertThat(results).noneMatch(r -> r.getProductId().equals(99L));
    }

    @DisplayName("순위는 score 내림차순으로 정렬된다")
    @Test
    void rankOrderedByScoreDescending() throws Exception {
        // arrange
        seedTestData();

        // act
        var jobExecution = jobLauncherTestUtils.launchJob(
                new JobParametersBuilder()
                        .addString("rankingType", "WEEKLY")
                        .addLocalDate("endDate", END_DATE)
                        .toJobParameters()
        );

        // assert
        assertThat(jobExecution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode());

        List<ProductRankSnapshot> results = productRankSnapshotJpaRepository.findAll()
                .stream()
                .sorted(Comparator.comparing(ProductRankSnapshot::getRankPosition))
                .toList();
        for (int i = 0; i < results.size() - 1; i++) {
            assertThat(results.get(i).getScore()).isGreaterThanOrEqualTo(results.get(i + 1).getScore());
        }
    }

    private void seedTestData() {
        transactionTemplate.executeWithoutResult(status -> {
            // brands
            entityManager.createNativeQuery("""
                    INSERT INTO brands (id, name, created_at, updated_at) VALUES
                    (1, '브랜드A', NOW(6), NOW(6)),
                    (2, '브랜드B', NOW(6), NOW(6))
                    """).executeUpdate();

            // products (VISIBLE)
            entityManager.createNativeQuery("""
                    INSERT INTO products (id, name, price, brand_id, visibility, created_at, updated_at) VALUES
                    (1, '상품A', 10000, 1, 'VISIBLE', NOW(6), NOW(6)),
                    (2, '상품B', 20000, 2, 'VISIBLE', NOW(6), NOW(6)),
                    (3, '상품C', 30000, 1, 'VISIBLE', NOW(6), NOW(6))
                    """).executeUpdate();

            // product_metrics_daily (7일 범위 내)
            LocalDate d1 = END_DATE;
            LocalDate d2 = END_DATE.minusDays(3);

            entityManager.createNativeQuery("""
                    INSERT INTO product_metrics_daily (product_id, metric_date, view_count, like_count, order_line_count, order_amount) VALUES
                    (1, :d1, 100, 50, 10, 500000),
                    (1, :d2, 200, 30, 5, 250000),
                    (2, :d1, 50, 100, 20, 1000000),
                    (3, :d1, 10, 5, 1, 30000)
                    """)
                    .setParameter("d1", d1)
                    .setParameter("d2", d2)
                    .executeUpdate();
        });
    }

    private void seedTestDataForMonthly() {
        transactionTemplate.executeWithoutResult(status -> {
            entityManager.createNativeQuery("""
                    INSERT INTO brands (id, name, created_at, updated_at) VALUES
                    (1, '브랜드A', NOW(6), NOW(6))
                    """).executeUpdate();

            entityManager.createNativeQuery("""
                    INSERT INTO products (id, name, price, brand_id, visibility, created_at, updated_at) VALUES
                    (1, '상품A', 10000, 1, 'VISIBLE', NOW(6), NOW(6))
                    """).executeUpdate();

            // 30일 범위 내 데이터 (15일 전 + 25일 전)
            LocalDate d1 = END_DATE.minusDays(15);
            LocalDate d2 = END_DATE.minusDays(25);

            entityManager.createNativeQuery("""
                    INSERT INTO product_metrics_daily (product_id, metric_date, view_count, like_count, order_line_count, order_amount) VALUES
                    (1, :d1, 100, 50, 10, 500000),
                    (1, :d2, 200, 30, 5, 250000)
                    """)
                    .setParameter("d1", d1)
                    .setParameter("d2", d2)
                    .executeUpdate();
        });
    }

    private void seedTestDataWithDeletedProduct() {
        transactionTemplate.executeWithoutResult(status -> {
            entityManager.createNativeQuery("""
                    INSERT INTO brands (id, name, created_at, updated_at) VALUES
                    (1, '브랜드A', NOW(6), NOW(6))
                    """).executeUpdate();

            entityManager.createNativeQuery("""
                    INSERT INTO products (id, name, price, brand_id, visibility, created_at, updated_at) VALUES
                    (1, '정상상품', 10000, 1, 'VISIBLE', NOW(6), NOW(6)),
                    (99, '삭제된상품', 20000, 1, 'VISIBLE', NOW(6), NOW(6))
                    """).executeUpdate();

            // 상품 99 삭제 처리
            entityManager.createNativeQuery("""
                    UPDATE products SET deleted_at = NOW(6) WHERE id = 99
                    """).executeUpdate();

            entityManager.createNativeQuery("""
                    INSERT INTO product_metrics_daily (product_id, metric_date, view_count, like_count, order_line_count, order_amount) VALUES
                    (1, :d1, 100, 50, 10, 500000),
                    (99, :d1, 999, 999, 999, 9999999)
                    """)
                    .setParameter("d1", END_DATE)
                    .executeUpdate();
        });
    }
}
