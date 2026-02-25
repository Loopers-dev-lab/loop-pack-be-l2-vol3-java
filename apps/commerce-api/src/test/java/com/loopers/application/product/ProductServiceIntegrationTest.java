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

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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

    @Nested
    class 상품_목록_조회 {

        private static final Pageable DEFAULT_PAGEABLE = PageRequest.of(0, 20);

        @Test
        void 조건_없이_조회하면_전체_상품을_최신_등록순으로_페이징하여_반환한다() {
            productService.register(1L, "운동화A", new BigDecimal("10000"), 10, "설명A");
            productService.register(1L, "운동화B", new BigDecimal("20000"), 20, "설명B");
            productService.register(1L, "운동화C", new BigDecimal("30000"), 30, "설명C");

            Page<Product> result = productService.findProducts(null, null, null, DEFAULT_PAGEABLE);

            assertThat(result.getContent()).hasSize(3);
            assertThat(result.getContent().get(0).getName()).isEqualTo("운동화C");
            assertThat(result.getContent().get(1).getName()).isEqualTo("운동화B");
            assertThat(result.getContent().get(2).getName()).isEqualTo("운동화A");
            assertThat(result.getTotalElements()).isEqualTo(3);
        }

        @Test
        void 삭제된_상품도_포함하여_반환한다() {
            productService.register(1L, "운동화A", new BigDecimal("10000"), 10, "설명A");
            Product deleted = productService.register(1L, "운동화B", new BigDecimal("20000"), 20, "설명B");
            deleted.delete();
            productRepository.save(deleted);

            Page<Product> result = productService.findProducts(null, null, null, DEFAULT_PAGEABLE);

            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getContent()).extracting(Product::isDeleted)
                    .containsExactly(true, false);
        }

        @Test
        void name_키워드로_검색하면_상품명에_해당_키워드가_포함된_상품만_반환한다() {
            productService.register(1L, "런닝화", new BigDecimal("10000"), 10, "설명A");
            productService.register(1L, "운동화", new BigDecimal("20000"), 20, "설명B");
            productService.register(1L, "런닝 슈즈", new BigDecimal("30000"), 30, "설명C");

            Page<Product> result = productService.findProducts("런닝", null, null, DEFAULT_PAGEABLE);

            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getContent()).extracting(Product::getName)
                    .containsExactly("런닝 슈즈", "런닝화");
        }

        @Test
        void brandId로_필터링하면_해당_브랜드에_속한_상품만_반환한다() {
            productService.register(1L, "나이키 운동화", new BigDecimal("10000"), 10, "설명");
            productService.register(2L, "아디다스 운동화", new BigDecimal("20000"), 20, "설명");
            productService.register(1L, "나이키 런닝화", new BigDecimal("30000"), 30, "설명");

            Page<Product> result = productService.findProducts(null, 1L, null, DEFAULT_PAGEABLE);

            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getContent()).extracting(Product::getBrandId)
                    .containsOnly(1L);
        }

        @Test
        void deleted_true로_필터링하면_삭제된_상품만_반환한다() {
            productService.register(1L, "운동화A", new BigDecimal("10000"), 10, "설명A");
            Product deleted = productService.register(1L, "운동화B", new BigDecimal("20000"), 20, "설명B");
            deleted.delete();
            productRepository.save(deleted);

            Page<Product> result = productService.findProducts(null, null, true, DEFAULT_PAGEABLE);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getName()).isEqualTo("운동화B");
            assertThat(result.getContent().get(0).isDeleted()).isTrue();
        }

        @Test
        void deleted_false로_필터링하면_활성_상품만_반환한다() {
            productService.register(1L, "운동화A", new BigDecimal("10000"), 10, "설명A");
            Product deleted = productService.register(1L, "운동화B", new BigDecimal("20000"), 20, "설명B");
            deleted.delete();
            productRepository.save(deleted);

            Page<Product> result = productService.findProducts(null, null, false, DEFAULT_PAGEABLE);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getName()).isEqualTo("운동화A");
            assertThat(result.getContent().get(0).isDeleted()).isFalse();
        }

        @Test
        void 복합_필터를_동시에_적용할_수_있다() {
            productService.register(1L, "나이키 에어맥스", new BigDecimal("10000"), 10, "설명");
            Product deleted = productService.register(1L, "나이키 조던", new BigDecimal("20000"), 20, "설명");
            deleted.delete();
            productRepository.save(deleted);
            productService.register(2L, "나이키 콜라보", new BigDecimal("30000"), 30, "설명");

            Page<Product> result = productService.findProducts("나이키", 1L, false, DEFAULT_PAGEABLE);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getName()).isEqualTo("나이키 에어맥스");
        }

        @Test
        void 결과가_없으면_빈_페이지를_반환한다() {
            Page<Product> result = productService.findProducts("존재하지않는상품", null, null, DEFAULT_PAGEABLE);

            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isEqualTo(0);
        }
    }

    @Nested
    class 브랜드별_상품_일괄_삭제 {

        @Test
        void 해당_브랜드의_활성_상품이_모두_삭제_상태로_변경된다() {
            Product product1 = productService.register(1L, "운동화", new BigDecimal("50000"), 100, "편한 운동화");
            Product product2 = productService.register(1L, "런닝화", new BigDecimal("60000"), 200, "가벼운 런닝화");

            productService.deleteAllByBrandId(1L);

            Product found1 = productRepository.findById(product1.getId()).orElseThrow();
            Product found2 = productRepository.findById(product2.getId()).orElseThrow();
            assertThat(found1.isDeleted()).isTrue();
            assertThat(found2.isDeleted()).isTrue();
        }

        @Test
        void 해당_브랜드에_상품이_없으면_정상_처리된다() {
            assertThatCode(() -> productService.deleteAllByBrandId(999L))
                    .doesNotThrowAnyException();
        }

        @Test
        void 이미_삭제된_상품도_삭제_상태를_유지한다() {
            Product product = productService.register(1L, "운동화", new BigDecimal("50000"), 100, "편한 운동화");
            productService.delete(product.getId());

            productService.deleteAllByBrandId(1L);

            Product found = productRepository.findById(product.getId()).orElseThrow();
            assertThat(found.isDeleted()).isTrue();
        }

        @Test
        void 다른_브랜드의_상품은_영향받지_않는다() {
            productService.register(1L, "운동화", new BigDecimal("50000"), 100, "편한 운동화");
            Product otherBrandProduct = productService.register(2L, "샌들", new BigDecimal("30000"), 50, "여름 샌들");

            productService.deleteAllByBrandId(1L);

            Product found = productRepository.findById(otherBrandProduct.getId()).orElseThrow();
            assertThat(found.isDeleted()).isFalse();
        }
    }
}
