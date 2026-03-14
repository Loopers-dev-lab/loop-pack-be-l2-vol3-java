package com.loopers.benchmark;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 상품 조회 인덱스 벤치마크 테스트.
 *
 * 실행 방법:
 *   ./gradlew :apps:commerce-api:test --tests "com.loopers.benchmark.ProductIndexBenchmarkTest"
 *
 * DATA_SIZE 를 변경하여 10만/20만/50만/100만건 비교 가능.
 */
@SpringBootTest
@Tag("benchmark")
class ProductIndexBenchmarkTest {

    @Autowired
    private DataSource dataSource;

    // ==================== 설정 ====================
    private static final int DATA_SIZE = 100_000;
    private static final int BRAND_COUNT = 100;
    private static final int BATCH_SIZE = 5_000;
    private static final int QUERY_RUNS = 3;

    private final StringBuilder report = new StringBuilder();
    private final Map<String, List<String>> strategyTimings = new LinkedHashMap<>();
    private final Map<Long, Integer> brandProductCounts = new TreeMap<>();

    private long popularBrandId;
    private long mediumBrandId;

    // ==================== 메인 ====================

    @Test
    void benchmark() throws Exception {
        seedData();
        analyzeTable(); // 통계 정보 갱신 (모든 전략에서 공정한 비교를 위해)
        initReport();

        List<IndexStrategy> strategies = defineStrategies();
        for (IndexStrategy strategy : strategies) {
            runStrategy(strategy);
        }

        appendSummaryTable(strategies);
        writeReport();
    }

