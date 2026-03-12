package com.loopers.application.brand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.loopers.application.brand.dto.BrandCriteria;
import com.loopers.application.brand.dto.BrandResult;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("BrandFacade 단위 테스트")
@ExtendWith(MockitoExtension.class)
class BrandFacadeTest {

    @Mock
    private BrandService brandService;

    @Mock
    private ProductService productService;

    @InjectMocks
    private BrandFacade brandFacade;

    @DisplayName("브랜드 등록")
    @Nested
    class Register {

        @Test
        @DisplayName("Command의 name을 BrandService.register에 전달한다")
        void register_호출_검증() {
            // arrange
            BrandCriteria.Register criteria = new BrandCriteria.Register("나이키");

            // act
            brandFacade.registerBrand(criteria);

            // assert
            verify(brandService).register("나이키");
        }
    }

    @DisplayName("브랜드 조회")
    @Nested
    class GetById {

        @Test
        @DisplayName("BrandService.getById 결과를 BrandResult로 변환하여 반환한다")
        void getById_변환_검증() {
            // arrange
            BrandModel brandModel = BrandModel.create("나이키");
            when(brandService.getById(1L)).thenReturn(brandModel);

            // act
            BrandResult result = brandFacade.getBrand(1L);

            // assert
            assertThat(result.name()).isEqualTo("나이키");
            verify(brandService).getById(1L);
        }
    }

    @DisplayName("브랜드 수정")
    @Nested
    class Update {

        @Test
        @DisplayName("id와 Command의 name을 BrandService.update에 전달한다")
        void update_호출_검증() {
            // arrange
            BrandCriteria.Update criteria = new BrandCriteria.Update("아디다스");

            // act
            brandFacade.updateBrand(1L, criteria);

            // assert
            verify(brandService).update(1L, "아디다스");
        }
    }

    @DisplayName("브랜드 삭제")
    @Nested
    class Delete {

        @Test
        @DisplayName("id를 BrandService.delete에 전달하고 해당 브랜드의 상품을 일괄 삭제한다")
        void delete_호출_검증() {
            // arrange & act
            brandFacade.deleteBrand(1L);

            // assert
            verify(brandService).delete(1L);
            verify(productService).deleteAllByBrandId(1L);
        }
    }
}
