package com.loopers.domain.brand;

import com.loopers.support.error.BrandErrorType;
import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class BrandServiceTest {

    private BrandRepository brandRepository;
    private BrandService brandService;

    @BeforeEach
    void setUp() {
        brandRepository = Mockito.mock(BrandRepository.class);
        brandService = new BrandService(brandRepository);
    }

    @DisplayName("브랜드를 생성할 때,")
    @Nested
    class 생성 {

        @Test
        void 유효한_정보면_생성된_브랜드가_반환된다() {
            // arrange
            when(brandRepository.save(any(Brand.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // act
            Brand brand = brandService.create("나이키", "스포츠 브랜드");

            // assert
            assertThat(brand)
                    .extracting(Brand::getName, Brand::getDescription, Brand::getStatus)
                    .containsExactly("나이키", "스포츠 브랜드", BrandStatus.ACTIVE);
        }

        @Test
        void 생성_시_save가_호출된다() {
            // arrange
            when(brandRepository.save(any(Brand.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // act
            brandService.create("나이키", "스포츠 브랜드");

            // assert
            verify(brandRepository).save(any(Brand.class));
        }
    }

    @DisplayName("브랜드를 단건 조회할 때,")
    @Nested
    class 단건조회 {

        @Test
        void 존재하지_않는_ID면_예외가_발생한다() {
            // arrange
            when(brandRepository.findById(1L)).thenReturn(Optional.empty());

            // act & assert
            assertThatThrownBy(() -> brandService.getById(1L))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(BrandErrorType.BRAND_NOT_FOUND);
        }

        @Test
        void 삭제된_브랜드면_예외가_발생한다() {
            // arrange
            Brand brand = Brand.create("나이키", "스포츠 브랜드");
            brand.delete();
            when(brandRepository.findById(1L)).thenReturn(Optional.of(brand));

            // act & assert
            assertThatThrownBy(() -> brandService.getById(1L))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(BrandErrorType.ALREADY_DELETED);
        }

        @Test
        void 존재하는_브랜드면_반환한다() {
            // arrange
            Brand brand = Brand.create("나이키", "스포츠 브랜드");
            when(brandRepository.findById(1L)).thenReturn(Optional.of(brand));

            // act
            Brand result = brandService.getById(1L);

            // assert
            assertThat(result.getName()).isEqualTo("나이키");
        }
    }

    @DisplayName("활성 브랜드를 조회할 때,")
    @Nested
    class 활성브랜드조회 {

        @Test
        void INACTIVE_상태면_예외가_발생한다() {
            // arrange
            Brand brand = Brand.create("나이키", "스포츠 브랜드");
            brand.changeStatus(BrandStatus.INACTIVE);
            when(brandRepository.findById(1L)).thenReturn(Optional.of(brand));

            // act & assert
            assertThatThrownBy(() -> brandService.getActiveBrand(1L))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(BrandErrorType.INACTIVE_BRAND);
        }

        @Test
        void ACTIVE_상태면_반환한다() {
            // arrange
            Brand brand = Brand.create("나이키", "스포츠 브랜드");
            when(brandRepository.findById(1L)).thenReturn(Optional.of(brand));

            // act
            Brand result = brandService.getActiveBrand(1L);

            // assert
            assertThat(result.getName()).isEqualTo("나이키");
        }
    }

    @DisplayName("브랜드를 수정할 때,")
    @Nested
    class 수정 {

        @Test
        void 존재하지_않는_ID면_예외가_발생한다() {
            // arrange
            when(brandRepository.findById(1L)).thenReturn(Optional.empty());

            // act & assert
            assertThatThrownBy(() -> brandService.update(1L, "아디다스", "독일 브랜드"))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(BrandErrorType.BRAND_NOT_FOUND);
        }

        @Test
        void 유효한_정보면_수정된_브랜드가_반환된다() {
            // arrange
            Brand brand = Brand.create("나이키", "스포츠 브랜드");
            when(brandRepository.findById(1L)).thenReturn(Optional.of(brand));

            // act
            Brand result = brandService.update(1L, "아디다스", "독일 브랜드");

            // assert
            assertThat(result)
                    .extracting(Brand::getName, Brand::getDescription)
                    .containsExactly("아디다스", "독일 브랜드");
        }
    }

    @DisplayName("브랜드를 삭제할 때,")
    @Nested
    class 삭제 {

        @Test
        void 존재하지_않는_ID면_예외가_발생한다() {
            // arrange
            when(brandRepository.findById(1L)).thenReturn(Optional.empty());

            // act & assert
            assertThatThrownBy(() -> brandService.delete(1L))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(BrandErrorType.BRAND_NOT_FOUND);
        }

        @Test
        void 유효한_브랜드면_delete가_호출된다() {
            // arrange
            Brand brand = Brand.create("나이키", "스포츠 브랜드");
            when(brandRepository.findById(1L)).thenReturn(Optional.of(brand));

            // act
            brandService.delete(1L);

            // assert
            assertThat(brand.getDeletedAt()).isNotNull();
        }
    }
}
