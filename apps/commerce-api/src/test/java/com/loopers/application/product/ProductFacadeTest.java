package com.loopers.application.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.application.brand.BrandService;
import com.loopers.domain.product.Money;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.stock.StockModel;
import com.loopers.application.stock.StockService;
import com.loopers.domain.stock.StockStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductFacadeTest {

    @Mock
    private ProductService productService;

    @Mock
    private BrandService brandService;

    @Mock
    private StockService stockService;

    private ProductFacade productFacade;

    @BeforeEach
    void setUp() {
        productFacade = new ProductFacade(productService, brandService, stockService);
    }

    @DisplayName("상품 등록")
    @Nested
    class Register {

        @DisplayName("브랜드 검증 후 상품 저장 및 재고 생성을 orchestrate 한다")
        @Test
        void orchestratesRegistration() {
            // given
            Long brandId = 1L;
            BrandModel brand = new BrandModel("나이키", "스포츠");
            ProductModel product = new ProductModel("에어맥스", "러닝화", new Money(129000), brandId);
            ReflectionTestUtils.setField(product, "id", 1L);
            StockModel stock = new StockModel(1L, 100);

            when(brandService.getBrand(brandId)).thenReturn(brand);
            when(productService.register("에어맥스", "러닝화", new Money(129000), brandId)).thenReturn(product);
            when(stockService.save(1L, 100)).thenReturn(stock);
            when(brandService.getBrandForAdmin(brandId)).thenReturn(brand);
            when(stockService.getByProductId(1L)).thenReturn(stock);

            // when
            ProductDetail result = productFacade.register("에어맥스", "러닝화", new Money(129000), brandId, 100);

            // then
            assertAll(
                () -> assertThat(result.name()).isEqualTo("에어맥스"),
                () -> verify(brandService).getBrand(brandId),
                () -> verify(productService).register("에어맥스", "러닝화", new Money(129000), brandId),
                () -> verify(stockService).save(1L, 100)
            );
        }

        @DisplayName("삭제된 브랜드에 등록하면 NOT_FOUND 예외를 던진다")
        @Test
        void throwsWhenBrandDeleted() {
            // given
            Long brandId = 1L;
            when(brandService.getBrand(brandId))
                .thenThrow(new CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다."));

            // when & then
            assertThrows(CoreException.class,
                () -> productFacade.register("에어맥스", "러닝화", new Money(129000), brandId, 100));
        }
    }

    @DisplayName("상품 상세 조회 (Customer)")
    @Nested
    class GetProduct {

        @DisplayName("상품 + 브랜드명 + 재고상태를 조합하여 반환한다")
        @Test
        void returnsProductDetail() {
            // given
            Long productId = 1L;
            Long brandId = 1L;
            ProductModel product = new ProductModel("에어맥스", "러닝화", new Money(129000), brandId);
            BrandModel brand = new BrandModel("나이키", "스포츠");
            StockModel stock = new StockModel(productId, 100);

            when(productService.getById(productId)).thenReturn(product);
            when(brandService.getBrandForAdmin(brandId)).thenReturn(brand);
            when(stockService.getByProductId(productId)).thenReturn(stock);

            // when
            ProductDetail result = productFacade.getProduct(productId);

            // then
            assertAll(
                () -> assertThat(result.name()).isEqualTo("에어맥스"),
                () -> assertThat(result.brandName()).isEqualTo("나이키"),
                () -> assertThat(result.stockStatus()).isEqualTo(StockStatus.IN_STOCK)
            );
        }
    }
}
