package com.loopers.infrastructure.like;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.like.ProductLike;
import com.loopers.domain.like.ProductLikeRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ProductLikeRepositoryIntegrationTest {

    @Autowired
    private ProductLikeRepository productLikeRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private Brand createBrand() {
        return brandRepository.save(Brand.register("나이키", "스포츠 브랜드"));
    }

    private Product createProduct(Long brandId, String name) {
        return productRepository.save(Product.register(brandId, name, name + " 설명", 10000));
    }

    @Nested
    @DisplayName("save 메서드는")
    class Save {

        @Test
        void 새로운_좋아요를_저장하면_ID가_생성된다() {
            // arrange
            Brand brand = createBrand();
            Product product = createProduct(brand.getId(), "에어맥스");

            // act
            ProductLike saved = productLikeRepository.save(ProductLike.of(1L, product.getId()));

            // assert
            assertThat(saved.getId()).isNotNull();
        }

        @Test
        void 저장된_좋아요의_필드가_올바르게_저장된다() {
            // arrange
            Brand brand = createBrand();
            Product product = createProduct(brand.getId(), "에어맥스");

            // act
            ProductLike saved = productLikeRepository.save(ProductLike.of(1L, product.getId()));

            // assert
            assertThat(saved)
                    .extracting(ProductLike::getUserId, ProductLike::getProductId)
                    .containsExactly(1L, product.getId());
            assertThat(saved.getCreatedAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("findByUserIdAndProductId 메서드는")
    class FindByUserIdAndProductId {

        @Test
        void 존재하는_좋아요를_반환한다() {
            // arrange
            Brand brand = createBrand();
            Product product = createProduct(brand.getId(), "에어맥스");
            productLikeRepository.save(ProductLike.of(1L, product.getId()));

            // act
            Optional<ProductLike> result = productLikeRepository.findByUserIdAndProductId(1L, product.getId());

            // assert
            assertThat(result).isPresent();
        }

        @Test
        void 존재하지_않으면_empty를_반환한다() {
            // act
            Optional<ProductLike> result = productLikeRepository.findByUserIdAndProductId(1L, 999L);

            // assert
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("delete 메서드는")
    class Delete {

        @Test
        void 좋아요를_완전히_삭제한다() {
            // arrange
            Brand brand = createBrand();
            Product product = createProduct(brand.getId(), "에어맥스");
            ProductLike saved = productLikeRepository.save(ProductLike.of(1L, product.getId()));

            // act
            productLikeRepository.delete(saved);

            // assert
            Optional<ProductLike> result = productLikeRepository.findByUserIdAndProductId(1L, product.getId());
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findActiveByUserId 메서드는")
    class FindActiveByUserId {

        @Test
        void 삭제된_상품의_좋아요는_제외한다() {
            // arrange
            Brand brand = createBrand();
            Product active = createProduct(brand.getId(), "활성상품");
            Product deleted = createProduct(brand.getId(), "삭제상품");

            productLikeRepository.save(ProductLike.of(1L, active.getId()));
            productLikeRepository.save(ProductLike.of(1L, deleted.getId()));

            // 상품 소프트 삭제
            Product loadedDeleted = productRepository.findById(deleted.getId()).orElseThrow();
            loadedDeleted.discontinue();
            productRepository.save(loadedDeleted);

            // act
            List<ProductLike> result = productLikeRepository.findActiveByUserId(1L, 0, 20);

            // assert
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getProductId()).isEqualTo(active.getId());
        }

        @Test
        void 최근_좋아요순으로_반환한다() {
            // arrange
            Brand brand = createBrand();
            Product product1 = createProduct(brand.getId(), "상품1");
            Product product2 = createProduct(brand.getId(), "상품2");

            productLikeRepository.save(ProductLike.of(1L, product1.getId()));
            productLikeRepository.save(ProductLike.of(1L, product2.getId()));

            // act
            List<ProductLike> result = productLikeRepository.findActiveByUserId(1L, 0, 20);

            // assert
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getProductId()).isEqualTo(product2.getId());
        }

        @Test
        void 페이지네이션이_올바르게_동작한다() {
            // arrange
            Brand brand = createBrand();
            Product product1 = createProduct(brand.getId(), "상품1");
            Product product2 = createProduct(brand.getId(), "상품2");
            Product product3 = createProduct(brand.getId(), "상품3");

            productLikeRepository.save(ProductLike.of(1L, product1.getId()));
            productLikeRepository.save(ProductLike.of(1L, product2.getId()));
            productLikeRepository.save(ProductLike.of(1L, product3.getId()));

            // act
            List<ProductLike> result = productLikeRepository.findActiveByUserId(1L, 0, 2);

            // assert
            assertThat(result).hasSize(2);
        }
    }

    @Nested
    @DisplayName("countActiveByUserId 메서드는")
    class CountActiveByUserId {

        @Test
        void 삭제된_상품을_제외한_좋아요_수를_반환한다() {
            // arrange
            Brand brand = createBrand();
            Product active = createProduct(brand.getId(), "활성상품");
            Product deleted = createProduct(brand.getId(), "삭제상품");

            productLikeRepository.save(ProductLike.of(1L, active.getId()));
            productLikeRepository.save(ProductLike.of(1L, deleted.getId()));

            Product loadedDeleted = productRepository.findById(deleted.getId()).orElseThrow();
            loadedDeleted.discontinue();
            productRepository.save(loadedDeleted);

            // act
            long count = productLikeRepository.countActiveByUserId(1L);

            // assert
            assertThat(count).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("existsByUserIdAndProductId 메서드는")
    class ExistsByUserIdAndProductId {

        @Test
        void 존재하면_true를_반환한다() {
            // arrange
            Brand brand = createBrand();
            Product product = createProduct(brand.getId(), "에어맥스");
            productLikeRepository.save(ProductLike.of(1L, product.getId()));

            // act & assert
            assertThat(productLikeRepository.existsByUserIdAndProductId(1L, product.getId())).isTrue();
        }

        @Test
        void 존재하지_않으면_false를_반환한다() {
            // act & assert
            assertThat(productLikeRepository.existsByUserIdAndProductId(1L, 999L)).isFalse();
        }
    }
}
