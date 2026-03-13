package com.loopers.infrastructure.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductStatus;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("상품 조회 EXPLAIN 분석 — 인덱스 최적화 검증")
class ProductQueryExplainTest {

    private static final Logger log = LoggerFactory.getLogger(ProductQueryExplainTest.class);
    private static final int BRAND_COUNT = 100;
    private static final int PRODUCT_COUNT = 100_000;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @BeforeAll
    void setUp() {
        databaseCleanUp.truncateAllTables();
        insertTestData();
    }

    @AfterAll
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private void insertTestData() {
        log.info("=== 테스트 데이터 적재 시작: brands={}, products={} ===", BRAND_COUNT, PRODUCT_COUNT);

        List<BrandModel> brands = new ArrayList<>();
        for (int i = 1; i <= BRAND_COUNT; i++) {
            brands.add(new BrandModel("brand-" + i, "desc-" + i));
        }
        brandJpaRepository.saveAll(brands);
        brandJpaRepository.flush();

        Random random = new Random(42);
        int batchSize = 5000;
        for (int batch = 0; batch < PRODUCT_COUNT / batchSize; batch++) {
            List<ProductModel> products = new ArrayList<>();
            for (int i = 0; i < batchSize; i++) {
                int n = batch * batchSize + i + 1;
                BrandModel brand = brands.get(n % BRAND_COUNT);
                long price = 1000L + (n * 37L) % 500000L;
                int stock = (n * 13) % 1000;
                long likeCount = random.nextInt(500);

                ProductModel product = new ProductModel(brand, "product-" + n, price, "description-" + n, stock, ProductStatus.ON_SALE);
                for (int lc = 0; lc < likeCount; lc++) {
                    product.increaseLikeCount();
                }
                products.add(product);
            }
            productJpaRepository.saveAll(products);
            productJpaRepository.flush();
            entityManager.clear();
        }

        log.info("=== 테스트 데이터 적재 완료 ===");
    }

    @DisplayName("UC1: 전체 + 최신순 — idx_products_deleted_created_id 사용 확인")
    @Test
    void explain_allProducts_sortByLatest() {
        String sql = "EXPLAIN SELECT p.id, p.name, p.price, p.like_count " +
            "FROM products p " +
            "WHERE p.deleted_at IS NULL " +
            "ORDER BY p.created_at DESC, p.id DESC " +
            "LIMIT 20";

        List<Object[]> result = executeExplain(sql);
        logExplainResult("UC1: 전체 + 최신순", result);

        String possibleKeys = possibleKeys(result.get(0));
        assertThat(possibleKeys).contains("idx_products_deleted_created_id");
    }

    @DisplayName("UC2: 전체 + 가격 오름차순 — idx_products_deleted_price_id 사용 확인")
    @Test
    void explain_allProducts_sortByPriceAsc() {
        String sql = "EXPLAIN SELECT p.id, p.name, p.price, p.like_count " +
            "FROM products p " +
            "WHERE p.deleted_at IS NULL " +
            "ORDER BY p.price ASC, p.id ASC " +
            "LIMIT 20";

        List<Object[]> result = executeExplain(sql);
        logExplainResult("UC2: 전체 + 가격 오름차순", result);

        String possibleKeys = possibleKeys(result.get(0));
        assertThat(possibleKeys).contains("idx_products_deleted_price_id");
    }

    @DisplayName("UC3: 전체 + 좋아요순 — idx_products_deleted_like_id 사용 확인")
    @Test
    void explain_allProducts_sortByLikesDesc() {
        String sql = "EXPLAIN SELECT p.id, p.name, p.price, p.like_count " +
            "FROM products p " +
            "WHERE p.deleted_at IS NULL " +
            "ORDER BY p.like_count DESC, p.id DESC " +
            "LIMIT 20";

        List<Object[]> result = executeExplain(sql);
        logExplainResult("UC3: 전체 + 좋아요순", result);

        String extra = extra(result.get(0));
        assertThat(extra).doesNotContain("Using filesort");
    }

