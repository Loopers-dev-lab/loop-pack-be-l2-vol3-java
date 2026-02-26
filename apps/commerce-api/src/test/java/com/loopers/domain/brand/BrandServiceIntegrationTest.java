package com.loopers.domain.brand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.loopers.support.BaseIntegrationTest;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

class BrandServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private BrandRepository brandRepository;

    @DisplayName("브랜드를 생성할 때,")
    @Nested
    class Create {

        @DisplayName("유효한 정보를 입력하면, 브랜드가 DB에 저장된다.")
        @Test
        void savesBrandToDatabase_whenValidInputProvided() {
            // arrange
            var name = "brand name";
            var logoUrl = "logo url";
            var description = "brand description";

            // act
            var result = brandService.create(name, logoUrl, description);

            // assert
            var savedBrand = brandRepository.findById(result.getId()).orElseThrow();
            assertAll(
                    () -> assertThat(savedBrand.getId()).isEqualTo(result.getId()),
                    () -> assertThat(savedBrand.getName()).isEqualTo(name),
                    () -> assertThat(savedBrand.getLogoUrl()).isEqualTo(logoUrl),
                    () -> assertThat(savedBrand.getDescription()).isEqualTo(description)
            );
        }

        @DisplayName("이미 존재하는 브랜드 이름을 입력하면, ALREADY_EXISTS_BRAND_NAME 예외가 발생한다.")
        @Test
        void throwsException_whenDuplicateBrandNameProvided() {
            // arrange
            var name = "brand name";
            brandService.create(name, "logo url", "brand description");

            // act & assert
            assertThatThrownBy(() -> brandService.create(name, "logo url", "brand description"))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.ALREADY_EXISTS_BRAND_NAME));
        }

        @DisplayName("삭제된 브랜드 이름으로 다시 생성할 수 있다.")
        @Test
        void allowsCreatingBrandWithDeletedName() {
            // arrange
            var name = "brand name";
            var brand = brandService.create(name, "logo url", "brand description");
            brandService.delete(brand.getId());

            // act
            var result = brandService.create(name, "logo url", "brand description");

            // assert
            assertAll(
                    () -> assertThat(result.getId()).isNotNull(),
                    () -> assertThat(result.getName()).isEqualTo(name)
            );
        }
    }

    @DisplayName("브랜드를 수정할 때,")
    @Nested
    class Update {

        @DisplayName("존재하는 브랜드 ID와 유효한 정보를 입력하면, 브랜드가 수정된다.")
        @Test
        void updatesBrand_whenExistingBrandIdAndValidInputProvided() {
            // arrange
            var created = brandService.create("brand name", "logo url", "brand description");
            var brandId = created.getId();

            // act
            brandService.update(brandId, "new brand name", "new logo url", "new brand description");

            // assert
            var updatedBrand = brandRepository.findById(brandId).orElseThrow();
            assertAll(
                    () -> assertThat(updatedBrand.getName()).isEqualTo("new brand name"),
                    () -> assertThat(updatedBrand.getLogoUrl()).isEqualTo("new logo url"),
                    () -> assertThat(updatedBrand.getDescription()).isEqualTo("new brand description")
            );
        }

        @DisplayName("존재하지 않는 브랜드 ID를 입력하면, BRAND_NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsException_whenBrandNotFound() {
            // act & assert
            assertThatThrownBy(() -> brandService.update(999L, "new name", "new logo", "new desc"))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BRAND_NOT_FOUND));
        }

        @DisplayName("다른 활성 브랜드와 동일한 이름으로 수정하면, ALREADY_EXISTS_BRAND_NAME 예외가 발생한다.")
        @Test
        void throwsException_whenDuplicateNameExists() {
            // arrange
            brandService.create("existing brand", "logo1", "desc1");
            var created = brandService.create("my brand", "logo2", "desc2");

            // act & assert
            assertThatThrownBy(() -> brandService.update(created.getId(), "existing brand", "logo2", "desc2"))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.ALREADY_EXISTS_BRAND_NAME));
        }

        @DisplayName("자기 자신과 동일한 이름으로 수정하면, 정상적으로 수정된다.")
        @Test
        void updatesBrand_whenKeepingSameName() {
            // arrange
            var created = brandService.create("brand name", "logo url", "description");

            // act & assert
            assertThatCode(() -> brandService.update(created.getId(), "brand name", "new logo url", "new description"))
                    .doesNotThrowAnyException();
        }

        @DisplayName("삭제된 브랜드와 동일한 이름으로 수정하면, 정상적으로 수정된다.")
        @Test
        void updatesBrand_whenNameMatchesDeletedBrand() {
            // arrange
            var deleted = brandService.create("deleted brand", "logo1", "desc1");
            brandService.delete(deleted.getId());

            var created = brandService.create("my brand", "logo2", "desc2");

            // act
            brandService.update(created.getId(), "deleted brand", "logo2", "desc2");

            // assert
            var updatedBrand = brandRepository.findById(created.getId()).orElseThrow();
            assertThat(updatedBrand.getName()).isEqualTo("deleted brand");
        }
    }

    @DisplayName("브랜드를 삭제할 때,")
    @Nested
    class Delete {

        @DisplayName("유효한 브랜드이면, 브랜드가 삭제된다.")
        @Test
        void deletesBrand_whenBrandExists() {
            // arrange
            var created = brandService.create("브랜드명", "logo url", "설명");

            // act
            brandService.delete(created.getId());

            // assert
            var deleted = brandRepository.findById(created.getId()).orElseThrow();
            assertThat(deleted.getDeletedAt()).isNotNull();
        }

        @DisplayName("존재하지 않는 브랜드이면, BRAND_NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsException_whenBrandNotFound() {
            // act & assert
            assertThatThrownBy(() -> brandService.delete(999L))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BRAND_NOT_FOUND));
        }
    }

    @DisplayName("브랜드 존재 여부를 검증할 때,")
    @Nested
    class ValidateBrandExists {

        @DisplayName("활성 브랜드가 존재하면, 예외가 발생하지 않는다.")
        @Test
        void doesNotThrow_whenActiveBrandExists() {
            // arrange
            var created = brandService.create("브랜드명", "logo url", "설명");

            // act & assert
            assertThatCode(() -> brandService.validateBrandExists(created.getId()))
                    .doesNotThrowAnyException();
        }

        @DisplayName("삭제된 브랜드여도, 예외가 발생하지 않는다.")
        @Test
        void doesNotThrow_whenBrandIsDeleted() {
            // arrange
            var created = brandService.create("브랜드명", "logo url", "설명");
            brandService.delete(created.getId());

            // act & assert
            assertThatCode(() -> brandService.validateBrandExists(created.getId()))
                    .doesNotThrowAnyException();
        }

        @DisplayName("존재하지 않는 브랜드이면, BRAND_NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsException_whenBrandNotFound() {
            // act & assert
            assertThatThrownBy(() -> brandService.validateBrandExists(999L))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BRAND_NOT_FOUND));
        }
    }

    @DisplayName("활성 브랜드 존재 여부를 검증할 때,")
    @Nested
    class ValidateActiveBrandExists {

        @DisplayName("활성 브랜드가 존재하면, 예외가 발생하지 않는다.")
        @Test
        void doesNotThrow_whenActiveBrandExists() {
            // arrange
            var created = brandService.create("브랜드명", "logo url", "설명");

            // act & assert
            assertThatCode(() -> brandService.validateActiveBrandExists(created.getId()))
                    .doesNotThrowAnyException();
        }

        @DisplayName("삭제된 브랜드이면, BRAND_NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsException_whenBrandIsDeleted() {
            // arrange
            var created = brandService.create("브랜드명", "logo url", "설명");
            brandService.delete(created.getId());

            // act & assert
            assertThatThrownBy(() -> brandService.validateActiveBrandExists(created.getId()))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BRAND_NOT_FOUND));
        }

        @DisplayName("존재하지 않는 브랜드이면, BRAND_NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsException_whenBrandNotFound() {
            // act & assert
            assertThatThrownBy(() -> brandService.validateActiveBrandExists(999L))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BRAND_NOT_FOUND));
        }
    }
}
