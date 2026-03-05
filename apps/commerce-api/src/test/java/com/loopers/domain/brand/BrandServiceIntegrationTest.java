package com.loopers.domain.brand;

import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
public class BrandServiceIntegrationTest {
    private static final String VALID_BRAND_NAME = "아디다스";
    private static final Long NOT_EXISTED_BRAND_ID = 999L;
    private static final String NEW_BRAND_NAME = "퓨마";

    @Autowired
    private BrandService brandService;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown(){
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("브랜드를 등록 시")
    @Nested
    class Register{
        @DisplayName("중복되지 않은 브랜드명으로 등록에 성공한다.")
        @Test
        void registersBrandSucceed_whenBrandNameIsUnique(){
            // act
            Brand result = brandService.register(VALID_BRAND_NAME);

            // assert
            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo(VALID_BRAND_NAME);

            // DB에 실제로 저장했는지 확인
            Brand saved = brandJpaRepository.findById(result.getId()).orElseThrow();
            assertThat(saved.getName()).isEqualTo(VALID_BRAND_NAME);
        }

        @DisplayName("중복되는 브랜드명으로 등록에 실패한다.")
        @Test
        void registersBrandFail_whenBrandNameIsNotUnique(){
            // arrange
            Brand existingBrand = new Brand(VALID_BRAND_NAME);
            brandJpaRepository.save(existingBrand);

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                brandService.register(VALID_BRAND_NAME);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }

    @DisplayName("브랜드 상세 조회 시")
    @Nested
    class FindById{
        @DisplayName("존재하는 brandId로 조회하면, 상세 정보를 반환한다")
        @Test
        void findByIdSucceed_whenBrandIdIsExists(){
            // arrange
            Brand existingBrand = new Brand(VALID_BRAND_NAME);
            existingBrand = brandJpaRepository.save(existingBrand);

            // act
            Brand brand = brandService.findById(existingBrand.getId());

            // assert
            assertThat(brand.getName()).isEqualTo(existingBrand.getName());
        }

        @DisplayName("존재하지 않는 brandId로 조회하면, 에러를 반환한다.")
        @Test
        void findByIdFail_whenBrandIdIsNotExists(){
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                brandService.findById(NOT_EXISTED_BRAND_ID);
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
            Brand existingBrand = brandJpaRepository.save(new Brand(VALID_BRAND_NAME));

            // act
            Brand result = brandService.update(existingBrand.getId(), NEW_BRAND_NAME);

            // assert
            assertThat(result.getName()).isEqualTo(NEW_BRAND_NAME);
            // dirty checking으로 인해 실제 DB에 반영됐는지도 확인
            Brand updated = brandJpaRepository.findById(existingBrand.getId()).orElseThrow();
            assertThat(updated.getName()).isEqualTo(NEW_BRAND_NAME);
        }

        @DisplayName("중복되는 브랜드명으로 수정 시, conflict 에러를 반환한다.")
        @Test
        void updateFailed_whenBrandNameIsNotUnique(){
            // arrange
            Brand existingBrand1 = brandJpaRepository.save(new Brand(VALID_BRAND_NAME));
            Brand existingBrand2 = brandJpaRepository.save(new Brand(NEW_BRAND_NAME));

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                brandService.update(existingBrand1.getId(), existingBrand2.getName());
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }

        @DisplayName("존재하지 않는 brandId로 수정 시, 404에러를 반환한다.")
        @Test
        void updateFailed_whenBrandIdIsNotExists(){
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                brandService.update(NOT_EXISTED_BRAND_ID, VALID_BRAND_NAME);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("브랜드 삭제 시")
    @Nested
    class Delete{
        @DisplayName("존재하는 brandId로 요청하면, 정상진행")
        @Test
        void deleteSucceed_whenBrandIdIsValid(){
            // arrange
            Brand brand = brandJpaRepository.save(new Brand(VALID_BRAND_NAME));

            // act
            brandService.delete(brand.getId());

            // assert
            Brand deleted = brandJpaRepository.findById(brand.getId()).orElseThrow();
            assertThat(deleted.getDeletedAt()).isNotNull();
        }

        @DisplayName("존재하지 않는 brandId로 요청하면, 404에러 발생")
        @Test
        void deleteFailed_whenBrandIdIsNotExists(){
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                brandService.delete(NOT_EXISTED_BRAND_ID);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