    @DisplayName("UC4: 브랜드 필터 + 좋아요순 — idx_products_brand_deleted_like_id 사용 확인")
    @Test
    void explain_brandFilter_sortByLikesDesc() {
        String sql = "EXPLAIN SELECT p.id, p.name, p.price, p.like_count " +
            "FROM products p " +
            "WHERE p.deleted_at IS NULL " +
            "  AND p.brand_id = 10 " +
            "ORDER BY p.like_count DESC, p.id DESC " +
            "LIMIT 20";

        List<Object[]> result = executeExplain(sql);
        logExplainResult("UC4: 브랜드 필터 + 좋아요순", result);

        String key = key(result.get(0));
        assertThat(key).contains("idx_products_brand_deleted_like_id");
    }

    @DisplayName("AS-IS vs TO-BE 비교: 브랜드 필터 + 좋아요순 (JOIN+GROUP BY vs like_count)")
    @Test
    void compare_asIs_vs_toBe_brandFilter_likesDesc() {
        // AS-IS: JOIN + GROUP BY
        String asIsSql = "EXPLAIN SELECT p.id, p.name, COUNT(l.id) AS like_cnt " +
            "FROM products p " +
            "LEFT JOIN likes l ON l.product_id = p.id " +
            "WHERE p.deleted_at IS NULL " +
            "  AND p.brand_id = 10 " +
            "GROUP BY p.id " +
            "ORDER BY like_cnt DESC, p.id DESC " +
            "LIMIT 20";

        // TO-BE: 비정규화 like_count
        String toBeSql = "EXPLAIN SELECT p.id, p.name, p.like_count " +
            "FROM products p " +
            "WHERE p.deleted_at IS NULL " +
            "  AND p.brand_id = 10 " +
            "ORDER BY p.like_count DESC, p.id DESC " +
            "LIMIT 20";

        List<Object[]> asIsResult = executeExplain(asIsSql);
        List<Object[]> toBeResult = executeExplain(toBeSql);

        logExplainResult("AS-IS (JOIN + GROUP BY)", asIsResult);
        logExplainResult("TO-BE (like_count 비정규화)", toBeResult);

        // TO-BE는 filesort 없이 인덱스만으로 처리
        String toBeExtra = extra(toBeResult.get(0));
        assertThat(toBeExtra).doesNotContain("Using filesort");
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> executeExplain(String sql) {
        Query query = entityManager.createNativeQuery(sql);
        return query.getResultList();
    }

    private void logExplainResult(String label, List<Object[]> rows) {
        log.info("========== EXPLAIN: {} ==========", label);
        log.info("{}", String.format("%-4s %-12s %-16s %-8s %-24s %-40s %-40s %-8s %-8s %-30s",
            "id", "select_type", "table", "type", "possible_keys", "key", "key_len", "rows", "filtered", "Extra"));
        for (Object[] row : rows) {
            log.info("{}", String.format("%-4s %-12s %-16s %-8s %-24s %-40s %-40s %-8s %-8s %-30s",
                id(row), selectType(row), table(row), type(row),
                truncate(possibleKeys(row), 24),
                truncate(key(row), 40),
                truncate(keyLen(row), 40),
                rows(row), filtered(row),
                truncate(extra(row), 30)));
        }
        log.info("================================================");
    }

    private String id(Object[] row) {
        return String.valueOf(row[0]);
    }

    private String selectType(Object[] row) {
        return String.valueOf(row[1]);
    }

    private String table(Object[] row) {
        return String.valueOf(row[2]);
    }

    private String type(Object[] row) {
        int index = row.length >= 12 ? 4 : 3;
        return String.valueOf(row[index]);
    }

    private String possibleKeys(Object[] row) {
        int index = row.length >= 12 ? 5 : 4;
        return String.valueOf(row[index]);
    }

    private String key(Object[] row) {
        int index = row.length >= 12 ? 6 : 5;
        return String.valueOf(row[index]);
    }

    private String keyLen(Object[] row) {
        int index = row.length >= 12 ? 7 : 6;
        return String.valueOf(row[index]);
    }

    private String rows(Object[] row) {
        int index = row.length >= 12 ? 9 : 8;
        return String.valueOf(row[index]);
    }

    private String filtered(Object[] row) {
        int index = row.length >= 12 ? 10 : 9;
        return String.valueOf(row[index]);
    }

    private String extra(Object[] row) {
        int index = row.length - 1;
        return String.valueOf(row[index]);
    }

    private String truncate(Object obj, int maxLen) {
        String s = String.valueOf(obj);
        return s.length() > maxLen ? s.substring(0, maxLen - 3) + "..." : s;
    }
}
