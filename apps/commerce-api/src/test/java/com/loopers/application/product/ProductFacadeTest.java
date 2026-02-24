package com.loopers.application.product;

import com.loopers.application.brand.BrandInfo;
import com.loopers.application.brand.BrandService;
import com.loopers.domain.brand.InMemoryBrandRepository;
import com.loopers.domain.product.InMemoryProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ProductFacadeTest {
    private BrandService brandService;
    private ProductFacade productFacade;

    @BeforeEach
    void setUp() {
        brandService = new BrandService(new InMemoryBrandRepository());
        ProductService productService = new ProductService(new InMemoryProductRepository());
        productFacade = new ProductFacade(brandService, productService);
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
