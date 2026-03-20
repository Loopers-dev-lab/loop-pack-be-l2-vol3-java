package com.loopers.domain.brand;

import com.loopers.domain.product.ProductService;
import com.loopers.support.enums.DisplayStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BrandService 도메인 서비스 테스트")
class BrandServiceTest {

    @Mock
    BrandRepository brandRepository;

    @Mock
    ProductService productService;

    @InjectMocks
    BrandService brandService;

    @Nested
    @DisplayName("브랜드 생성")
    class CreateBrandTests {

        @Test
        @DisplayName("유효한 입력으로 브랜드를 생성하면 BrandModel을 반환한다")
        void createBrand_WithValidInput_ShouldReturnBrand() {
            when(brandRepository.save(any(BrandModel.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            BrandModel result = brandService.createBrand("테스트브랜드", "설명", "서울");

            assertThat(result.getBrandName()).isEqualTo("테스트브랜드");
            assertThat(result.getDescription()).isEqualTo("설명");
            assertThat(result.getAddress()).isEqualTo("서울");
            assertThat(result.getDisplayStatus()).isEqualTo(DisplayStatus.ACTIVE);
            verify(brandRepository).save(any(BrandModel.class));
        }
    }

    @Nested
    @DisplayName("브랜드 조회")
    class FindTests {

        @Test
        @DisplayName("존재하는 ID로 조회하면 BrandModel을 반환한다")
        void findById_Existing_ShouldReturn() {
            BrandModel brand = BrandModel.create("브랜드", "설명", "서울");
            when(brandRepository.findById(1L)).thenReturn(Optional.of(brand));

            BrandModel result = brandService.findById(1L);

            assertThat(result.getBrandName()).isEqualTo("브랜드");
        }

        @Test
        @DisplayName("존재하지 않는 ID 조회 시 BRAND_NOT_FOUND 예외가 발생한다")
        void findById_NotFound_ShouldThrowBRAND_NOT_FOUND() {
            when(brandRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> brandService.findById(999L))
                    .isInstanceOf(CoreException.class)
                    .satisfies(ex -> assertThat(((CoreException) ex).getErrorType())
                            .isEqualTo(ErrorType.BRAND_NOT_FOUND));
        }

        @Test
        @DisplayName("HIDDEN 브랜드를 고객 조회 시 BRAND_NOT_FOUND 예외가 발생한다")
        void findVisibleById_WhenHidden_ShouldThrowBRAND_NOT_FOUND() {
            BrandModel brand = BrandModel.create("브랜드", "설명", "서울");
            brand.hide();
            when(brandRepository.findById(1L)).thenReturn(Optional.of(brand));

            assertThatThrownBy(() -> brandService.findVisibleById(1L))
                    .isInstanceOf(CoreException.class)
                    .satisfies(ex -> assertThat(((CoreException) ex).getErrorType())
                            .isEqualTo(ErrorType.BRAND_NOT_FOUND));
        }

        @Test
        @DisplayName("삭제된 브랜드를 고객 조회 시 BRAND_NOT_FOUND 예외가 발생한다")
        void findVisibleById_WhenDeleted_ShouldThrowBRAND_NOT_FOUND() {
            BrandModel brand = BrandModel.create("브랜드", "설명", "서울");
            brand.softDelete();
            when(brandRepository.findById(1L)).thenReturn(Optional.of(brand));

            assertThatThrownBy(() -> brandService.findVisibleById(1L))
                    .isInstanceOf(CoreException.class)
                    .satisfies(ex -> assertThat(((CoreException) ex).getErrorType())
                            .isEqualTo(ErrorType.BRAND_NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("브랜드 목록 조회")
    class FindAllTests {

        @Test
        @DisplayName("고객용 목록 조회 시 ACTIVE + 미삭제 브랜드만 반환한다")
        void findAllVisibleBrands_ShouldReturnOnlyActiveAndNotDeleted() {
            BrandModel brand1 = BrandModel.create("브랜드1", "설명1", "서울");
            BrandModel brand2 = BrandModel.create("브랜드2", "설명2", "부산");
            when(brandRepository.findAllByDelYnAndDisplayStatus("N", DisplayStatus.ACTIVE))
                    .thenReturn(List.of(brand1, brand2));

            List<BrandModel> result = brandService.findAllVisibleBrands(null);

            assertThat(result).hasSize(2);
            verify(brandRepository).findAllByDelYnAndDisplayStatus("N", DisplayStatus.ACTIVE);
        }

        @Test
        @DisplayName("keyword 검색이 올바르게 동작한다")
        void findAllVisibleBrands_WithKeyword_ShouldFilter() {
            BrandModel brand = BrandModel.create("테스트브랜드", "설명", "서울");
            when(brandRepository.findAllByKeyword("테스트")).thenReturn(List.of(brand));

            List<BrandModel> result = brandService.findAllVisibleBrands("테스트");

            assertThat(result).hasSize(1);
            verify(brandRepository).findAllByKeyword("테스트");
        }
    }

    @Nested
    @DisplayName("브랜드 수정")
    class UpdateTests {

        @Test
        @DisplayName("수정 후 변경된 BrandModel을 반환한다")
        void updateBrand_ShouldUpdateAndReturn() {
            BrandModel brand = BrandModel.create("기존이름", "기존설명", "기존주소");
            when(brandRepository.findById(1L)).thenReturn(Optional.of(brand));

            BrandModel result = brandService.updateBrand(1L, "새이름", "새설명", "새주소");

            assertThat(result.getBrandName()).isEqualTo("새이름");
            assertThat(result.getDescription()).isEqualTo("새설명");
            assertThat(result.getAddress()).isEqualTo("새주소");
        }
    }

    @Nested
    @DisplayName("브랜드 삭제")
    class DeleteTests {

        @Test
        @DisplayName("소프트 삭제가 정상적으로 수행된다")
        void deleteBrand_ShouldSoftDeleteBrand() {
            BrandModel brand = BrandModel.create("브랜드", "설명", "서울");
            when(brandRepository.findById(1L)).thenReturn(Optional.of(brand));

            brandService.deleteBrand(1L);

            assertThat(brand.isDeleted()).isTrue();
        }

        @Test
        @DisplayName("이미 삭제된 브랜드 재삭제 시 에러 없이 통과한다 (멱등성)")
        void deleteBrand_AlreadyDeleted_ShouldBeIdempotent() {
            BrandModel brand = BrandModel.create("브랜드", "설명", "서울");
            brand.softDelete();
            when(brandRepository.findById(1L)).thenReturn(Optional.of(brand));

            assertThatCode(() -> brandService.deleteBrand(1L))
                    .doesNotThrowAnyException();
        }
    }
}
