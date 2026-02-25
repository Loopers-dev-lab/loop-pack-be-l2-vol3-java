package com.loopers.application.product;

import com.loopers.domain.product.Product;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ProductServiceIntegrationTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    class 상품_등록 {

        @Test
        void 유효한_정보로_등록하면_상품이_생성된다() {
            Product result = productService.register(1L, "운동화", new BigDecimal("50000"), 100, "편한 운동화");

            assertThat(result.getId()).isNotNull();
            assertThat(result.getBrandId()).isEqualTo(1L);
            assertThat(result.getName()).isEqualTo("운동화");
            assertThat(result.getPrice()).isEqualByComparingTo(new BigDecimal("50000"));
            assertThat(result.getStockQuantity()).isEqualTo(100);
            assertThat(result.getDescription()).isEqualTo("편한 운동화");
            assertThat(result.getLikeCount()).isEqualTo(0);
        }
    }
}
