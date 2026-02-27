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

import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class BrandServiceTest {

    private static final String VALID_BRAND_NAME = "아디다스";
    private static final Long VALID_BRAND_ID = 1L;
    private static final String NOT_UNIQUE_BRAND_NAME = "나이키";
    private static final Long NOT_EXISTED_BRAND_ID = 999L;


    @Mock
    private BrandRepository brandRepository;

    @InjectMocks
    private BrandService brandService;

    @DisplayName("브랜드 등록 시")
    @Nested
    class Register{

        @DisplayName("중복되지 않은 브랜드명으로 등록에 성공한다.")
        @Test
        void registersBrandSucceed_whenBrandNameIsUnique(){
            // arrange
            // stub: existsByName 호출 시 , false 반환
            when(brandRepository.existsByName(VALID_BRAND_NAME)).thenReturn(false);

            // stub: save 메서드 호출 시 저장된 객체 반환
            when(brandRepository.save(any(Brand.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // act
            Brand result = brandService.register(VALID_BRAND_NAME);

            // assert
            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo(VALID_BRAND_NAME);
        }

        @DisplayName("중복되는 브랜드명으로 등록에 실패한다.")
        @Test
        void registersBrandFail_whenBrandNameIsNotUnique(){
            // stub : existsByName 호출 시 true 반환
            when(brandRepository.existsByName("나이키")).thenReturn(true);

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                brandService.register(NOT_UNIQUE_BRAND_NAME);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);

            // 행위 검증
            verify(brandRepository, never()).save(any());
        }
    }

    @DisplayName("브랜드 상세 조회 시")
    @Nested
    class FindById{

        @DisplayName("존재하는 brandId로 조회하면, 상세 정보를 반환한다")
        @Test
        void findByIdSucceed_whenBrandIdIsExsits(){
            // arrange
            Brand brand = new Brand(VALID_BRAND_NAME);
            // stub
            when(brandRepository.findById(VALID_BRAND_ID)).thenReturn(Optional.of(brand));

            // act
            Brand result = brandService.findById(VALID_BRAND_ID);

            // assert
            assertThat(result.getName()).isEqualTo(VALID_BRAND_NAME);
        }

        @DisplayName("존재하지 않는 brandId로 조회하면, 에러를 반환한다.")
        @Test
        void findByIdFail_whenBrandIdIsNotExists(){
            // stub
            when(brandRepository.findById(NOT_EXISTED_BRAND_ID)).thenReturn(Optional.empty());

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                brandService.findById(999L);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("브랜드 정보 수정 시")
    @Nested
    class Update{

        @DisplayName("중복되지 않는 브랜드명으로 수정 시, 성공한다.")
        @Test
        void updateSucceed_whenBrandNameIsUnique(){
            // arrange
            Brand brand = new Brand(VALID_BRAND_NAME);
            String newName = "새로운 나이키";

            // stub
            when(brandRepository.findById(VALID_BRAND_ID)).thenReturn(Optional.of(brand));
            when(brandRepository.existsByNameAndIdNot(newName, VALID_BRAND_ID)).thenReturn(false);

            // act
            Brand result = brandService.update(VALID_BRAND_ID, newName);

            // assert
            assertThat(result.getName()).isEqualTo(newName);
        }

        @DisplayName("중복되는 브랜드명으로 수정 시, conflict 에러를 반환한다.")
        @Test
        void updateFailed_whenBrandNameIsNotUnique(){
            // arrange
            Brand brand = new Brand(VALID_BRAND_NAME);

            // stub
            when(brandRepository.findById(VALID_BRAND_ID)).thenReturn(Optional.of(brand));

            when(brandRepository.existsByNameAndIdNot(VALID_BRAND_NAME, VALID_BRAND_ID)).thenReturn(true);

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                brandService.update(VALID_BRAND_ID, VALID_BRAND_NAME);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }

    }

    @DisplayName("브랜드 삭제 시")
    @Nested
    class Delete{

        @DisplayName("존재하는 brandId로 요청하면, 정상진행")
        @Test
        void deleteSucceed_whenBrandIdIsValid(){
            // arrange
            Brand brand = new Brand(VALID_BRAND_NAME);

            // stub
            when(brandRepository.findById(VALID_BRAND_ID)).thenReturn(Optional.of(brand));

            // act
            brandService.delete(VALID_BRAND_ID);

            // assert
            assertThat(brand.getDeletedAt()).isNotNull();
        }

    }
}
