package com.loopers;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.product.Product;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@SpringBootTest
@ActiveProfiles("local")
public class DataInitializer {

    // TestContainers(MySqlTestContainersConfig)가 System.setProperty로 DB URL을 덮어쓰므로
    // DynamicPropertySource로 로컬 MySQL로 재설정 (우선순위: DynamicPropertySource > System.setProperty)
    @DynamicPropertySource
    static void overrideDataSource(DynamicPropertyRegistry registry) {
        registry.add("datasource.mysql-jpa.main.jdbc-url", () -> "jdbc:mysql://localhost:3306/loopers");
        registry.add("datasource.mysql-jpa.main.username", () -> "application");
        registry.add("datasource.mysql-jpa.main.password", () -> "application");
    }

    @PersistenceContext
    EntityManager entityManager;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Disabled("성능 테스트 데이터 시딩 전용 - 수동 실행 필요")
    @Test
    void initialize() {
        List<Long> brandIds = saveBrands(50);

        for (int i = 0; i < brandIds.size(); i++) {
            Long brandId = brandIds.get(i);

            boolean isTopBrand = i < 10; // 앞 10개 브랜드만 top
            int productCount = isTopBrand ? 10_000 : 2_500;
            int likeMin = isTopBrand ? 300 : 0;
            int likeMax = isTopBrand ? 3_000 : 2_000;

            insertProducts(brandId, productCount);
            updateLikeCount(brandId, likeMin, likeMax);
        }
    }

    List<Long> saveBrands(int count) {
        return transactionTemplate.execute(status -> {
            List<Long> ids = new ArrayList<>();
            for (int i = 1; i <= count; i++) {
                Brand brand = Brand.create("브랜드-" + i, null);
                entityManager.persist(brand);
                entityManager.flush();
                ids.add(brand.getId());
            }

            return ids;
        });
    }

    void insertProducts(Long brandId, int count) {
        transactionTemplate.executeWithoutResult(status -> {
            for (int i = 0; i < count; i++) {
                int price = ThreadLocalRandom.current().nextInt(1_000, 100_001);
                entityManager.persist(Product.create(brandId,
                                        "상품_" + UUID.randomUUID().toString()
                                                            .substring(0, 8), null, price, 100));
                if (i % 500 == 499) {
                    entityManager.flush();
                    entityManager.clear();
                }
            }
        });
    }

    void updateLikeCount(Long brandId, int min, int max) {
        transactionTemplate.executeWithoutResult(status ->
            entityManager.createNativeQuery("UPDATE products SET like_count = FLOOR(:min + RAND() * (:max - :min + 1)) WHERE brand_id = :brandId")
                .setParameter("min", min)
                .setParameter("max", max)
                .setParameter("brandId", brandId)
                .executeUpdate()
        );
    }
}
