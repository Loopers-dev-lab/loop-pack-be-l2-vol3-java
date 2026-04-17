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
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
@SpringBatchTest
@TestPropertySource(properties = {
        "spring.batch.job.name=" + RankingAggregationJobConfig.JOB_NAME,
        "spring.batch.job.enabled=false",
        "ranking.weight.view=0.1",
        "ranking.weight.like=0.2",
        "ranking.weight.order=0.7"
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

        List<ProductRankSnapshot> results = productRankSnapshotJpaRepository.findAll()
                .stream()
                .sorted(Comparator.comparing(ProductRankSnapshot::getRankPosition))
                .toList();

        assertThat(results).hasSize(3);
        assertThat(results).allMatch(r -> r.getRankingType() == RankingType.WEEKLY);
        assertThat(results).allMatch(r -> r.getRankDate().equals(END_DATE));

        // rank 1: 상품B (product 2) — score = 50*0.1 + 100*0.2 + 1000000*0.7 = 700025.0
        ProductRankSnapshot rank1 = results.get(0);
        assertAll(
                () -> assertThat(rank1.getProductId()).isEqualTo(2L),
                () -> assertThat(rank1.getRankPosition()).isEqualTo(1),
                () -> assertThat(rank1.getTotalViewCount()).isEqualTo(50L),
                () -> assertThat(rank1.getTotalLikeCount()).isEqualTo(100L),
                () -> assertThat(rank1.getTotalOrderLineCount()).isEqualTo(20L),
                () -> assertThat(rank1.getTotalOrderAmount()).isEqualTo(1000000L),
                () -> assertThat(rank1.getScore()).isCloseTo(700025.0, within(0.01))
        );

        // rank 2: 상품A (product 1) — score = 300*0.1 + 80*0.2 + 750000*0.7 = 525046.0
        ProductRankSnapshot rank2 = results.get(1);
        assertAll(
                () -> assertThat(rank2.getProductId()).isEqualTo(1L),
                () -> assertThat(rank2.getRankPosition()).isEqualTo(2),
                () -> assertThat(rank2.getTotalViewCount()).isEqualTo(300L),
                () -> assertThat(rank2.getTotalLikeCount()).isEqualTo(80L),
                () -> assertThat(rank2.getTotalOrderLineCount()).isEqualTo(15L),
                () -> assertThat(rank2.getTotalOrderAmount()).isEqualTo(750000L),
                () -> assertThat(rank2.getScore()).isCloseTo(525046.0, within(0.01))
        );

        // rank 3: 상품C (product 3) — score = 10*0.1 + 5*0.2 + 30000*0.7 = 21002.0
        ProductRankSnapshot rank3 = results.get(2);
        assertAll(
                () -> assertThat(rank3.getProductId()).isEqualTo(3L),
                () -> assertThat(rank3.getRankPosition()).isEqualTo(3),
                () -> assertThat(rank3.getScore()).isCloseTo(21002.0, within(0.01))
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
        assertThat(results).hasSize(1);

        // 상품A: d1(3/30) view=100,like=50,orderLine=10,orderAmount=500000
        //        d2(3/20) view=200,like=30,orderLine=5,orderAmount=250000
        // 합산: view=300, like=80, orderLine=15, orderAmount=750000
        // score = 300*0.1 + 80*0.2 + 750000*0.7 = 525046.0
        ProductRankSnapshot snapshot = results.get(0);
        assertAll(
                () -> assertThat(snapshot.getRankingType()).isEqualTo(RankingType.MONTHLY),
                () -> assertThat(snapshot.getRankDate()).isEqualTo(END_DATE),
                () -> assertThat(snapshot.getProductId()).isEqualTo(1L),
                () -> assertThat(snapshot.getTotalViewCount()).isEqualTo(300L),
                () -> assertThat(snapshot.getTotalLikeCount()).isEqualTo(80L),
                () -> assertThat(snapshot.getTotalOrderLineCount()).isEqualTo(15L),
                () -> assertThat(snapshot.getTotalOrderAmount()).isEqualTo(750000L),
                () -> assertThat(snapshot.getScore()).isCloseTo(525046.0, within(0.01))
        );
    }

    @DisplayName("WEEKLY 타입: 7일 범위 밖(8일 전) 데이터는 집계에서 제외된다")
    @Test
    void weeklyAggregation_excludesDataOutsideWindow() throws Exception {
        // arrange
        // 상품B에 범위 내(4/14) 데이터와 범위 밖(4/7, 8일 전) 데이터를 모두 시딩한다.
        // WEEKLY 집계 범위는 endDate-6 ~ endDate (4/8 ~ 4/14) 이므로,
        // 4/7 데이터는 집계에서 제외되어야 한다.
        seedTestDataForWindowBoundary();

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

        assertThat(results).hasSize(2);

        // 상품A (product 1): 범위 내 4/14(view=100,like=50,orderLine=10,orderAmount=500000)
        //                   + 경계일 4/8(view=50,like=20,orderLine=5,orderAmount=200000)
        // 합산: view=150, like=70, orderLine=15, orderAmount=700000
        // score = 150*0.1 + 70*0.2 + 700000*0.7 = 490029.0
        ProductRankSnapshot productA = results.stream()
                                              .filter(r -> r.getProductId().equals(1L))
                                              .findFirst()
                                              .orElseThrow();
        assertAll(
                () -> assertThat(productA.getTotalViewCount()).isEqualTo(150L),
                () -> assertThat(productA.getTotalLikeCount()).isEqualTo(70L),
                () -> assertThat(productA.getTotalOrderLineCount()).isEqualTo(15L),
                () -> assertThat(productA.getTotalOrderAmount()).isEqualTo(700000L),
                () -> assertThat(productA.getScore()).isCloseTo(490029.0, within(0.01))
        );

        // 상품B (product 2): 범위 내(4/14: view=50, like=10, orderLine=3, orderAmount=100000)만 집계
        // 범위 밖(4/7: view=9999, like=9999, ...) 데이터는 제외
        // score = 50*0.1 + 10*0.2 + 100000*0.7 = 70007.0
        ProductRankSnapshot productB = results.stream()
                                              .filter(r -> r.getProductId().equals(2L))
                                              .findFirst()
                                              .orElseThrow();
        assertAll(
                () -> assertThat(productB.getTotalViewCount()).isEqualTo(50L),
                () -> assertThat(productB.getTotalLikeCount()).isEqualTo(10L),
                () -> assertThat(productB.getTotalOrderLineCount()).isEqualTo(3L),
                () -> assertThat(productB.getTotalOrderAmount()).isEqualTo(100000L),
                () -> assertThat(productB.getScore()).isCloseTo(70007.0, within(0.01))
        );
    }

    @DisplayName("MONTHLY 타입: 30일 범위 밖(31일 전) 데이터는 집계에서 제외된다")
    @Test
    void monthlyAggregation_excludesDataOutsideWindow() throws Exception {
        // arrange
        // 상품A에 범위 내(3/30) 데이터와 범위 밖(3/15, 31일 전) 데이터를 모두 시딩한다.
        // MONTHLY 집계 범위는 endDate-29 ~ endDate (3/16 ~ 4/14) 이므로,
        // 3/15 데이터는 집계에서 제외되어야 한다.
        seedTestDataForMonthlyWindowBoundary();

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
        assertThat(results).hasSize(1);

        // 상품A는 범위 내(3/30: view=100, like=50, order_amount=500000)와
        // 범위 밖(3/15: view=9999, like=9999, order_amount=99999999) 데이터가 모두 있지만,
        // 집계 결과는 범위 내 데이터만으로 계산되어야 한다.
        // 기대 score = 100 * 0.1 + 50 * 0.2 + 500000 * 0.7 = 350020.0
        ProductRankSnapshot snapshot = results.get(0);
        assertAll(
                () -> assertThat(snapshot.getRankingType()).isEqualTo(RankingType.MONTHLY),
                () -> assertThat(snapshot.getRankDate()).isEqualTo(END_DATE),
                () -> assertThat(snapshot.getProductId()).isEqualTo(1L),
                () -> assertThat(snapshot.getRankPosition()).isEqualTo(1),
                () -> assertThat(snapshot.getTotalViewCount()).isEqualTo(100L),
                () -> assertThat(snapshot.getTotalLikeCount()).isEqualTo(50L),
                () -> assertThat(snapshot.getTotalOrderLineCount()).isEqualTo(10L),
                () -> assertThat(snapshot.getTotalOrderAmount()).isEqualTo(500000L),
                () -> assertThat(snapshot.getScore()).isCloseTo(350020.0, within(0.01))
        );
    }

    @DisplayName("재집계: 같은 rankingType/endDate로 재실행 시 기존 스냅샷을 대체하고 중복이 발생하지 않는다")
    @Test
    void reAggregation_replacesExistingSnapshots_noDuplicates() throws Exception {
        // arrange
        seedTestData();

        // act - 1차 집계
        var firstJobExecution = jobLauncherTestUtils.launchJob(
                new JobParametersBuilder()
                        .addString("rankingType", "WEEKLY")
                        .addLocalDate("endDate", END_DATE)
                        .toJobParameters()
        );
        assertThat(firstJobExecution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode());

        List<ProductRankSnapshot> firstRunResults = productRankSnapshotJpaRepository.findAll()
                .stream()
                .sorted(Comparator.comparing(ProductRankSnapshot::getRankPosition))
                .toList();

        // 재집계를 위해 배치 메타데이터 초기화 (같은 파라미터로 재실행 허용)
        cleanUpBatchMetadata();

        // act - 2차 집계 (동일 rankingType/endDate)
        var jobExecution = jobLauncherTestUtils.launchJob(
                new JobParametersBuilder()
                        .addString("rankingType", "WEEKLY")
                        .addLocalDate("endDate", END_DATE)
                        .toJobParameters()
        );

        // assert
        List<ProductRankSnapshot> secondRunResults = productRankSnapshotJpaRepository.findAll()
                .stream()
                .sorted(Comparator.comparing(ProductRankSnapshot::getRankPosition))
                .toList();

        assertAll(
                () -> assertThat(jobExecution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode()),
                () -> assertThat(secondRunResults).hasSize(firstRunResults.size()),
                () -> assertThat(secondRunResults)
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
        assertAll(
                () -> assertThat(results).hasSize(1),
                () -> assertThat(results).anyMatch(r -> r.getProductId().equals(1L)),
                () -> assertThat(results).noneMatch(r -> r.getProductId().equals(99L))
        );
    }

    @DisplayName("비노출(HIDDEN) 상품은 랭킹에서 제외된다")
    @Test
    void excludesHiddenProducts() throws Exception {
        // arrange
        seedTestDataWithHiddenProduct();

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
                () -> assertThat(results).hasSize(1),
                () -> assertThat(results).anyMatch(r -> r.getProductId().equals(1L)),
                () -> assertThat(results).noneMatch(r -> r.getProductId().equals(98L))
        );
    }

    @DisplayName("삭제된 브랜드의 상품은 랭킹에서 제외된다")
    @Test
    void excludesProductsOfDeletedBrand() throws Exception {
        // arrange
        seedTestDataWithDeletedBrand();

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
                () -> assertThat(results).hasSize(1),
                () -> assertThat(results).anyMatch(r -> r.getProductId().equals(1L)),
                () -> assertThat(results).noneMatch(r -> r.getProductId().equals(97L))
        );
    }

    @DisplayName("동점일 때 product_id 오름차순으로 순위가 매겨진다")
    @Test
    void tiedScore_orderedByProductIdAsc() throws Exception {
        // arrange
        seedTestDataForTiedScore();

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

        assertThat(results).hasSize(3);

        // 상품A(id=1)와 상품B(id=2)는 동일 메트릭 → 동점
        // SQL: ORDER BY score DESC, pm.product_id ASC → product_id가 작은 쪽이 상위
        assertAll(
                () -> assertThat(results.get(0).getProductId()).isEqualTo(1L),
                () -> assertThat(results.get(1).getProductId()).isEqualTo(2L),
                () -> assertThat(results.get(0).getScore()).isEqualTo(results.get(1).getScore()),
                () -> assertThat(results.get(0).getRankPosition()).isLessThan(results.get(1).getRankPosition()),
                () -> assertThat(results.get(2).getProductId()).isEqualTo(3L),
                () -> assertThat(results.get(2).getScore()).isLessThan(results.get(0).getScore())
        );
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

    private void seedTestDataForWindowBoundary() {
        transactionTemplate.executeWithoutResult(status -> {
            entityManager.createNativeQuery("""
                    INSERT INTO brands (id, name, created_at, updated_at) VALUES
                    (1, '브랜드A', NOW(6), NOW(6))
                    """).executeUpdate();

            entityManager.createNativeQuery("""
                    INSERT INTO products (id, name, price, brand_id, visibility, created_at, updated_at) VALUES
                    (1, '상품A', 10000, 1, 'VISIBLE', NOW(6), NOW(6)),
                    (2, '상품B', 20000, 1, 'VISIBLE', NOW(6), NOW(6))
                    """).executeUpdate();

            // WEEKLY endDate=4/14 → 범위: 4/8 ~ 4/14
            LocalDate insideWindow = END_DATE;            // 4/14 (범위 내)
            LocalDate boundaryInside = END_DATE.minusDays(6); // 4/8 (범위 내 경계)
            LocalDate outsideWindow = END_DATE.minusDays(7);  // 4/7 (범위 밖)

            entityManager.createNativeQuery("""
                    INSERT INTO product_metrics_daily (product_id, metric_date, view_count, like_count, order_line_count, order_amount) VALUES
                    (1, :inside, 100, 50, 10, 500000),
                    (1, :boundary, 50, 20, 5, 200000),
                    (2, :inside, 50, 10, 3, 100000),
                    (2, :outside, 9999, 9999, 9999, 99999999)
                    """)
                    .setParameter("inside", insideWindow)
                    .setParameter("boundary", boundaryInside)
                    .setParameter("outside", outsideWindow)
                    .executeUpdate();
        });
    }

    private void seedTestDataForMonthlyWindowBoundary() {
        transactionTemplate.executeWithoutResult(status -> {
            entityManager.createNativeQuery("""
                    INSERT INTO brands (id, name, created_at, updated_at) VALUES
                    (1, '브랜드A', NOW(6), NOW(6))
                    """).executeUpdate();

            entityManager.createNativeQuery("""
                    INSERT INTO products (id, name, price, brand_id, visibility, created_at, updated_at) VALUES
                    (1, '상품A', 10000, 1, 'VISIBLE', NOW(6), NOW(6))
                    """).executeUpdate();

            // MONTHLY endDate=4/14 → 범위: 3/16 ~ 4/14
            LocalDate insideWindow = END_DATE.minusDays(15);  // 3/30 (범위 내)
            LocalDate outsideWindow = END_DATE.minusDays(30); // 3/15 (범위 밖)

            entityManager.createNativeQuery("""
                    INSERT INTO product_metrics_daily (product_id, metric_date, view_count, like_count, order_line_count, order_amount) VALUES
                    (1, :inside, 100, 50, 10, 500000),
                    (1, :outside, 9999, 9999, 9999, 99999999)
                    """)
                    .setParameter("inside", insideWindow)
                    .setParameter("outside", outsideWindow)
                    .executeUpdate();
        });
    }

    private void seedTestDataForTiedScore() {
        transactionTemplate.executeWithoutResult(status -> {
            entityManager.createNativeQuery("""
                    INSERT INTO brands (id, name, created_at, updated_at) VALUES
                    (1, '브랜드A', NOW(6), NOW(6))
                    """).executeUpdate();

            // 상품A(id=1)와 상품B(id=2)는 동일 메트릭, 상품C(id=3)는 낮은 메트릭
            entityManager.createNativeQuery("""
                    INSERT INTO products (id, name, price, brand_id, visibility, created_at, updated_at) VALUES
                    (1, '상품A', 10000, 1, 'VISIBLE', NOW(6), NOW(6)),
                    (2, '상품B', 10000, 1, 'VISIBLE', NOW(6), NOW(6)),
                    (3, '상품C', 10000, 1, 'VISIBLE', NOW(6), NOW(6))
                    """).executeUpdate();

            entityManager.createNativeQuery("""
                    INSERT INTO product_metrics_daily (product_id, metric_date, view_count, like_count, order_line_count, order_amount) VALUES
                    (1, :d1, 100, 50, 10, 500000),
                    (2, :d1, 100, 50, 10, 500000),
                    (3, :d1, 10, 5, 1, 30000)
                    """)
                    .setParameter("d1", END_DATE)
                    .executeUpdate();
        });
    }

    private void seedTestDataWithHiddenProduct() {
        transactionTemplate.executeWithoutResult(status -> {
            entityManager.createNativeQuery("""
                    INSERT INTO brands (id, name, created_at, updated_at) VALUES
                    (1, '브랜드A', NOW(6), NOW(6))
                    """).executeUpdate();

            entityManager.createNativeQuery("""
                    INSERT INTO products (id, name, price, brand_id, visibility, created_at, updated_at) VALUES
                    (1, '정상상품', 10000, 1, 'VISIBLE', NOW(6), NOW(6)),
                    (98, '비노출상품', 20000, 1, 'HIDDEN', NOW(6), NOW(6))
                    """).executeUpdate();

            entityManager.createNativeQuery("""
                    INSERT INTO product_metrics_daily (product_id, metric_date, view_count, like_count, order_line_count, order_amount) VALUES
                    (1, :d1, 100, 50, 10, 500000),
                    (98, :d1, 999, 999, 999, 9999999)
                    """)
                    .setParameter("d1", END_DATE)
                    .executeUpdate();
        });
    }

    private void seedTestDataWithDeletedBrand() {
        transactionTemplate.executeWithoutResult(status -> {
            entityManager.createNativeQuery("""
                    INSERT INTO brands (id, name, created_at, updated_at) VALUES
                    (1, '정상브랜드', NOW(6), NOW(6)),
                    (2, '삭제된브랜드', NOW(6), NOW(6))
                    """).executeUpdate();

            // 브랜드 2 삭제 처리
            entityManager.createNativeQuery("""
                    UPDATE brands SET deleted_at = NOW(6) WHERE id = 2
                    """).executeUpdate();

            entityManager.createNativeQuery("""
                    INSERT INTO products (id, name, price, brand_id, visibility, created_at, updated_at) VALUES
                    (1, '정상상품', 10000, 1, 'VISIBLE', NOW(6), NOW(6)),
                    (97, '삭제브랜드상품', 20000, 2, 'VISIBLE', NOW(6), NOW(6))
                    """).executeUpdate();

            entityManager.createNativeQuery("""
                    INSERT INTO product_metrics_daily (product_id, metric_date, view_count, like_count, order_line_count, order_amount) VALUES
                    (1, :d1, 100, 50, 10, 500000),
                    (97, :d1, 999, 999, 999, 9999999)
                    """)
                    .setParameter("d1", END_DATE)
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
