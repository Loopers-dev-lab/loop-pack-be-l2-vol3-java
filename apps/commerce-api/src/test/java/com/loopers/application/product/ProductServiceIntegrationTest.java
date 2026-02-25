package com.loopers.application.product;

import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ProductServiceIntegrationTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

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

    @Nested
    class 상품_수정 {

        @Test
        void 유효한_정보로_수정하면_성공한다() {
            Product product = productService.register(1L, "운동화", new BigDecimal("50000"), 100, "편한 운동화");

            Product result = productService.update(product.getId(), "런닝화", new BigDecimal("60000"), 200, "가벼운 런닝화");

            assertThat(result.getName()).isEqualTo("런닝화");
            assertThat(result.getPrice()).isEqualByComparingTo(new BigDecimal("60000"));
            assertThat(result.getStockQuantity()).isEqualTo(200);
            assertThat(result.getDescription()).isEqualTo("가벼운 런닝화");
        }

        @Test
        void 미존재_상품이면_예외() {
            assertThatThrownBy(() -> productService.update(999L, "런닝화", null, null, null))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.NOT_FOUND))
                    .hasMessageContaining("존재하지 않는 상품입니다");
        }

        @Test
        void 삭제된_상품을_수정하면_예외() {
            Product product = productService.register(1L, "운동화", new BigDecimal("50000"), 100, "편한 운동화");
            product.delete();
            productRepository.save(product);

            assertThatThrownBy(() -> productService.update(product.getId(), "런닝화", null, null, null))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.NOT_FOUND))
                    .hasMessageContaining("존재하지 않는 상품입니다");
        }
    }

    @Nested
    class 상품_삭제 {

        @Test
        void 활성_상품을_삭제하면_삭제_상태로_변경된다() {
            Product product = productService.register(1L, "운동화", new BigDecimal("50000"), 100, "편한 운동화");

            productService.delete(product.getId());

            Product found = productRepository.findById(product.getId()).orElseThrow();
            assertThat(found.isDeleted()).isTrue();
        }

        @Test
        void 미존재_상품이면_예외() {
            assertThatThrownBy(() -> productService.delete(999L))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.NOT_FOUND))
                    .hasMessageContaining("존재하지 않는 상품입니다");
        }

        @Test
        void 삭제된_상품이면_예외() {
            Product product = productService.register(1L, "운동화", new BigDecimal("50000"), 100, "편한 운동화");
            productService.delete(product.getId());

            assertThatThrownBy(() -> productService.delete(product.getId()))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.NOT_FOUND))
                    .hasMessageContaining("존재하지 않는 상품입니다");
        }
    }
}