    private void analyzeTable() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            conn.createStatement().execute("ANALYZE TABLE products");
        }
    }

    // ==================== 데이터 시딩 ====================

    private void seedData() throws SQLException {
        Random random = new Random(42);
        double[] brandCdf = buildZipfCdf(BRAND_COUNT);
        int[] basePrices = {5_000, 9_900, 15_000, 19_900, 29_900, 39_900, 49_900, 79_900, 99_000, 149_000, 199_000, 299_000, 499_000};

        long start = System.currentTimeMillis();

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            String sql = "INSERT INTO products (brand_id, name, price, like_count, created_at, updated_at, deleted_at) "
                       + "VALUES (?, ?, ?, ?, ?, ?, NULL)";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (int i = 0; i < DATA_SIZE; i++) {
                    long brandId = sampleFromCdf(random, brandCdf) + 1;
                    int base = basePrices[random.nextInt(basePrices.length)];
                    int price = (int) (base * (0.8 + random.nextDouble() * 0.4));
                    int likeCount = Math.max(0, Math.min((int) Math.exp(random.nextGaussian() * 2.0 + 2.5), 100_000));

                    long daysAgo = random.nextInt(365);
                    Timestamp createdAt = Timestamp.valueOf(
                        LocalDateTime.now().minusDays(daysAgo).minusHours(random.nextInt(24))
                    );

                    ps.setLong(1, brandId);
                    ps.setString(2, "Product-" + (i + 1));
                    ps.setInt(3, price);
                    ps.setInt(4, likeCount);
                    ps.setTimestamp(5, createdAt);
                    ps.setTimestamp(6, createdAt);
                    ps.addBatch();

                    brandProductCounts.merge(brandId, 1, Integer::sum);

                    if ((i + 1) % BATCH_SIZE == 0) {
                        ps.executeBatch();
                    }
                }
                ps.executeBatch();
            }

            conn.commit();
        }

        // 인기 브랜드(상품 최다), 중간 브랜드 선정
        List<Map.Entry<Long, Integer>> sorted = brandProductCounts.entrySet().stream()
            .sorted(Map.Entry.<Long, Integer>comparingByValue().reversed())
            .toList();
        popularBrandId = sorted.get(0).getKey();
        mediumBrandId = sorted.get(sorted.size() / 2).getKey();

        long elapsed = System.currentTimeMillis() - start;
        System.out.printf("✅ 데이터 시딩 완료: %,d건 (%dms)%n", DATA_SIZE, elapsed);
    }

    // ==================== 리포트 헤더 ====================

    private void initReport() throws SQLException {
        report.append("# 📊 상품 조회 인덱스 벤치마크\n\n");
        report.append("> 실행일시: ")
              .append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
              .append("  \n");
        report.append("> MySQL 8.0 (TestContainers)\n\n");

        report.append("## 📋 테스트 환경\n\n");
        report.append("| 항목 | 값 |\n|---|---|\n");
        report.append(String.format("| 데이터 수 | %,d건 |\n", DATA_SIZE));
        report.append(String.format("| 브랜드 수 | %d개 (Zipf 분포) |\n", BRAND_COUNT));
        report.append("| 좋아요 분포 | Log-normal (중앙값 ~12, 롱테일) |\n");
        report.append(String.format("| 측정 횟수 | %d회 평균 |\n", QUERY_RUNS));
        report.append(String.format("| 인기 브랜드 ID | %d (%,d건) |\n", popularBrandId, brandProductCounts.get(popularBrandId)));
        report.append(String.format("| 중간 브랜드 ID | %d (%,d건) |\n", mediumBrandId, brandProductCounts.get(mediumBrandId)));
        report.append("\n");

        // 브랜드 분포 상위 10개
        report.append("### 브랜드별 상품 수 (상위 10개)\n\n");
        report.append("| 순위 | 브랜드 ID | 상품 수 | 비율 |\n|---|---|---|---|\n");
        List<Map.Entry<Long, Integer>> sorted = brandProductCounts.entrySet().stream()
            .sorted(Map.Entry.<Long, Integer>comparingByValue().reversed())
            .limit(10)
            .toList();
        for (int i = 0; i < sorted.size(); i++) {
            Map.Entry<Long, Integer> e = sorted.get(i);
            report.append(String.format("| %d | %d | %,d | %.1f%% |\n",
                i + 1, e.getKey(), e.getValue(), e.getValue() * 100.0 / DATA_SIZE));
        }

        // 좋아요 분포
        report.append("\n### 좋아요 수 분포\n\n");
        appendLikeDistribution();
        report.append("\n---\n\n");
    }

    private void appendLikeDistribution() throws SQLException {
        String sql = """
            SELECT
                CASE
                    WHEN like_count BETWEEN 0 AND 10 THEN '0~10'
                    WHEN like_count BETWEEN 11 AND 50 THEN '11~50'
                    WHEN like_count BETWEEN 51 AND 200 THEN '51~200'
                    WHEN like_count BETWEEN 201 AND 1000 THEN '201~1,000'
                    WHEN like_count BETWEEN 1001 AND 5000 THEN '1,001~5,000'
                    ELSE '5,001+'
                END AS range_label,
                COUNT(*) AS cnt,
                MIN(like_count) AS range_min
            FROM products
            GROUP BY range_label
            ORDER BY range_min
            """;

        report.append("| 구간 | 상품 수 | 비율 |\n|---|---|---|\n");

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                int cnt = rs.getInt("cnt");
                report.append(String.format("| %s | %,d | %.1f%% |\n",
                    rs.getString("range_label"), cnt, cnt * 100.0 / DATA_SIZE));
            }
        }
    }

    // ==================== 인덱스 전략 정의 ====================

    record IndexStrategy(String name, String description, List<String> createSqls, List<String> dropSqls) {}

    private List<IndexStrategy> defineStrategies() {
        return List.of(
            new IndexStrategy(
                "인덱스 없음 (Baseline)",
                "PK(id)만 존재하는 상태",
                List.of(),
                List.of()
            ),
            new IndexStrategy(
                "단일 인덱스: (brand_id)",
                "가장 기본적인 필터 컬럼 인덱스",
                List.of("CREATE INDEX idx_brand_id ON products(brand_id)"),
                List.of("DROP INDEX idx_brand_id ON products")
            ),
            new IndexStrategy(
                "복합 인덱스: (brand_id, deleted_at, like_count DESC)",
                "브랜드 필터 + soft delete + 좋아요순 정렬까지 커버",
                List.of("CREATE INDEX idx_brand_deleted_like ON products(brand_id, deleted_at, like_count DESC)"),
                List.of("DROP INDEX idx_brand_deleted_like ON products")
            ),
            new IndexStrategy(
                "전체 커버링 인덱스 세트",
                "모든 조회 패턴에 최적화된 인덱스 조합 (쓰기 비용 증가 트레이드오프)",
                List.of(
                    "CREATE INDEX idx_prod_brand_like ON products(brand_id, deleted_at, like_count DESC)",
                    "CREATE INDEX idx_prod_brand_price ON products(brand_id, deleted_at, price ASC)",
                    "CREATE INDEX idx_prod_brand_created ON products(brand_id, deleted_at, created_at DESC)",
                    "CREATE INDEX idx_prod_deleted_like ON products(deleted_at, like_count DESC)",
                    "CREATE INDEX idx_prod_deleted_price ON products(deleted_at, price ASC)"
                ),
                List.of(
                    "DROP INDEX idx_prod_brand_like ON products",
                    "DROP INDEX idx_prod_brand_price ON products",
                    "DROP INDEX idx_prod_brand_created ON products",
                    "DROP INDEX idx_prod_deleted_like ON products",
                    "DROP INDEX idx_prod_deleted_price ON products"
                )
            )
        );
    }

    // ==================== 벤치마크 쿼리 정의 ====================

    record BenchmarkQuery(String label, String sql) {}

    private List<BenchmarkQuery> getQueries() {
        return List.of(
            new BenchmarkQuery("브랜드(인기) + 좋아요순",
                "SELECT * FROM products WHERE brand_id = %d AND deleted_at IS NULL ORDER BY like_count DESC LIMIT 20".formatted(popularBrandId)),
            new BenchmarkQuery("브랜드(인기) + 가격순",
                "SELECT * FROM products WHERE brand_id = %d AND deleted_at IS NULL ORDER BY price ASC LIMIT 20".formatted(popularBrandId)),
            new BenchmarkQuery("브랜드(인기) + 최신순",
                "SELECT * FROM products WHERE brand_id = %d AND deleted_at IS NULL ORDER BY created_at DESC LIMIT 20".formatted(popularBrandId)),
            new BenchmarkQuery("브랜드(중간) + 좋아요순",
                "SELECT * FROM products WHERE brand_id = %d AND deleted_at IS NULL ORDER BY like_count DESC LIMIT 20".formatted(mediumBrandId)),
            new BenchmarkQuery("전체 + 좋아요순",
                "SELECT * FROM products WHERE deleted_at IS NULL ORDER BY like_count DESC LIMIT 20"),
            new BenchmarkQuery("전체 + 가격순",
                "SELECT * FROM products WHERE deleted_at IS NULL ORDER BY price ASC LIMIT 20"),
            new BenchmarkQuery("COUNT(브랜드 인기)",
                "SELECT COUNT(*) FROM products WHERE brand_id = %d AND deleted_at IS NULL".formatted(popularBrandId)),
            new BenchmarkQuery("COUNT(전체)",
                "SELECT COUNT(*) FROM products WHERE deleted_at IS NULL"),
            // 딥 페이지네이션: OFFSET이 커질수록 성능 저하 확인
            new BenchmarkQuery("딥페이징: 좋아요순 OFFSET 10000",
                "SELECT * FROM products WHERE deleted_at IS NULL ORDER BY like_count DESC LIMIT 20 OFFSET 10000"),
            new BenchmarkQuery("딥페이징: 브랜드(인기)+좋아요순 OFFSET 5000",
                "SELECT * FROM products WHERE brand_id = %d AND deleted_at IS NULL ORDER BY like_count DESC LIMIT 20 OFFSET 5000".formatted(popularBrandId))
        );
    }

    // ==================== 전략 실행 ====================

    private void runStrategy(IndexStrategy strategy) throws SQLException {
        // 인덱스 생성
        try (Connection conn = dataSource.getConnection()) {
            for (String sql : strategy.createSqls()) {
                conn.createStatement().execute(sql);
            }
            if (!strategy.createSqls().isEmpty()) {
                conn.createStatement().execute("ANALYZE TABLE products");
            }
        }

        report.append("## 🔍 ").append(strategy.name()).append("\n\n");
        report.append("> ").append(strategy.description()).append("\n\n");

        appendCurrentIndexes();

        // 인덱스 생성에 사용된 DDL 표시
        if (!strategy.createSqls().isEmpty()) {
            report.append("```sql\n");
            for (String sql : strategy.createSqls()) {
                report.append(sql).append(";\n");
            }
            report.append("```\n\n");
        }

        // 쿼리별 벤치마크 실행
        report.append("### 벤치마크 결과\n\n");
        report.append("| # | 쿼리 | type | possible_keys | key | rows | filtered | Extra | 수행시간 |\n");
        report.append("|---|---|---|---|---|---|---|---|---|\n");

        List<String> timings = new ArrayList<>();
        List<BenchmarkQuery> queries = getQueries();
        for (int i = 0; i < queries.size(); i++) {
            String timing = runSingleBenchmark(i + 1, queries.get(i));
            timings.add(timing);
        }
        strategyTimings.put(strategy.name(), timings);

        // 실행된 쿼리 상세 (접힘)
        report.append("\n<details><summary>실행된 쿼리 상세</summary>\n\n");
        for (int i = 0; i < queries.size(); i++) {
            report.append("**").append(i + 1).append(". ").append(queries.get(i).label()).append("**\n");
            report.append("```sql\n").append(queries.get(i).sql()).append("\n```\n\n");
        }
        report.append("</details>\n\n");
        report.append("---\n\n");

        // 인덱스 제거
        try (Connection conn = dataSource.getConnection()) {
            for (String sql : strategy.dropSqls()) {
                try { conn.createStatement().execute(sql); } catch (Exception ignored) {}
            }
        }
    }

    private String runSingleBenchmark(int idx, BenchmarkQuery query) throws SQLException {
        // EXPLAIN 결과 수집
        Map<String, String> explain = getExplain(query.sql());

        // 수행시간 측정: warm-up 1회 + 측정 QUERY_RUNS회 평균
        long totalNanos = 0;
        try (Connection conn = dataSource.getConnection()) {
            // warm-up (버퍼 풀에 데이터 로드)
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query.sql())) {
                while (rs.next()) { /* consume */ }
            }
            // 측정
            for (int i = 0; i < QUERY_RUNS; i++) {
                long start = System.nanoTime();
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(query.sql())) {
                    while (rs.next()) { /* consume */ }
                }
                totalNanos += System.nanoTime() - start;
            }
        }
        long avgMs = totalNanos / QUERY_RUNS / 1_000_000;
        String timing = avgMs + "ms";

        report.append(String.format("| %d | %s | %s | %s | %s | %s | %s | %s | **%s** |\n",
            idx,
            query.label(),
            explain.getOrDefault("type", "-"),
            truncate(explain.getOrDefault("possible_keys", "NULL"), 30),
            explain.getOrDefault("key", "NULL"),
            explain.getOrDefault("rows", "-"),
            explain.getOrDefault("filtered", "-"),
            explain.getOrDefault("Extra", "-"),
            timing
        ));

        return timing;
    }

    // ==================== EXPLAIN 실행 ====================

    private Map<String, String> getExplain(String sql) throws SQLException {
        Map<String, String> result = new LinkedHashMap<>();
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("EXPLAIN " + sql)) {
            if (rs.next()) {
                ResultSetMetaData meta = rs.getMetaData();
                for (int i = 1; i <= meta.getColumnCount(); i++) {
                    String val = rs.getString(i);
                    result.put(meta.getColumnLabel(i), val != null ? val : "NULL");
                }
            }
        }
        return result;
    }

    // ==================== 현재 인덱스 표시 ====================

    private void appendCurrentIndexes() throws SQLException {
        report.append("**적용된 인덱스:**\n\n");
        report.append("| Key_name | Column_name | Seq | Non_unique |\n|---|---|---|---|\n");

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SHOW INDEX FROM products")) {
            while (rs.next()) {
                report.append(String.format("| %s | %s | %d | %d |\n",
                    rs.getString("Key_name"),
                    rs.getString("Column_name"),
                    rs.getInt("Seq_in_index"),
                    rs.getInt("Non_unique")
                ));
            }
        }
        report.append("\n");
    }

    // ==================== 요약 비교표 ====================

    private void appendSummaryTable(List<IndexStrategy> strategies) {
        report.append("## 📈 전략별 성능 비교 요약\n\n");

        List<BenchmarkQuery> queries = getQueries();

        // 헤더
        report.append("| 쿼리 |");
        for (IndexStrategy s : strategies) {
            report.append(" ").append(s.name()).append(" |");
        }
        report.append("\n|---|");
        for (int i = 0; i < strategies.size(); i++) report.append("---|");
        report.append("\n");

        // 행
        for (int q = 0; q < queries.size(); q++) {
            report.append("| ").append(queries.get(q).label()).append(" |");
            for (IndexStrategy s : strategies) {
                List<String> timings = strategyTimings.get(s.name());
                String val = (timings != null && q < timings.size()) ? timings.get(q) : "-";
                report.append(" ").append(val).append(" |");
            }
            report.append("\n");
        }
    }

    // ==================== 리포트 출력 ====================

    private void writeReport() throws IOException {
        String suffix = switch (DATA_SIZE) {
            case 100_000 -> "100k";
            case 200_000 -> "200k";
            case 500_000 -> "500k";
            case 1_000_000 -> "1m";
            default -> String.valueOf(DATA_SIZE);
        };

        // .docs/ 디렉토리 탐색 (프로젝트 루트 기준)
        Path docsDir = findDocsDir();
        Path outputPath = docsDir.resolve("index-benchmark-" + suffix + ".md");
        Files.writeString(outputPath, report.toString(), StandardCharsets.UTF_8);

        System.out.println("\n📄 리포트 저장: " + outputPath.toAbsolutePath());
        System.out.println("\n" + report);
    }

    private Path findDocsDir() throws IOException {
        Path dir = Path.of(System.getProperty("user.dir"));
        for (int i = 0; i < 5; i++) {
            Path docs = dir.resolve(".docs");
            if (Files.isDirectory(docs)) {
                return docs;
            }
            dir = dir.getParent();
            if (dir == null) break;
        }
        // fallback: 현재 디렉토리에 생성
        Path fallback = Path.of(System.getProperty("user.dir"), ".docs");
        Files.createDirectories(fallback);
        return fallback;
    }

    // ==================== 유틸리티 ====================

    private double[] buildZipfCdf(int n) {
        double[] cdf = new double[n];
        double sum = 0;
        for (int i = 0; i < n; i++) {
            sum += 1.0 / (i + 1);
        }
        double cumulative = 0;
        for (int i = 0; i < n; i++) {
            cumulative += (1.0 / (i + 1)) / sum;
            cdf[i] = cumulative;
        }
        return cdf;
    }

    private int sampleFromCdf(Random random, double[] cdf) {
        double r = random.nextDouble();
        for (int i = 0; i < cdf.length; i++) {
            if (r <= cdf[i]) return i;
        }
        return cdf.length - 1;
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "NULL";
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }
}
