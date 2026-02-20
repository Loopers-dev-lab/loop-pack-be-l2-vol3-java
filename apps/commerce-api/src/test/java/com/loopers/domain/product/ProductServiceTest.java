package com.loopers.domain.product;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.brand.InMemoryBrandRepository;
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
            Brand brand = brandService.register("나이키", "스포츠 브랜드");

            // act
            Product product = productService.register(brand.getId(), "에어맥스", "신발", 150000, 10);

            // assert
            assertAll(
                () -> assertThat(product.getBrandId()).isEqualTo(brand.getId()),
                () -> assertThat(product.getName()).isEqualTo("에어맥스"),
                () -> assertThat(product.getPrice()).isEqualTo(150000),
                () -> assertThat(product.getStockQuantity()).isEqualTo(10)
            );
        }

        @DisplayName("존재하지 않는 브랜드로 등록하면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFoundException_whenBrandNotExists() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                productService.register(99999L, "에어맥스", "신발", 150000, 10);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("삭제된 브랜드로 등록하면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFoundException_whenBrandIsDeleted() {
            // arrange
            Brand brand = brandService.register("나이키", "스포츠 브랜드");
            brandService.delete(brand.getId());

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                productService.register(brand.getId(), "에어맥스", "신발", 150000, 10);
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
            Brand brand = brandService.register("나이키", "스포츠 브랜드");
            Product saved = productService.register(brand.getId(), "에어맥스", "신발", 150000, 10);

            // act
            Product found = productService.getProduct(saved.getId());

            // assert
            assertThat(found.getName()).isEqualTo("에어맥스");
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
            Brand brand = brandService.register("나이키", "스포츠 브랜드");
            Product saved = productService.register(brand.getId(), "에어맥스", "신발", 150000, 10);
            productService.delete(saved.getId());

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                productService.getProduct(saved.getId());
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("상품 목록 조회 시, ")
    @Nested
    class GetProducts {
        @DisplayName("삭제되지 않은 상품만 반환된다.")
        @Test
        void returnsOnlyActiveProducts() {
            // arrange
            Brand brand = brandService.register("나이키", "스포츠 브랜드");
            productService.register(brand.getId(), "에어맥스", "신발", 150000, 10);
            productService.register(brand.getId(), "조던", "농구화", 200000, 5);
            Product toDelete = productService.register(brand.getId(), "삭제상품", "삭제될 상품", 100000, 1);
            productService.delete(toDelete.getId());

            // act
            Page<Product> result = productService.getProducts(null, ProductSort.LATEST, PageRequest.of(0, 20));

            // assert
            assertThat(result.getContent()).noneMatch(p -> p.getName().equals("삭제상품"));
        }

        @DisplayName("brandId로 필터링하면 해당 브랜드 상품만 반환된다.")
        @Test
        void returnsProductsFilteredByBrandId() {
            // arrange
            Brand nike = brandService.register("나이키", "스포츠 브랜드");
            Brand adidas = brandService.register("아디다스", "독일 스포츠 브랜드");
            productService.register(nike.getId(), "에어맥스", "신발", 150000, 10);
            productService.register(adidas.getId(), "슈퍼스타", "신발", 120000, 8);

            // act
            Page<Product> result = productService.getProducts(nike.getId(), ProductSort.LATEST, PageRequest.of(0, 20));

            // assert
            assertThat(result.getContent()).allMatch(p -> p.getBrandId().equals(nike.getId()));
        }

        @DisplayName("HIDDEN 상품은 반환되지 않는다.")
        @Test
        void excludesHiddenProducts() {
            // arrange
            Brand brand = brandService.register("나이키", "스포츠 브랜드");
            productService.register(brand.getId(), "에어맥스", "신발", 150000, 10);
            Product hidden = productService.register(brand.getId(), "숨김상품", "숨겨진 상품", 200000, 5);
            productService.changeVisibility(hidden.getId(), Product.Visibility.HIDDEN);

            // act
            Page<Product> result = productService.getProducts(null, ProductSort.LATEST, PageRequest.of(0, 20));

            // assert
            assertThat(result.getContent()).noneMatch(p -> p.getName().equals("숨김상품"));
        }
    }

    @DisplayName("노출 여부 변경 시, ")
    @Nested
    class ChangeVisibility {
        @DisplayName("정상적으로 변경된다.")
        @Test
        void changesVisibility() {
            // arrange
            Brand brand = brandService.register("나이키", "스포츠 브랜드");
            Product saved = productService.register(brand.getId(), "에어맥스", "신발", 150000, 10);

            // act
            productService.changeVisibility(saved.getId(), Product.Visibility.HIDDEN);

            // assert
            assertThat(saved.getVisibility()).isEqualTo(Product.Visibility.HIDDEN);
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
            Brand brand = brandService.register("나이키", "스포츠 브랜드");
            Product saved = productService.register(brand.getId(), "에어맥스", "신발", 150000, 10);

            // act
            Product updated = productService.update(saved.getId(), "조던", "농구화", 200000, 5);

            // assert
            assertAll(
                () -> assertThat(updated.getName()).isEqualTo("조던"),
                () -> assertThat(updated.getDescription()).isEqualTo("농구화"),
                () -> assertThat(updated.getPrice()).isEqualTo(200000),
                () -> assertThat(updated.getStockQuantity()).isEqualTo(5)
            );
        }

        @DisplayName("존재하지 않는 상품을 수정하면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFoundException_whenNotExists() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                productService.update(Long.MAX_VALUE, "조던", "농구화", 200000, 5);
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
            Brand brand = brandService.register("나이키", "스포츠 브랜드");
            Product saved = productService.register(brand.getId(), "에어맥스", "신발", 150000, 10);

            // act
            productService.delete(saved.getId());

            // assert
            Product deleted = productRepository.findById(saved.getId()).orElseThrow();
            assertThat(deleted.getDeletedAt()).isNotNull();
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
