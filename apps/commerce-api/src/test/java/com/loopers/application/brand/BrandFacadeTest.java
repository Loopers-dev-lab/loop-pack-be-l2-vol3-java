package com.loopers.application.brand;

import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BrandFacadeTest {

    @Mock
    private BrandService brandService;

    @Mock
    private ProductService productService;

    private BrandFacade brandFacade;

    @BeforeEach
    void setUp() {
        brandFacade = new BrandFacade(brandService, productService);
    }

    @DisplayName("브랜드 삭제")
    @Nested
    class Delete {

        @DisplayName("브랜드 soft delete 후 소속 상품을 연쇄 soft delete 한다")
        @Test
        void deletesBrandAndCascadesProducts() {
            // given
            Long brandId = 1L;

            // when
            brandFacade.delete(brandId);

            // then
            verify(brandService).delete(brandId);
            verify(productService).deleteAllByBrandId(brandId);
        }
    }
}
