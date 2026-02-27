package com.loopers.application.brand;

import com.loopers.application.brand.dto.FindBrandListResDto;
import com.loopers.application.brand.dto.FindBrandResDto;
import com.loopers.domain.brand.model.Brand;
import com.loopers.domain.brand.service.BrandService;
import com.loopers.domain.product.model.Product;
import com.loopers.domain.product.service.ProductService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BrandFacadeTest {

    @InjectMocks
    private BrandFacade brandFacade;

    @Mock
    private BrandService brandService;

    @Mock
    private ProductService productService;

    private static Brand createTestBrand() {
        return Brand.reconstruct(1L, "나이키", "스포츠 브랜드");
    }

    private static Product createTestProduct(Long id, Long brandId) {
        return Product.reconstruct(id, brandId, "상품A", 10000, 100, "DISPLAYING");
    }

    @DisplayName("브랜드 상세 조회")
    @Nested
    class FindBrand {

        @DisplayName("존재하지 않는 브랜드 조회 시 CoreException이 발생한다")
        @Test
        void throwsException_whenBrandNotFound() {
            // arrange
            when(brandService.findBrand(999L))
                    .thenThrow(new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 브랜드입니다."));

            // act & assert
            assertThatThrownBy(() -> brandFacade.findBrand(999L))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
        }

        @DisplayName("정상 조회 시 상품 목록이 포함된 결과를 반환한다")
        @Test
        void returnsBrandWithProducts() {
            // arrange
            Brand brand = createTestBrand();
            Product product1 = createTestProduct(1L, brand.getId());
            Product product2 = createTestProduct(2L, brand.getId());

            when(brandService.findBrand(1L)).thenReturn(brand);
            when(productService.findProductsByBrandId(1L)).thenReturn(List.of(product1, product2));

            // act
            FindBrandResDto result = brandFacade.findBrand(1L);

            // assert
            assertAll(
                () -> assertThat(result.id()).isEqualTo(1L),
                () -> assertThat(result.name()).isEqualTo("나이키"),
                () -> assertThat(result.description()).isEqualTo("스포츠 브랜드"),
                () -> assertThat(result.products()).hasSize(2)
            );
        }
    }

    @DisplayName("브랜드 목록 조회")
    @Nested
    class FindBrandList {

        @DisplayName("브랜드가 없으면 빈 페이지를 반환한다")
        @Test
        void returnsEmptyPage_whenNoBrandsExist() {
            // arrange
            Pageable pageable = PageRequest.of(0, 10);
            when(brandService.findBrandList(pageable)).thenReturn(Page.empty(pageable));

            // act
            Page<FindBrandListResDto> result = brandFacade.findBrandList(pageable);

            // assert
            assertThat(result.getContent()).isEmpty();
        }

        @DisplayName("정상 조회 시 브랜드 목록을 반환한다")
        @Test
        void returnsBrandList() {
            // arrange
            Pageable pageable = PageRequest.of(0, 10);
            Brand brand1 = Brand.reconstruct(1L, "나이키", "스포츠 브랜드");
            Brand brand2 = Brand.reconstruct(2L, "아디다스", "독일 스포츠 브랜드");
            Page<Brand> brandPage = new PageImpl<>(List.of(brand1, brand2), pageable, 2);

            when(brandService.findBrandList(pageable)).thenReturn(brandPage);

            // act
            Page<FindBrandListResDto> result = brandFacade.findBrandList(pageable);

            // assert
            assertAll(
                () -> assertThat(result.getTotalElements()).isEqualTo(2),
                () -> assertThat(result.getContent().get(0).name()).isEqualTo("나이키"),
                () -> assertThat(result.getContent().get(1).name()).isEqualTo("아디다스")
            );
        }
    }
}
