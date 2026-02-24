package com.loopers.support;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.loopers.application.brand.BrandService;
import com.loopers.application.product.ProductCommand;
import com.loopers.application.product.ProductService;
import com.loopers.utils.DatabaseCleanUp;

@SpringBootTest
public abstract class BaseIntegrationTest {

    @Autowired
    protected BrandService brandService;

    @Autowired
    protected ProductService productService;

    @Autowired
    protected DatabaseCleanUp databaseCleanUp;

    protected Long initDefaultBrand() {
        return brandService.createBrand("브랜드명", "https://example.com/logo.png", "브랜드 설명").id();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    protected Long createProduct(Long brandId) {
        return createProduct(brandId, "상품명", 10000L, 100L);
    }

    protected Long createProduct(Long brandId, String name, Long price, Long stock) {
        return productService.createProduct(new ProductCommand.CreateProductCommand(
                brandId, name, "https://example.com/thumb.png", price, stock, "상품 설명"
        ));
    }
}