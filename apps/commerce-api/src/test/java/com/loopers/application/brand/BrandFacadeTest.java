package com.loopers.application.brand;

import com.loopers.application.product.ProductCreateCommand;
import com.loopers.application.product.ProductInfo;
import com.loopers.application.product.ProductService;
import com.loopers.domain.brand.InMemoryBrandRepository;
import com.loopers.domain.product.InMemoryProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BrandFacadeTest {
    private InMemoryBrandRepository brandRepository;
    private InMemoryProductRepository productRepository;
    private BrandService brandService;
    private ProductService productService;
    private BrandFacade brandFacade;

    @BeforeEach
    void setUp() {
        brandRepository = new InMemoryBrandRepository();
        productRepository = new InMemoryProductRepository();
        brandService = new BrandService(brandRepository);
        productService = new ProductService(productRepository, brandService);
        brandFacade = new BrandFacade(brandService, productService);
    }

    @DisplayName("브랜드 삭제 시, ")
    @Nested
    class Delete {
        @DisplayName("소속 상품이 모두 soft delete 된다.")
        @Test
        void softDeletesAllProducts_whenBrandIsDeleted() {
            // arrange
            BrandInfo brand = brandService.register("나이키", "스포츠 브랜드");
            ProductInfo product1 = productService.register(new ProductCreateCommand(brand.id(), "에어맥스", "신발", 150000, 10));
            ProductInfo product2 = productService.register(new ProductCreateCommand(brand.id(), "조던", "농구화", 200000, 5));

            // act
            brandFacade.delete(brand.id());

            // assert
            assertThat(productRepository.findById(product1.id()).orElseThrow().getDeletedAt()).isNotNull();
            assertThat(productRepository.findById(product2.id()).orElseThrow().getDeletedAt()).isNotNull();
        }

        @DisplayName("브랜드가 soft delete 된다.")
        @Test
        void softDeletesBrand() {
            // arrange
            BrandInfo brand = brandService.register("나이키", "스포츠 브랜드");

            // act
            brandFacade.delete(brand.id());

            // assert
            assertThat(brandRepository.findById(brand.id()).orElseThrow().getDeletedAt()).isNotNull();
        }

        @DisplayName("소속 상품이 없어도 브랜드만 soft delete 된다.")
        @Test
        void softDeletesBrandOnly_whenNoProducts() {
            // arrange
            BrandInfo brand = brandService.register("나이키", "스포츠 브랜드");

            // act
            brandFacade.delete(brand.id());

            // assert
            assertThat(brandRepository.findById(brand.id()).orElseThrow().getDeletedAt()).isNotNull();
        }
    }
}
