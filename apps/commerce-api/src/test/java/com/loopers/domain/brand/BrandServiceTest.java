package com.loopers.domain.brand;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BrandServiceTest {

    @Mock
    private BrandRepository brandRepository;

    private BrandService brandService;

    @BeforeEach
    void setUp() {
        brandService = new BrandService(brandRepository);
    }

    @DisplayName("브랜드를 조회할 때, ")
    @Nested
    class GetBrand {

        @DisplayName("존재하는 ID가 주어지면, 브랜드를 반환한다.")
        @Test
        void returnsBrand_whenIdExists() {
            // arrange
            Long brandId = 1L;
            BrandModel brand = new BrandModel("나이키", "스포츠 의류 및 신발 브랜드");
            given(brandRepository.findById(brandId)).willReturn(Optional.of(brand));

            // act
            BrandModel result = brandService.getBrand(brandId);

            // assert
            assertAll(
                () -> assertThat(result.getName()).isEqualTo("나이키"),
                () -> assertThat(result.getDescription()).isEqualTo("스포츠 의류 및 신발 브랜드")
            );
        }

        @DisplayName("존재하지 않는 ID가 주어지면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFoundException_whenIdDoesNotExist() {
            // arrange
            Long brandId = 999L;
            given(brandRepository.findById(brandId)).willReturn(Optional.empty());

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                brandService.getBrand(brandId);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("브랜드를 등록할 때, ")
    @Nested
    class Register {

        @DisplayName("정상적인 정보가 주어지면, 브랜드가 저장된다.")
        @Test
        void savesBrand_whenValidInfoIsProvided() {
            // arrange
            String name = "나이키";
            String description = "스포츠 의류 및 신발 브랜드";
            given(brandRepository.findByName(name)).willReturn(Optional.empty());
            given(brandRepository.save(any(BrandModel.class))).willAnswer(invocation -> invocation.getArgument(0));

            // act
            BrandModel result = brandService.register(name, description);

            // assert
            assertAll(
                () -> assertThat(result.getName()).isEqualTo(name),
                () -> assertThat(result.getDescription()).isEqualTo(description)
            );
            verify(brandRepository).save(any(BrandModel.class));
        }

        @DisplayName("이미 존재하는 이름이 주어지면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflictException_whenNameAlreadyExists() {
            // arrange
            String name = "나이키";
            String description = "스포츠 의류 및 신발 브랜드";
            BrandModel existingBrand = new BrandModel(name, description);
            given(brandRepository.findByName(name)).willReturn(Optional.of(existingBrand));

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                brandService.register(name, description);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }

    @DisplayName("브랜드를 수정할 때, ")
    @Nested
    class Update {

        @DisplayName("정상적인 정보가 주어지면, 브랜드가 수정된다.")
        @Test
        void updatesBrand_whenValidInfoIsProvided() {
            // arrange
            Long brandId = 1L;
            BrandModel brand = new BrandModel("나이키", "스포츠 의류 및 신발 브랜드");
            given(brandRepository.findById(brandId)).willReturn(Optional.of(brand));

            String newName = "아디다스";
            String newDescription = "독일 스포츠 브랜드";

            // act
            BrandModel result = brandService.update(brandId, newName, newDescription);

            // assert
            assertAll(
                () -> assertThat(result.getName()).isEqualTo(newName),
                () -> assertThat(result.getDescription()).isEqualTo(newDescription)
            );
        }

        @DisplayName("존재하지 않는 ID가 주어지면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFoundException_whenIdDoesNotExist() {
            // arrange
            Long brandId = 999L;
            given(brandRepository.findById(brandId)).willReturn(Optional.empty());

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                brandService.update(brandId, "아디다스", "독일 스포츠 브랜드");
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("브랜드를 삭제할 때, ")
    @Nested
    class Delete {

        @DisplayName("존재하는 ID가 주어지면, 브랜드가 소프트 삭제된다.")
        @Test
        void deletesBrand_whenIdExists() {
            // arrange
            Long brandId = 1L;
            BrandModel brand = new BrandModel("나이키", "스포츠 의류 및 신발 브랜드");
            given(brandRepository.findById(brandId)).willReturn(Optional.of(brand));

            // act
            brandService.delete(brandId);

            // assert
            assertThat(brand.getDeletedAt()).isNotNull();
        }

        @DisplayName("존재하지 않는 ID가 주어지면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFoundException_whenIdDoesNotExist() {
            // arrange
            Long brandId = 999L;
            given(brandRepository.findById(brandId)).willReturn(Optional.empty());

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                brandService.delete(brandId);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("브랜드 목록을 조회할 때, ")
    @Nested
    class GetAll {

        @DisplayName("페이징 정보가 주어지면, 브랜드 목록을 반환한다.")
        @Test
        void returnsBrandList_whenPageableIsProvided() {
            // arrange
            Pageable pageable = PageRequest.of(0, 20);
            List<BrandModel> brands = List.of(
                new BrandModel("나이키", "스포츠 의류 및 신발 브랜드"),
                new BrandModel("아디다스", "독일 스포츠 브랜드")
            );
            Page<BrandModel> brandPage = new PageImpl<>(brands, pageable, brands.size());
            given(brandRepository.findAll(pageable)).willReturn(brandPage);

            // act
            Page<BrandModel> result = brandService.getAll(pageable);

            // assert
            assertAll(
                () -> assertThat(result.getContent()).hasSize(2),
                () -> assertThat(result.getContent().get(0).getName()).isEqualTo("나이키"),
                () -> assertThat(result.getContent().get(1).getName()).isEqualTo("아디다스")
            );
        }
    }
}
