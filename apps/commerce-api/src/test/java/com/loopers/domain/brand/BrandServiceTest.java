package com.loopers.domain.brand;

import com.loopers.domain.brand.model.Brand;
import com.loopers.domain.brand.model.BrandCommand;
import com.loopers.domain.brand.repository.BrandRepository;
import com.loopers.domain.brand.service.BrandService;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BrandServiceTest {

    @InjectMocks
    private BrandService brandService;

    @Mock
    private BrandRepository brandRepository;

    @DisplayName("브랜드 생성")
    @Nested
    class CreateBrand {

        @DisplayName("정상적으로 브랜드를 생성한다")
        @Test
        void createsBrand_andReturnsSaved() {
            // arrange
            BrandCommand.Create command = new BrandCommand.Create("나이키", "스포츠 브랜드");
            Brand saved = Brand.reconstruct(1L, "나이키", "스포츠 브랜드");
            when(brandRepository.save(any(Brand.class))).thenReturn(saved);

            // act
            Brand result = brandService.createBrand(command);

            // assert
            verify(brandRepository).save(any(Brand.class));
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getName().value()).isEqualTo("나이키");
        }
    }

    @DisplayName("브랜드 조회")
    @Nested
    class FindBrand {

        @DisplayName("존재하지 않는 브랜드 ID이면 예외가 발생한다")
        @Test
        void throwsException_whenBrandNotFound() {
            // arrange
            when(brandRepository.findById(999L)).thenReturn(Optional.empty());

            // act & assert
            assertThatThrownBy(() -> brandService.findBrand(999L))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> {
                        CoreException ce = (CoreException) e;
                        assertThat(ce.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
                    });
        }

        @DisplayName("정상적으로 브랜드를 조회한다")
        @Test
        void returnsBrand_whenFound() {
            // arrange
            Brand brand = Brand.reconstruct(1L, "나이키", "스포츠 브랜드");
            when(brandRepository.findById(1L)).thenReturn(Optional.of(brand));

            // act
            Brand result = brandService.findBrand(1L);

            // assert
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getName().value()).isEqualTo("나이키");
        }
    }

    @DisplayName("브랜드 수정")
    @Nested
    class UpdateBrand {

        @DisplayName("존재하지 않는 브랜드 ID이면 예외가 발생한다")
        @Test
        void throwsException_whenBrandNotFound() {
            // arrange
            BrandCommand.Update command = new BrandCommand.Update("아디다스", "글로벌 스포츠");
            when(brandRepository.findById(999L)).thenReturn(Optional.empty());

            // act & assert
            assertThatThrownBy(() -> brandService.updateBrand(999L, command))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> {
                        CoreException ce = (CoreException) e;
                        assertThat(ce.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
                    });
        }

        @DisplayName("정상적으로 브랜드를 수정한다")
        @Test
        void updatesBrand_andCallsUpdate() {
            // arrange
            Brand brand = Brand.reconstruct(1L, "나이키", "스포츠 브랜드");
            BrandCommand.Update command = new BrandCommand.Update("아디다스", "글로벌 스포츠");
            when(brandRepository.findById(1L)).thenReturn(Optional.of(brand));

            // act
            Brand result = brandService.updateBrand(1L, command);

            // assert
            verify(brandRepository).update(brand);
            assertThat(result.getName().value()).isEqualTo("아디다스");
            assertThat(result.getDescription()).isEqualTo("글로벌 스포츠");
        }
    }

    @DisplayName("브랜드 삭제")
    @Nested
    class DeleteBrand {

        @DisplayName("존재하지 않는 브랜드 ID이면 예외가 발생한다")
        @Test
        void throwsException_whenBrandNotFound() {
            // arrange
            when(brandRepository.findById(999L)).thenReturn(Optional.empty());

            // act & assert
            assertThatThrownBy(() -> brandService.deleteBrand(999L))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> {
                        CoreException ce = (CoreException) e;
                        assertThat(ce.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
                    });
        }

        @DisplayName("정상적으로 브랜드를 삭제한다")
        @Test
        void deletesBrand_andCallsDeleteById() {
            // arrange
            Brand brand = Brand.reconstruct(1L, "나이키", "스포츠 브랜드");
            when(brandRepository.findById(1L)).thenReturn(Optional.of(brand));

            // act
            brandService.deleteBrand(1L);

            // assert
            verify(brandRepository).deleteById(1L);
        }
    }

    @DisplayName("브랜드 목록 조회")
    @Nested
    class FindBrandList {

        @DisplayName("브랜드가 없으면 빈 페이지를 반환한다")
        @Test
        void returnsEmptyPage_whenNoBrands() {
            // arrange
            Pageable pageable = PageRequest.of(0, 10);
            when(brandRepository.findAll(pageable)).thenReturn(Page.empty());

            // act
            Page<Brand> result = brandService.findBrandList(pageable);

            // assert
            assertThat(result.getContent()).isEmpty();
        }

        @DisplayName("정상적으로 브랜드 목록을 반환한다")
        @Test
        void returnsBrandPage_whenBrandsExist() {
            // arrange
            Pageable pageable = PageRequest.of(0, 10);
            Brand brand1 = Brand.reconstruct(1L, "나이키", "스포츠 브랜드");
            Brand brand2 = Brand.reconstruct(2L, "아디다스", "글로벌 스포츠");
            Page<Brand> page = new PageImpl<>(List.of(brand1, brand2), pageable, 2);
            when(brandRepository.findAll(pageable)).thenReturn(page);

            // act
            Page<Brand> result = brandService.findBrandList(pageable);

            // assert
            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getContent().get(0).getName().value()).isEqualTo("나이키");
            assertThat(result.getContent().get(1).getName().value()).isEqualTo("아디다스");
        }
    }
}
