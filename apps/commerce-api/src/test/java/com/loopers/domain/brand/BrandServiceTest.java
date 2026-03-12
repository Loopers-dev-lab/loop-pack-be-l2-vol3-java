package com.loopers.domain.brand;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class BrandServiceTest {

    @Mock
    private BrandRepository brandRepository;

    private BrandService brandService;

    @BeforeEach
    void setUp() {
        brandService = new BrandService(brandRepository);
    }

    @DisplayName("브랜드 등록")
    @Nested
    class Register {

        @DisplayName("이름이 중복되지 않으면 정상 등록된다")
        @Test
        void registersSuccessfully() {
            // arrange
            String name = "나이키";
            String description = "스포츠 브랜드";
            BrandModel brand = new BrandModel(name, description);
            given(brandRepository.findByName(name)).willReturn(Optional.empty());
            given(brandRepository.save(any(BrandModel.class))).willReturn(brand);
            // act
            BrandModel result = brandService.register(name, description);
            // assert
            assertAll(
                () -> assertThat(result.getName()).isEqualTo(name),
                () -> assertThat(result.getDescription()).isEqualTo(description)
            );
            then(brandRepository).should().save(any(BrandModel.class));
        }

        @DisplayName("이름이 중복되면 CONFLICT 예외가 발생한다")
        @Test
        void throwsOnDuplicateName() {
            // arrange
            String name = "나이키";
            given(brandRepository.findByName(name)).willReturn(Optional.of(new BrandModel(name, "기존")));
            // act
            CoreException exception = assertThrows(CoreException.class, () -> {
                brandService.register(name, "신규");
            });
            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.CONFLICT);
            then(brandRepository).should(never()).save(any());
        }
    }

    @DisplayName("브랜드 조회")
    @Nested
    class GetById {

        @DisplayName("존재하는 ID면 브랜드를 반환한다")
        @Test
        void returnsForExistingId() {
            // arrange
            Long id = 1L;
            BrandModel brand = new BrandModel("나이키", "스포츠");
            given(brandRepository.findById(id)).willReturn(Optional.of(brand));
            // act
            BrandModel result = brandService.getById(id);
            // assert
            assertThat(result.getName()).isEqualTo("나이키");
        }

        @DisplayName("존재하지 않는 ID면 NOT_FOUND 예외가 발생한다")
        @Test
        void throwsOnNonExistentId() {
            // arrange
            Long id = 999L;
            given(brandRepository.findById(id)).willReturn(Optional.empty());
            // act
            CoreException exception = assertThrows(CoreException.class, () -> {
                brandService.getById(id);
            });
            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("브랜드 수정")
    @Nested
    class Update {

        @DisplayName("자기 자신과 같은 이름이면 정상 수정된다")
        @Test
        void updatesWhenSameNameAsSelf() {
            // arrange
            Long id = 1L;
            BrandModel brand = new BrandModel("나이키", "스포츠");
            ReflectionTestUtils.setField(brand, "id", id);
            given(brandRepository.findById(id)).willReturn(Optional.of(brand));
            given(brandRepository.findByName("나이키")).willReturn(Optional.of(brand));
            // act
            BrandModel result = brandService.update(id, "나이키", "새 설명");
            // assert
            assertThat(result.getDescription()).isEqualTo("새 설명");
        }

        @DisplayName("다른 브랜드와 이름이 중복되면 CONFLICT 예외가 발생한다")
        @Test
        void throwsOnDuplicateNameWithOther() {
            // arrange
            Long id = 1L;
            BrandModel brand = new BrandModel("나이키", "스포츠");
            ReflectionTestUtils.setField(brand, "id", id);
            BrandModel other = new BrandModel("아디다스", "독일");
            ReflectionTestUtils.setField(other, "id", 2L);
            given(brandRepository.findById(id)).willReturn(Optional.of(brand));
            given(brandRepository.findByName("아디다스")).willReturn(Optional.of(other));
            // act
            CoreException exception = assertThrows(CoreException.class, () -> {
                brandService.update(id, "아디다스", "설명");
            });
            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }

    @DisplayName("브랜드 삭제")
    @Nested
    class Delete {

        @DisplayName("존재하는 브랜드를 soft delete 한다")
        @Test
        void softDeletesSuccessfully() {
            // given
            Long brandId = 1L;
            BrandModel brand = new BrandModel(new BrandName("나이키"), "스포츠 브랜드");
            when(brandRepository.findById(brandId)).thenReturn(Optional.of(brand));

            // when
            brandService.delete(brandId);

            // then
            assertThat(brand.getDeletedAt()).isNotNull();
        }

        @DisplayName("미존재 브랜드면 NOT_FOUND 예외를 던진다")
        @Test
        void throwsWhenNotFound() {
            // given
            Long brandId = 999L;
            when(brandRepository.findById(brandId)).thenReturn(Optional.empty());

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> brandService.delete(brandId));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("브랜드 목록 조회")
    @Nested
    class GetAll {

        @DisplayName("페이징된 결과를 반환한다")
        @Test
        void returnsPagedResult() {
            // given
            Pageable pageable = PageRequest.of(0, 10);
            List<BrandModel> brands = List.of(
                new BrandModel(new BrandName("나이키"), "스포츠"),
                new BrandModel(new BrandName("아디다스"), "스포츠")
            );
            Page<BrandModel> page = new PageImpl<>(brands, pageable, brands.size());
            when(brandRepository.findAll(pageable)).thenReturn(page);

            // when
            Page<BrandModel> result = brandService.getAll(pageable);

            // then
            assertAll(
                () -> assertThat(result.getTotalElements()).isEqualTo(2),
                () -> assertThat(result.getContent()).hasSize(2)
            );
        }
    }
}
