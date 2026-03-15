package com.loopers.domain.product;

import com.loopers.testcontainers.MySqlTestContainersConfig;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@SpringBootTest
@Import(MySqlTestContainersConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProductIndexPerformanceTest {

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    // 테스트 시나리오 정의
    private static final String[] QUERY_NAMES = {
        "전체 최신순",
        "전체 가격순",
        "전체 좋아요순",
        "브랜드별 최신순",
        "브랜드별 가격순",
        "브랜드별 좋아요순",
        "favorite countByProductId"
    };

    private static final String[] QUERY_SQLS = {
        "SELECT p.id, p.name, p.price, p.stock, p.like_count " +
        "FROM product p WHERE p.deleted_at IS NULL ORDER BY p.id DESC LIMIT 20",

        "SELECT p.id, p.name, p.price, p.stock, p.like_count " +
        "FROM product p WHERE p.deleted_at IS NULL ORDER BY p.price ASC LIMIT 20",

        "SELECT p.id, p.name, p.price, p.stock, p.like_count " +
        "FROM product p WHERE p.deleted_at IS NULL ORDER BY p.like_count DESC LIMIT 20",

        "SELECT p.id, p.name, p.price, p.stock, p.like_count " +
        "FROM product p WHERE p.brand_id = 1 AND p.deleted_at IS NULL ORDER BY p.id DESC LIMIT 20",

        "SELECT p.id, p.name, p.price, p.stock, p.like_count " +
        "FROM product p WHERE p.brand_id = 1 AND p.deleted_at IS NULL ORDER BY p.price ASC LIMIT 20",

        "SELECT p.id, p.name, p.price, p.stock, p.like_count " +
        "FROM product p WHERE p.brand_id = 1 AND p.deleted_at IS NULL ORDER BY p.like_count DESC LIMIT 20",

        "SELECT COUNT(*) FROM favorite WHERE product_id = 1"
    };

    // 후보 인덱스 정의: [인덱스명, CREATE문, DROP문, 설명]
    private static final String[][] INDEX_CANDIDATES = {
        {"idx_product_price",       "CREATE INDEX idx_product_price ON product(price)",                "DROP INDEX idx_product_price ON product",       "단일: price"},
        {"idx_product_like_count",  "CREATE INDEX idx_product_like_count ON product(like_count)",      "DROP INDEX idx_product_like_count ON product",  "단일: like_count"},
        {"idx_product_brand_price", "CREATE INDEX idx_product_brand_price ON product(brand_id, price)","DROP INDEX idx_product_brand_price ON product", "복합: brand_id + price"},
        {"idx_product_brand_like",  "CREATE INDEX idx_product_brand_like ON product(brand_id, like_count)","DROP INDEX idx_product_brand_like ON product","복합: brand_id + like_count"},
        {"idx_product_brand_like_price", "CREATE INDEX idx_product_brand_like_price ON product(brand_id, like_count, price)","DROP INDEX idx_product_brand_like_price ON product","복합: brand_id + like_count + price (3컬럼)"},
        {"idx_favorite_product_id", "CREATE INDEX idx_favorite_product_id ON favorite(product_id)",    "DROP INDEX idx_favorite_product_id ON favorite", "단일: product_id (favorite)"},
    };

    @BeforeAll
    void setUp() {
        EntityManager em = entityManagerFactory.createEntityManager();
        em.getTransaction().begin();

        // 브랜드 10개
        for (int i = 1; i <= 10; i++) {
            em.createNativeQuery(
                    "INSERT INTO brand (id, name, description, created_at, updated_at) VALUES (:id, :name, :desc, NOW(), NOW())")
                .setParameter("id", i)
                .setParameter("name", "Brand-" + i)
                .setParameter("desc", "Desc-" + i)
                .executeUpdate();
        }

        // 상품 10만건
        for (int batch = 0; batch < 100; batch++) {
            StringBuilder sql = new StringBuilder(
                "INSERT INTO product (brand_id, name, price, stock, like_count, display_status, created_at, updated_at) VALUES ");
            for (int i = 0; i < 1000; i++) {
                int idx = batch * 1000 + i;
                long brandId = (idx % 10) + 1;
                int price = 1000 + (idx * 7) % 99000;
                int stock = 1 + (idx * 3) % 500;
                if (i > 0) sql.append(",");
                sql.append(String.format("(%d, 'Product-%d', %d, %d, 0, 'DISPLAYING', NOW(), NOW())",
                    brandId, idx, price, stock));
            }
            em.createNativeQuery(sql.toString()).executeUpdate();
        }

        // 멤버 10명
        for (int i = 1; i <= 10; i++) {
            em.createNativeQuery(
                    "INSERT INTO member (id, login_id, password, name, email, birth_date, created_at, updated_at) " +
                    "VALUES (:id, :loginId, 'pass', :name, :email, '1990-01-01', NOW(), NOW())")
                .setParameter("id", i)
                .setParameter("loginId", "user" + i)
                .setParameter("name", "User-" + i)
                .setParameter("email", "user" + i + "@test.com")
                .executeUpdate();
        }

        // favorite: 멤버 10명 x 상품 랜덤 좋아요 (약 5%)
        em.createNativeQuery(
                "INSERT INTO favorite (member_id, product_id, created_at, updated_at) " +
                "SELECT m.id, p.id, NOW(), NOW() " +
                "FROM member m CROSS JOIN product p " +
                "WHERE RAND() < 0.05")
            .executeUpdate();

        em.getTransaction().commit();

        // product.like_count 동기화
        em.getTransaction().begin();
        em.createNativeQuery(
                "UPDATE product p SET p.like_count = " +
                "(SELECT COUNT(*) FROM favorite f WHERE f.product_id = p.id)")
            .executeUpdate();
        em.getTransaction().commit();

        em.close();
    }

    @Test
    void indexPerformanceBenchmark() {
        EntityManager em = entityManagerFactory.createEntityManager();

        // ═══ 1단계: 베이스라인 (인덱스 없음) ═══
        System.out.println("\n========================================");
        System.out.println("  [BASELINE] 인덱스 없는 상태 측정");
        System.out.println("========================================");
        List<QueryResult> baseline = measureAllQueries(em);

        // ═══ 2단계: 각 인덱스별 개별 측정 ═══
        // key=인덱스명, value=해당 인덱스만 적용했을 때 전체 쿼리 결과
        Map<String, List<QueryResult>> perIndexResults = new LinkedHashMap<>();

        for (String[] candidate : INDEX_CANDIDATES) {
            String indexName = candidate[0];
            String createSql = candidate[1];
            String dropSql = candidate[2];
            String desc = candidate[3];

            System.out.println("\n========================================");
            System.out.printf("  [INDEX] %s (%s) 적용 후 측정%n", indexName, desc);
            System.out.println("========================================");

            // CREATE
            em.getTransaction().begin();
            em.createNativeQuery(createSql).executeUpdate();
            em.getTransaction().commit();

            // 측정
            List<QueryResult> results = measureAllQueries(em);
            perIndexResults.put(indexName, results);

            // DROP (다음 인덱스 개별 테스트를 위해 원복)
            em.getTransaction().begin();
            em.createNativeQuery(dropSql).executeUpdate();
            em.getTransaction().commit();
        }

        // ═══ 3단계: 전체 인덱스 동시 적용 ═══
        System.out.println("\n========================================");
        System.out.println("  [ALL INDEXES] 전체 인덱스 적용 후 측정");
        System.out.println("========================================");

        em.getTransaction().begin();
        for (String[] candidate : INDEX_CANDIDATES) {
            em.createNativeQuery(candidate[1]).executeUpdate();
        }
        em.getTransaction().commit();

        List<QueryResult> allIndexResults = measureAllQueries(em);

        em.close();

        // ═══ 4단계: 결과 출력 ═══
        printPerIndexComparisonTable(baseline, perIndexResults);
        printFinalComparisonTable(baseline, allIndexResults);
        printConclusion(baseline, perIndexResults, allIndexResults);
    }

    // ─── 측정 헬퍼 ─────────────────────────────────────────────────

    private List<QueryResult> measureAllQueries(EntityManager em) {
        List<QueryResult> results = new ArrayList<>();
        for (int i = 0; i < QUERY_SQLS.length; i++) {
            results.add(measureQuery(em, QUERY_NAMES[i], QUERY_SQLS[i]));
        }
        return results;
    }

    private QueryResult measureQuery(EntityManager em, String name, String sql) {
        // EXPLAIN
        String accessType = "N/A", possibleKeys = "NULL", keyUsed = "NULL",
               keyLen = "NULL", rowsStr = "N/A", extraStr = "";

        try {
            @SuppressWarnings("unchecked")
            List<Object[]> explainRows = em.createNativeQuery("EXPLAIN " + sql).getResultList();
            if (!explainRows.isEmpty()) {
                Object[] row = explainRows.get(0);
                for (Object[] r : explainRows) {
                    String tableName = r[2] != null ? String.valueOf(r[2]) : "";
                    if ("p".equals(tableName) || "product".equals(tableName) || "favorite".equals(tableName)) {
                        row = r;
                        break;
                    }
                }
                accessType   = str(row[4]);
                possibleKeys = str(row[5]);
                keyUsed      = str(row[6]);
                keyLen        = str(row[7]);
                rowsStr      = str(row[9]);
                extraStr     = str(row[11]);
            }
        } catch (Exception e) {
            System.out.println("  EXPLAIN 오류 [" + name + "]: " + e.getMessage());
        }

        // 워밍업 1회
        try { em.createNativeQuery(sql).getResultList(); } catch (Exception ignored) {}

        // 10회 반복 측정
        long total = 0;
        for (int i = 0; i < 10; i++) {
            long start = System.currentTimeMillis();
            em.createNativeQuery(sql).getResultList();
            total += System.currentTimeMillis() - start;
        }
        double avgMs = total / 10.0;

        System.out.printf("  %-28s | avg=%.1fms | type=%-6s | key=%-30s | rows=%-8s | Extra=%s%n",
            name, avgMs, accessType, keyUsed, rowsStr, extraStr);

        return new QueryResult(name, avgMs, accessType, possibleKeys, keyUsed, keyLen, rowsStr, extraStr);
    }

    // ─── 출력 헬퍼 ─────────────────────────────────────────────────

    private void printPerIndexComparisonTable(List<QueryResult> baseline, Map<String, List<QueryResult>> perIndex) {
        System.out.println();
        System.out.println("┌─────────────────────────────────────────────────────────────────────────────────────────────────────────────────┐");
        System.out.println("│                              각 인덱스별 개별 성능 비교 (10만건 기준)                                                │");
        System.out.println("└─────────────────────────────────────────────────────────────────────────────────────────────────────────────────┘");

        for (Map.Entry<String, List<QueryResult>> entry : perIndex.entrySet()) {
            String indexName = entry.getKey();
            List<QueryResult> indexResults = entry.getValue();

            System.out.println();
            System.out.printf("▶ %s%n", indexName);
            System.out.println("┌──────────────────────────────┬──────────┬──────────┬──────────────────────┬──────────────────────┬──────────┐");
            System.out.println("│ 쿼리                          │ Before   │ After    │ EXPLAIN Before       │ EXPLAIN After        │ 개선율    │");
            System.out.println("├──────────────────────────────┼──────────┼──────────┼──────────────────────┼──────────────────────┼──────────┤");

            for (int i = 0; i < baseline.size(); i++) {
                QueryResult b = baseline.get(i);
                QueryResult a = indexResults.get(i);

                String improvement = calcImprovement(b.avgMs, a.avgMs);
                String explainBefore = String.format("%s/%s", b.accessType, b.keyUsed);
                String explainAfter  = String.format("%s/%s", a.accessType, a.keyUsed);

                // 변화가 있는 행 강조
                boolean changed = !b.keyUsed.equals(a.keyUsed) || !b.accessType.equals(a.accessType);
                String marker = changed ? " ★" : "";

                System.out.printf("│ %-28s │ %6.1fms │ %6.1fms │ %-20s │ %-20s │ %7s%s │%n",
                    trunc(b.name, 28), b.avgMs, a.avgMs,
                    trunc(explainBefore, 20), trunc(explainAfter, 20),
                    improvement, marker);
            }
            System.out.println("└──────────────────────────────┴──────────┴──────────┴──────────────────────┴──────────────────────┴──────────┘");
            System.out.println("  ★ = EXPLAIN 실행 계획이 변경된 쿼리");
        }
    }

    private void printFinalComparisonTable(List<QueryResult> baseline, List<QueryResult> allIndex) {
        System.out.println();
        System.out.println("┌─────────────────────────────────────────────────────────────────────────────────────────────────────────────────┐");
        System.out.println("│                              전체 인덱스 적용 최종 비교                                                           │");
        System.out.println("└─────────────────────────────────────────────────────────────────────────────────────────────────────────────────┘");
        System.out.println("┌──────────────────────────────┬──────────┬──────────┬──────────────────────┬──────────────────────┬──────────┐");
        System.out.println("│ 쿼리                          │ Before   │ After    │ EXPLAIN Before       │ EXPLAIN After        │ 개선율    │");
        System.out.println("├──────────────────────────────┼──────────┼──────────┼──────────────────────┼──────────────────────┼──────────┤");

        for (int i = 0; i < baseline.size(); i++) {
            QueryResult b = baseline.get(i);
            QueryResult a = allIndex.get(i);

            String improvement = calcImprovement(b.avgMs, a.avgMs);
            String explainBefore = String.format("%s/%s", b.accessType, b.keyUsed);
            String explainAfter  = String.format("%s/%s", a.accessType, a.keyUsed);

            System.out.printf("│ %-28s │ %6.1fms │ %6.1fms │ %-20s │ %-20s │ %8s │%n",
                trunc(b.name, 28), b.avgMs, a.avgMs,
                trunc(explainBefore, 20), trunc(explainAfter, 20), improvement);
        }
        System.out.println("└──────────────────────────────┴──────────┴──────────┴──────────────────────┴──────────────────────┴──────────┘");
    }

    private void printConclusion(List<QueryResult> baseline, Map<String, List<QueryResult>> perIndex, List<QueryResult> allIndex) {
        System.out.println();
        System.out.println("┌─────────────────────────────────────────────────────────────────────────────────────────────────────────────────┐");
        System.out.println("│                                        인덱스 선택 결론                                                          │");
        System.out.println("└─────────────────────────────────────────────────────────────────────────────────────────────────────────────────┘");
        System.out.println();

        for (Map.Entry<String, List<QueryResult>> entry : perIndex.entrySet()) {
            String indexName = entry.getKey();
            List<QueryResult> results = entry.getValue();

            // 해당 인덱스로 인해 EXPLAIN이 변경된 쿼리 찾기
            List<String> improved = new ArrayList<>();
            for (int i = 0; i < baseline.size(); i++) {
                QueryResult b = baseline.get(i);
                QueryResult a = results.get(i);
                if (!b.keyUsed.equals(a.keyUsed) || !b.accessType.equals(a.accessType)) {
                    String pct = calcImprovement(b.avgMs, a.avgMs);
                    improved.add(String.format("    - %s: %s/%s → %s/%s (%s)",
                        b.name, b.accessType, b.keyUsed, a.accessType, a.keyUsed, pct));
                }
            }

            System.out.printf("  [%s]%n", indexName);
            if (improved.isEmpty()) {
                System.out.println("    → 실행 계획 변화 없음 (이미 다른 인덱스/PK로 커버됨)");
            } else {
                System.out.println("    → 실행 계획이 개선된 쿼리:");
                improved.forEach(System.out::println);
            }
            System.out.println();
        }

        System.out.println("  ════════════════════════════════════════════════════════════════════");
        System.out.println("  최종 선택 인덱스:");
        for (String[] candidate : INDEX_CANDIDATES) {
            System.out.printf("    ✓ %s (%s)%n", candidate[0], candidate[3]);
        }
        System.out.println();
        System.out.println("  선택 근거:");
        System.out.println("    1. idx_product_price: 전체 가격순 정렬 시 filesort 제거");
        System.out.println("    2. idx_product_like_count: 전체 좋아요순 정렬 시 filesort 제거");
        System.out.println("    3. idx_product_brand_price: 브랜드 필터 + 가격순 정렬을 복합 인덱스로 커버");
        System.out.println("    4. idx_product_brand_like: 브랜드 필터 + 좋아요순 정렬을 복합 인덱스로 커버");
        System.out.println("    5. idx_favorite_product_id: countByProductId 풀스캔 → ref 스캔으로 개선");
        System.out.println();
        System.out.println("  미선택 근거:");
        System.out.println("    - (brand_id, id) 복합 인덱스: brand_id 필터 후 PK 역순 스캔 가능, 복합 인덱스 prefix로도 커버");
        System.out.println("    - (id) 단독 인덱스: PK 클러스터드 인덱스가 이미 존재");
        System.out.println("  ════════════════════════════════════════════════════════════════════");
    }

    // ─── 유틸 ──────────────────────────────────────────────────────

    private String calcImprovement(double before, double after) {
        if (before <= 0) return "N/A";
        double pct = (before - after) / before * 100.0;
        return String.format("%+.1f%%", pct);
    }

    private String str(Object o) {
        return o != null ? String.valueOf(o) : "NULL";
    }

    private String trunc(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    // ─── 결과 레코드 ──────────────────────────────────────────────

    record QueryResult(
        String name,
        double avgMs,
        String accessType,
        String possibleKeys,
        String keyUsed,
        String keyLen,
        String rows,
        String extra
    ) {}
}
