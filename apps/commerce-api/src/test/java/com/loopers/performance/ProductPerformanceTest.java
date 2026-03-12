package com.loopers.performance;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.like.Like;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.vo.Price;
import com.loopers.domain.product.vo.Stock;
import com.loopers.utils.DatabaseCleanUp;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@SpringBootTest
@Disabled("성능 테스트는 수동 실행. 10만 건 시딩에 수 분 소요")
class ProductPerformanceTest {

    private static final Logger log = LoggerFactory.getLogger(ProductPerformanceTest.class);

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private LikeRepository likeRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private final Random random = new Random(42);

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("10만 건 데이터 시딩 + EXPLAIN 분석")
    @Test
    void seedAndAnalyze() {
        // === 1. 데이터 시딩 ===
        log.info("=== 데이터 시딩 시작 ===");
        int brandCount = 100;
        int productCount = 100_000;
        int productPerBrand = productCount / brandCount;

        // 브랜드 100개
        List<Brand> brands = new ArrayList<>();
        for (int i = 0; i < brandCount; i++) {
            brands.add(brandRepository.save(new Brand("브랜드" + i, "설명" + i)));
        }
        log.info("브랜드 {} 개 생성 완료", brandCount);

        // 상품 10만 개 (브랜드당 ~1,000개)
        List<Product> products = new ArrayList<>();
        for (int i = 0; i < productCount; i++) {
            Brand brand = brands.get(i / productPerBrand);
            int price = 1000 + random.nextInt(499_000); // 1,000 ~ 500,000
            Product product = productRepository.save(
                new Product(brand.getId(), "상품" + i, new Price(price), new Stock(random.nextInt(100))));
            products.add(product);

            if ((i + 1) % 10_000 == 0) {
                log.info("상품 {} 개 생성 완료", i + 1);
            }
        }

        // likeCount 설정 (멱법칙 분포 — 소수 상품이 높은 좋아요)
        for (int i = 0; i < productCount; i++) {
            int likes = (int) Math.round(Math.pow(random.nextDouble(), 3) * 10_000);
            if (likes > 0) {
                Product p = products.get(i);
                for (int j = 0; j < likes && j < 50; j++) { // 실제 Like 레코드는 최대 50개만
                    try {
                        likeRepository.save(new Like((long) (i * 100 + j + 1), p.getId()));
                        productRepository.incrementLikeCount(p.getId());
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        log.info("좋아요 데이터 생성 완료");

        // === 2. EXPLAIN 분석 ===
        log.info("=== EXPLAIN 분석 시작 ===");

        // 전체 상품 좋아요순 정렬
        analyzeQuery("전체 상품 + 좋아요순",
            "EXPLAIN SELECT p.*, b.name FROM product p LEFT JOIN brand b ON b.id = p.brand_id "
                + "WHERE p.deleted_at IS NULL AND (b.deleted_at IS NULL OR b.id IS NULL) "
                + "ORDER BY p.like_count DESC, p.id DESC LIMIT 20");

        // 브랜드 필터 + 좋아요순
        Long firstBrandId = brands.get(0).getId();
        analyzeQuery("브랜드 필터 + 좋아요순",
            "EXPLAIN SELECT p.*, b.name FROM product p LEFT JOIN brand b ON b.id = p.brand_id "
                + "WHERE p.brand_id = " + firstBrandId + " AND p.deleted_at IS NULL AND (b.deleted_at IS NULL OR b.id IS NULL) "
                + "ORDER BY p.like_count DESC, p.id DESC LIMIT 20");

        // 브랜드 필터 + 가격순
        analyzeQuery("브랜드 필터 + 가격순",
            "EXPLAIN SELECT p.*, b.name FROM product p LEFT JOIN brand b ON b.id = p.brand_id "
                + "WHERE p.brand_id = " + firstBrandId + " AND p.deleted_at IS NULL AND (b.deleted_at IS NULL OR b.id IS NULL) "
                + "ORDER BY p.price ASC, p.id ASC LIMIT 20");

        // Like countByProductId
        Long firstProductId = products.get(0).getId();
        analyzeQuery("좋아요 카운트 (product_id 인덱스 활용)",
            "EXPLAIN SELECT COUNT(*) FROM likes WHERE product_id = " + firstProductId);

        // AS-IS: GROUP BY 집계
        analyzeQuery("AS-IS: 전체 상품 GROUP BY 좋아요 집계",
            "EXPLAIN SELECT l.product_id, COUNT(*) FROM likes l GROUP BY l.product_id");

        log.info("=== EXPLAIN 분석 완료 ===");
    }

    private void analyzeQuery(String label, String explainSql) {
        Query query = entityManager.createNativeQuery(explainSql);
        List<?> results = query.getResultList();

        log.info("\n--- {} ---", label);
        log.info("SQL: {}", explainSql.replace("EXPLAIN ", ""));
        for (Object row : results) {
            if (row instanceof Object[] cols) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < cols.length; i++) {
                    sb.append(cols[i] != null ? cols[i].toString() : "NULL");
                    if (i < cols.length - 1) sb.append(" | ");
                }
                log.info("  {}", sb.toString());
            }
        }
    }
}
