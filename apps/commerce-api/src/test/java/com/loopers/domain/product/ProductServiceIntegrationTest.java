package com.loopers.domain.product;

import com.loopers.domain.brand.model.Brand;
import com.loopers.domain.brand.model.BrandCommand;
import com.loopers.domain.product.model.Product;
import com.loopers.domain.product.model.ProductCommand;
import com.loopers.domain.product.service.ProductService;
import com.loopers.domain.product.vo.DisplayStatus;
import com.loopers.infrastructure.brand.entity.BrandEntity;
import com.loopers.infrastructure.brand.repository.BrandJpaRepository;
import com.loopers.infrastructure.product.entity.ProductEntity;
import com.loopers.infrastructure.product.repository.ProductJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
@Import(MySqlTestContainersConfig.class)
class ProductServiceIntegrationTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @BeforeEach
    void setUp() {
        productJpaRepository.deleteAll();
        brandJpaRepository.deleteAll();
    }

    private BrandEntity saveBrand() {
        Brand brand = Brand.create(new BrandCommand.Create("테스트브랜드", "테스트 설명"));
        return brandJpaRepository.save(BrandEntity.toEntity(brand));
    }

    private ProductEntity saveProduct(Long brandId, String name, int price, int stock) {
        Product product = Product.create(brandId, new ProductCommand.Create(brandId, name, price, stock));
        return productJpaRepository.save(ProductEntity.toEntity(product));
    }

    @DisplayName("상품 생성")
    @Nested
    class CreateProduct {

        @DisplayName("정상적인 정보로 상품을 생성하면 DB에 저장된다")
        @Test
        void createProduct_success() {
            // arrange
            BrandEntity brand = saveBrand();
            ProductCommand.Create command = new ProductCommand.Create(brand.getId(), "운동화", 50000, 100);

            // act
            Product result = productService.createProduct(brand.getId(), command);

            // assert
            assertThat(result.getId()).isNotNull();
            ProductEntity saved = productJpaRepository.findById(result.getId()).orElseThrow();
            assertThat(saved.getName()).isEqualTo("운동화");
            assertThat(saved.getPrice()).isEqualTo(50000);
            assertThat(saved.getStock()).isEqualTo(100);
            assertThat(saved.getBrandId()).isEqualTo(brand.getId());
        }
    }

    @DisplayName("상품 조회")
    @Nested
    class FindProduct {

        @DisplayName("존재하지 않는 상품을 조회하면 NOT_FOUND 예외가 발생한다")
        @Test
        void findProduct_notFound() {
            // act & assert
            assertThatThrownBy(() -> productService.findProduct(999L))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
        }

        @DisplayName("존재하는 상품을 조회하면 상품 정보를 반환한다")
        @Test
        void findProduct_success() {
            // arrange
            BrandEntity brand = saveBrand();
            ProductEntity saved = saveProduct(brand.getId(), "런닝화", 80000, 50);

            // act
            Product result = productService.findProduct(saved.getId());

            // assert
            assertThat(result.getName().value()).isEqualTo("런닝화");
            assertThat(result.getPrice().value()).isEqualTo(80000);
            assertThat(result.getStock().value()).isEqualTo(50);
        }
    }

    @DisplayName("상품 수정")
    @Nested
    class UpdateProduct {

        @DisplayName("존재하지 않는 상품을 수정하면 NOT_FOUND 예외가 발생한다")
        @Test
        void updateProduct_notFound() {
            // arrange
            ProductCommand.Update command = new ProductCommand.Update("수정상품", 30000, 20, DisplayStatus.DISPLAYING);

            // act & assert
            assertThatThrownBy(() -> productService.updateProduct(999L, command))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
        }

        @DisplayName("존재하는 상품을 수정하면 DB에 반영된다")
        @Test
        void updateProduct_success() {
            // arrange
            BrandEntity brand = saveBrand();
            ProductEntity saved = saveProduct(brand.getId(), "원래상품", 10000, 30);
            ProductCommand.Update command = new ProductCommand.Update("수정상품", 20000, 50, DisplayStatus.DISPLAYING);

            // act
            Product result = productService.updateProduct(saved.getId(), command);

            // assert
            assertThat(result.getName().value()).isEqualTo("수정상품");
            assertThat(result.getPrice().value()).isEqualTo(20000);
            assertThat(result.getStock().value()).isEqualTo(50);
        }
    }

    @DisplayName("재고 차감")
    @Nested
    class DecreaseStock {

        @DisplayName("재고보다 많은 수량을 차감하면 BAD_REQUEST 예외가 발생한다")
        @Test
        void decreaseStock_insufficientStock() {
            // arrange
            BrandEntity brand = saveBrand();
            ProductEntity saved = saveProduct(brand.getId(), "재고상품", 10000, 5);
            Product product = productService.findProduct(saved.getId());

            // act & assert
            assertThatThrownBy(() -> productService.decreaseStock(product, 10))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @DisplayName("재고 이하의 수량을 차감하면 DB에 반영된다")
        @Test
        void decreaseStock_success() {
            // arrange
            BrandEntity brand = saveBrand();
            ProductEntity saved = saveProduct(brand.getId(), "재고상품", 10000, 10);
            Product product = productService.findProduct(saved.getId());

            // act
            productService.decreaseStock(product, 3);

            // assert
            ProductEntity updated = productJpaRepository.findById(saved.getId()).orElseThrow();
            assertThat(updated.getStock()).isEqualTo(7);
        }
    }

    @DisplayName("상품 삭제")
    @Nested
    class DeleteProduct {

        @DisplayName("존재하지 않는 상품을 삭제하면 NOT_FOUND 예외가 발생한다")
        @Test
        void deleteProduct_notFound() {
            // act & assert
            assertThatThrownBy(() -> productService.deleteProduct(999L))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
        }

        @DisplayName("존재하는 상품을 삭제하면 DB에서 삭제된다")
        @Test
        void deleteProduct_success() {
            // arrange
            BrandEntity brand = saveBrand();
            ProductEntity saved = saveProduct(brand.getId(), "삭제상품", 10000, 10);

            // act
            productService.deleteProduct(saved.getId());

            // assert
            assertThat(productJpaRepository.findById(saved.getId())).isEmpty();
        }
    }

    @DisplayName("ID 목록으로 상품 조회")
    @Nested
    class GetProductsByIds {

        @DisplayName("요청한 ID 중 일부가 존재하지 않으면 NOT_FOUND 예외가 발생한다")
        @Test
        void getProductsByIds_partialNotFound() {
            // arrange
            BrandEntity brand = saveBrand();
            ProductEntity saved = saveProduct(brand.getId(), "상품1", 10000, 10);

            // act & assert
            assertThatThrownBy(() -> productService.getProductsByIds(List.of(saved.getId(), 999L)))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
        }

        @DisplayName("모든 ID가 존재하면 상품 목록을 반환한다")
        @Test
        void getProductsByIds_success() {
            // arrange
            BrandEntity brand = saveBrand();
            ProductEntity saved1 = saveProduct(brand.getId(), "상품1", 10000, 10);
            ProductEntity saved2 = saveProduct(brand.getId(), "상품2", 20000, 20);

            // act
            List<Product> result = productService.getProductsByIds(List.of(saved1.getId(), saved2.getId()));

            // assert
            assertThat(result).hasSize(2);
        }
    }

    @DisplayName("브랜드별 상품 조회")
    @Nested
    class FindProductsByBrandId {

        @DisplayName("해당 브랜드의 상품만 조회된다")
        @Test
        void findProductsByBrandId_success() {
            // arrange
            BrandEntity brandA = saveBrand();
            Brand brandModelB = Brand.create(new BrandCommand.Create("다른브랜드", "다른 설명"));
            BrandEntity brandB = brandJpaRepository.save(BrandEntity.toEntity(brandModelB));

            saveProduct(brandA.getId(), "상품A1", 10000, 10);
            saveProduct(brandA.getId(), "상품A2", 20000, 20);
            saveProduct(brandB.getId(), "상품B1", 30000, 30);

            // act
            List<Product> result = productService.findProductsByBrandId(brandA.getId());

            // assert
            assertThat(result).hasSize(2);
            assertThat(result).allMatch(p -> p.getBrandId().equals(brandA.getId()));
        }
    }
}
