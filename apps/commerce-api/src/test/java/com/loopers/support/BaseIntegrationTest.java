package com.loopers.support;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductService;
import com.loopers.utils.DatabaseCleanUp;

/**
 * 통합 테스트의 공통 설정을 제공한다.
 *
 * <p>테스트 데이터 생성 헬퍼 메서드를 제공하며,
 * 각 테스트 종료 후 데이터베이스를 자동으로 초기화한다.
 */
@SpringBootTest
public abstract class BaseIntegrationTest {

    @Autowired
    protected BrandService brandService;

    @Autowired
    protected ProductService productService;

    @Autowired
    protected DatabaseCleanUp databaseCleanUp;

    /**
     * 기본 브랜드를 생성하고 ID를 반환한다.
     *
     * @return 생성된 브랜드의 ID
     */
    protected Long initDefaultBrand() {
        return brandService.create("브랜드명", "https://example.com/logo.png", "브랜드 설명").getId();
    }

    /**
     * 기본값으로 상품을 생성하고 ID를 반환한다.
     *
     * @param brandId 상품이 속할 브랜드 ID
     * @return 생성된 상품의 ID
     */
    protected Long createProduct(Long brandId) {
        return createProduct(brandId, "상품명", 10000L, 100L);
    }

    /**
     * 지정된 속성으로 상품을 생성하고 ID를 반환한다.
     *
     * @param brandId 상품이 속할 브랜드 ID
     * @param name    상품명
     * @param price   상품 가격
     * @param stock   재고 수량
     * @return 생성된 상품의 ID
     */
    protected Long createProduct(Long brandId, String name, Long price, Long stock) {
        return productService.create(brandId, name, "https://example.com/thumb.png", price, stock, "상품 설명").getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }
}
