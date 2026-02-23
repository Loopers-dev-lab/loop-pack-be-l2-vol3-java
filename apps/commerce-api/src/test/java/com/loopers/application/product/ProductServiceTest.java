package com.loopers.application.product;

import com.loopers.application.brand.BrandInfo;
import com.loopers.application.brand.BrandService;
import com.loopers.domain.brand.InMemoryBrandRepository;
import com.loopers.domain.product.InMemoryProductRepository;
import com.loopers.domain.product.Product;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ProductServiceTest {
    private InMemoryProductRepository productRepository;
    private BrandService brandService;
    private ProductService productService;

    @BeforeEach
    void setUp() {
        productRepository = new InMemoryProductRepository();
        brandService = new BrandService(new InMemoryBrandRepository());
        productService = new ProductService(productRepository, brandService);
    }

    @DisplayName("상품 등록 시, ")
    @Nested
    class Register {
        @DisplayName("정상적으로 등록된다.")
        @Test
        void registersProduct() {
            // arrange
            BrandInfo brand = brandService.register("나이키", "스포츠 브랜드");

            // act
            ProductInfo product = productService.register(new ProductCreateCommand(brand.id(), "에어맥스", "신발", 150000, 10));

            // assert
            assertAll(
                () -> assertThat(product.brandId()).isEqualTo(brand.id()),
                () -> assertThat(product.name()).isEqualTo("에어맥스"),
                () -> assertThat(product.price()).isEqualTo(150000),
                () -> assertThat(product.stockQuantity()).isEqualTo(10)
            );
        }

        @DisplayName("존재하지 않는 브랜드로 등록하면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFoundException_whenBrandNotExists() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                productService.register(new ProductCreateCommand(99999L, "에어맥스", "신발", 150000, 10));
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
                productService.register(new ProductCreateCommand(brand.id(), "에어맥스", "신발", 150000, 10));
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("상품 조회 시, ")
    @Nested
    class GetProduct {
        @DisplayName("존재하는 상품을 조회하면 정상 반환된다.")
        @Test
        void returnsProduct_whenExists() {
            // arrange
            BrandInfo brand = brandService.register("나이키", "스포츠 브랜드");
            ProductInfo saved = productService.register(new ProductCreateCommand(brand.id(), "에어맥스", "신발", 150000, 10));

            // act
            ProductInfo found = productService.getProduct(saved.id());

            // assert
            assertThat(found.name()).isEqualTo("에어맥스");
        }

        @DisplayName("존재하지 않는 상품을 조회하면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFoundException_whenNotExists() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                productService.getProduct(99999L);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("삭제된 상품을 조회하면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFoundException_whenDeleted() {
            // arrange
            BrandInfo brand = brandService.register("나이키", "스포츠 브랜드");
            ProductInfo saved = productService.register(new ProductCreateCommand(brand.id(), "에어맥스", "신발", 150000, 10));
            productService.delete(saved.id());

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                productService.getProduct(saved.id());
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("노출 여부 변경 시, ")
    @Nested
    class ChangeVisibility {
        @DisplayName("정상적으로 변경된다.")
        @Test
        void changesVisibility() {
            // arrange
            BrandInfo brand = brandService.register("나이키", "스포츠 브랜드");
            ProductInfo saved = productService.register(new ProductCreateCommand(brand.id(), "에어맥스", "신발", 150000, 10));

            // act
            ProductInfo updated = productService.changeVisibility(saved.id(), Product.Visibility.HIDDEN);

            // assert
            assertThat(updated.visibility()).isEqualTo(Product.Visibility.HIDDEN);
        }

        @DisplayName("존재하지 않는 상품의 노출 여부를 변경하면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFoundException_whenNotExists() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                productService.changeVisibility(Long.MAX_VALUE, Product.Visibility.HIDDEN);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("상품 수정 시, ")
    @Nested
    class Update {
        @DisplayName("정상적으로 수정된다.")
        @Test
        void updatesProduct() {
            // arrange
            BrandInfo brand = brandService.register("나이키", "스포츠 브랜드");
            ProductInfo saved = productService.register(new ProductCreateCommand(brand.id(), "에어맥스", "신발", 150000, 10));

            // act
            ProductInfo updated = productService.update(saved.id(), new ProductUpdateCommand("조던", "농구화", 200000, 5, null));

            // assert
            assertAll(
                () -> assertThat(updated.name()).isEqualTo("조던"),
                () -> assertThat(updated.description()).isEqualTo("농구화"),
                () -> assertThat(updated.price()).isEqualTo(200000),
                () -> assertThat(updated.stockQuantity()).isEqualTo(5)
            );
        }

        @DisplayName("존재하지 않는 상품을 수정하면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFoundException_whenNotExists() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                productService.update(Long.MAX_VALUE, new ProductUpdateCommand("조던", "농구화", 200000, 5, null));
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("상품 삭제 시, ")
    @Nested
    class Delete {
        @DisplayName("정상적으로 soft delete 된다.")
        @Test
        void deletesProduct() {
            // arrange
            BrandInfo brand = brandService.register("나이키", "스포츠 브랜드");
            ProductInfo saved = productService.register(new ProductCreateCommand(brand.id(), "에어맥스", "신발", 150000, 10));

            // act
            productService.delete(saved.id());

            // assert
            assertThat(productRepository.findById(saved.id()).orElseThrow().getDeletedAt()).isNotNull();
        }

        @DisplayName("존재하지 않는 상품을 삭제하면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFoundException_whenNotExists() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                productService.delete(Long.MAX_VALUE);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
