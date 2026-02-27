package com.loopers.application.product;

import com.loopers.application.brand.BrandInfo;
import com.loopers.application.brand.BrandApplicationService;
import com.loopers.application.like.LikeApplicationService;
import com.loopers.domain.brand.InMemoryBrandRepository;
import com.loopers.domain.like.InMemoryLikeRepository;
import com.loopers.domain.product.InMemoryProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ProductFacadeTest {
    private BrandApplicationService brandService;
    private ProductApplicationService productService;
    private LikeApplicationService likeService;
    private InMemoryLikeRepository likeRepository;
    private InMemoryProductRepository productRepository;
    private ProductFacade productFacade;

    @BeforeEach
    void setUp() {
        brandService = new BrandApplicationService(new InMemoryBrandRepository());
        productRepository = new InMemoryProductRepository();
        likeRepository = new InMemoryLikeRepository();
        productService = new ProductApplicationService(productRepository);
        likeService = new LikeApplicationService(likeRepository);
        productFacade = new ProductFacade(brandService, productService, likeService);
    }

    @DisplayName("활성 상품 단건 조회 시, ")
    @Nested
    class GetActiveProduct {
        @DisplayName("상품 응답에 브랜드 이름이 포함된다.")
        @Test
        void returnsBrandName_whenProductIsActive() {
            // arrange
            BrandInfo brand = brandService.register("나이키", "스포츠 브랜드");
            ProductInfo product = productService.register(new ProductCreateCommand(brand.id(), "에어맥스", "신발", 150000, 10));

            // act
            ProductInfo result = productFacade.getActiveProduct(product.id());

            // assert
            assertThat(result.brand().name()).isEqualTo("나이키");
        }
    }

    @DisplayName("활성 상품 목록 조회 시, ")
    @Nested
    class GetActiveProducts {
        @DisplayName("각 상품 응답에 브랜드 이름이 포함된다.")
        @Test
        void returnsBrandName_forEachProduct() {
            // arrange
            BrandInfo nike = brandService.register("나이키", "스포츠 브랜드");
            BrandInfo adidas = brandService.register("아디다스", "스포츠 브랜드");
            productService.register(new ProductCreateCommand(nike.id(), "에어맥스", "신발", 150000, 10));
            productService.register(new ProductCreateCommand(adidas.id(), "슈퍼스타", "신발", 120000, 8));

            // act
            Page<ProductInfo> result = productFacade.getActiveProducts(null, ProductSort.LATEST, PageRequest.of(0, 20));

            // assert
            assertAll(
                    () -> assertThat(result.getContent()).extracting(p -> p.brand().name())
                            .containsExactlyInAnyOrder("나이키", "아디다스")
            );
        }

        @DisplayName("같은 브랜드의 여러 상품 조회 시 브랜드 이름이 모두 일치한다.")
        @Test
        void returnsSameBrandName_whenMultipleProductsOfSameBrand() {
            // arrange
            BrandInfo brand = brandService.register("나이키", "스포츠 브랜드");
            productService.register(new ProductCreateCommand(brand.id(), "에어맥스", "신발", 150000, 10));
            productService.register(new ProductCreateCommand(brand.id(), "조던", "농구화", 200000, 5));

            // act
            Page<ProductInfo> result = productFacade.getActiveProducts(null, ProductSort.LATEST, PageRequest.of(0, 20));

            // assert
            assertThat(result.getContent()).extracting(p -> p.brand().name())
                    .containsOnly("나이키");
        }
    }

    @DisplayName("상품 삭제 시, ")
    @Nested
    class Delete {
        @DisplayName("해당 상품의 좋아요가 모두 삭제된다.")
        @Test
        void deletesAllLikes_whenProductIsDeleted() {
            // arrange
            BrandInfo brand = brandService.register("나이키", "스포츠 브랜드");
            ProductInfo product = productService.register(new ProductCreateCommand(brand.id(), "에어맥스", "신발", 150000, 10));
            ProductInfo anotherProduct = productService.register(new ProductCreateCommand(brand.id(), "조던", "농구화", 200000, 5));

            long userId = 1L;
            likeService.register(userId, product.id());
            likeService.register(userId, anotherProduct.id());

            // act
            productFacade.delete(product.id());

            // assert
            assertThat(likeRepository.findByUserId(userId))
                    .extracting("productId")
                    .doesNotContain(product.id())
                    .contains(anotherProduct.id());
        }

        @DisplayName("상품이 soft delete 된다.")
        @Test
        void softDeletesProduct() {
            // arrange
            BrandInfo brand = brandService.register("나이키", "스포츠 브랜드");
            ProductInfo product = productService.register(new ProductCreateCommand(brand.id(), "에어맥스", "신발", 150000, 10));

            // act
            productFacade.delete(product.id());

            // assert
            assertThat(productRepository.findById(product.id()).orElseThrow().getDeletedAt()).isNotNull();
        }
    }

    @DisplayName("상품 등록 시, ")
    @Nested
    class Register {
        @DisplayName("존재하지 않는 브랜드로 등록하면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFoundException_whenBrandNotExists() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                productFacade.register(new ProductCreateCommand(99999L, "에어맥스", "신발", 150000, 10));
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("삭제된 브랜드로 등록하면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFoundException_whenBrandIsDeleted() {
            // arrange
            BrandInfo brand = brandService.register("나이키", "스포츠 브랜드");
            brandService.delete(brand.id());

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                productFacade.register(new ProductCreateCommand(brand.id(), "에어맥스", "신발", 150000, 10));
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
