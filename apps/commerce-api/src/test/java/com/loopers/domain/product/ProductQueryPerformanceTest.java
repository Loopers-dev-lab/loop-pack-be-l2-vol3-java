package com.loopers.domain.product;

import com.loopers.testcontainers.MySqlTestContainersConfig;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(MySqlTestContainersConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProductQueryPerformanceTest {

    @Autowired
    private EntityManagerFactory entityManagerFactory;

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

        // 상품 10만건 (like_count = 0)
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

        // DDL은 별도 트랜잭션
        em.getTransaction().begin();
        em.createNativeQuery(
                "CREATE TABLE IF NOT EXISTS product_like_stats (product_id BIGINT PRIMARY KEY, like_count BIGINT NOT NULL DEFAULT 0)")
            .executeUpdate();
        em.getTransaction().commit();

        // stats 테이블 채우기 + product.like_count 동기화
        em.getTransaction().begin();
        em.createNativeQuery(
                "INSERT INTO product_like_stats (product_id, like_count) " +
                "SELECT p.id, COUNT(f.id) FROM product p LEFT JOIN favorite f ON p.id = f.product_id GROUP BY p.id")
            .executeUpdate();
        em.createNativeQuery(
                "UPDATE product p JOIN product_like_stats s ON p.id = s.product_id SET p.like_count = s.like_count")
            .executeUpdate();
        em.getTransaction().commit();

        em.close();
    }

    @DisplayName("비정규화 vs MaterializedView vs 정규화 성능 비교")
    @Nested
    class QueryPerformanceComparison {

        @DisplayName("방식 A: 비정규화 - 브랜드 필터 + 좋아요 순 정렬")
        @Test
        void denormalization_brandFilter_likeSort() {
            EntityManager em = entityManagerFactory.createEntityManager();

            em.createNativeQuery(
                    "SELECT p.id, p.name, p.price, p.stock, p.like_count " +
                    "FROM product p " +
                    "WHERE p.brand_id = 1 AND p.deleted_at IS NULL " +
                    "ORDER BY p.like_count DESC LIMIT 20")
                .getResultList();

            long start = System.currentTimeMillis();
            for (int i = 0; i < 10; i++) {
                em.createNativeQuery(
                        "SELECT p.id, p.name, p.price, p.stock, p.like_count " +
                        "FROM product p " +
                        "WHERE p.brand_id = 1 AND p.deleted_at IS NULL " +
                        "ORDER BY p.like_count DESC LIMIT 20")
                    .getResultList();
            }
            long elapsed = System.currentTimeMillis() - start;

            System.out.println("=== 방식 A (비정규화) ===");
            System.out.println("10회 실행 총 시간: " + elapsed + "ms");
            System.out.println("평균: " + (elapsed / 10.0) + "ms");

            var explain = em.createNativeQuery(
                    "EXPLAIN SELECT p.id, p.name, p.price, p.stock, p.like_count " +
                    "FROM product p " +
                    "WHERE p.brand_id = 1 AND p.deleted_at IS NULL " +
                    "ORDER BY p.like_count DESC LIMIT 20")
                .getResultList();
            System.out.println("EXPLAIN: " + explain);
            em.close();
        }

        @DisplayName("방식 B: MaterializedView - 브랜드 필터 + 좋아요 순 정렬")
        @Test
        void materializedView_brandFilter_likeSort() {
            EntityManager em = entityManagerFactory.createEntityManager();

            em.createNativeQuery(
                    "SELECT p.id, p.name, p.price, p.stock, s.like_count " +
                    "FROM product p JOIN product_like_stats s ON p.id = s.product_id " +
                    "WHERE p.brand_id = 1 AND p.deleted_at IS NULL " +
                    "ORDER BY s.like_count DESC LIMIT 20")
                .getResultList();

            long start = System.currentTimeMillis();
            for (int i = 0; i < 10; i++) {
                em.createNativeQuery(
                        "SELECT p.id, p.name, p.price, p.stock, s.like_count " +
                        "FROM product p JOIN product_like_stats s ON p.id = s.product_id " +
                        "WHERE p.brand_id = 1 AND p.deleted_at IS NULL " +
                        "ORDER BY s.like_count DESC LIMIT 20")
                    .getResultList();
            }
            long elapsed = System.currentTimeMillis() - start;

            System.out.println("=== 방식 B (MaterializedView) ===");
            System.out.println("10회 실행 총 시간: " + elapsed + "ms");
            System.out.println("평균: " + (elapsed / 10.0) + "ms");

            var explain = em.createNativeQuery(
                    "EXPLAIN SELECT p.id, p.name, p.price, p.stock, s.like_count " +
                    "FROM product p JOIN product_like_stats s ON p.id = s.product_id " +
                    "WHERE p.brand_id = 1 AND p.deleted_at IS NULL " +
                    "ORDER BY s.like_count DESC LIMIT 20")
                .getResultList();
            System.out.println("EXPLAIN: " + explain);
            em.close();
        }

        @DisplayName("방식 C: 정규화 - LEFT JOIN favorite + COUNT")
        @Test
        void normalized_brandFilter_likeSort() {
            EntityManager em = entityManagerFactory.createEntityManager();

            em.createNativeQuery(
                    "SELECT p.id, p.name, p.price, p.stock, COUNT(f.id) as like_count " +
                    "FROM product p LEFT JOIN favorite f ON p.id = f.product_id " +
                    "WHERE p.brand_id = 1 AND p.deleted_at IS NULL " +
                    "GROUP BY p.id ORDER BY like_count DESC LIMIT 20")
                .getResultList();

            long start = System.currentTimeMillis();
            for (int i = 0; i < 10; i++) {
                em.createNativeQuery(
                        "SELECT p.id, p.name, p.price, p.stock, COUNT(f.id) as like_count " +
                        "FROM product p LEFT JOIN favorite f ON p.id = f.product_id " +
                        "WHERE p.brand_id = 1 AND p.deleted_at IS NULL " +
                        "GROUP BY p.id ORDER BY like_count DESC LIMIT 20")
                    .getResultList();
            }
            long elapsed = System.currentTimeMillis() - start;

            System.out.println("=== 방식 C (정규화: LEFT JOIN + COUNT) ===");
            System.out.println("10회 실행 총 시간: " + elapsed + "ms");
            System.out.println("평균: " + (elapsed / 10.0) + "ms");

            var explain = em.createNativeQuery(
                    "EXPLAIN SELECT p.id, p.name, p.price, p.stock, COUNT(f.id) as like_count " +
                    "FROM product p LEFT JOIN favorite f ON p.id = f.product_id " +
                    "WHERE p.brand_id = 1 AND p.deleted_at IS NULL " +
                    "GROUP BY p.id ORDER BY like_count DESC LIMIT 20")
                .getResultList();
            System.out.println("EXPLAIN: " + explain);
            em.close();
        }
    }
}
