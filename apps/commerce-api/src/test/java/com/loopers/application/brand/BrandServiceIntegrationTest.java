package com.loopers.application.brand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.loopers.domain.brand.BrandRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.support.page.PageSize;
import com.loopers.utils.DatabaseCleanUp;

@SpringBootTest
class BrandServiceIntegrationTest {

    @Autowired
    private BrandService brandService;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("브랜드를 생성할 때,")
    @Nested
    class createBrand {

        @DisplayName("유효한 정보를 입력하면, 브랜드가 DB에 저장된다.")
        @Test
        void savesBrandToDatabase_whenValidInputProvided() {
            // arrange
            var name = "brand name";
            var logoUrl = "logo url";
            var description = "brand description";

            // act
            var result = brandService.createBrand(name, logoUrl, description);

            // assert
            var savedBrand = brandRepository.findById(result.id()).orElseThrow();
            assertAll(
                    () -> assertThat(savedBrand.getId()).isEqualTo(result.id()),
                    () -> assertThat(savedBrand.getName()).isEqualTo(name),
                    () -> assertThat(savedBrand.getLogoUrl()).isEqualTo(logoUrl),
                    () -> assertThat(savedBrand.getDescription()).isEqualTo(description)
            );
        }

        @DisplayName("이미 존재하는 브랜드 이름을 입력하면, 예외가 발생한다.")
        @Test
        void throwsException_whenDuplicateBrandNameProvided() {
            // arrange
            var name = "brand name";
            var logoUrl = "logo url";
            var description = "brand description";
            brandService.createBrand(name, logoUrl, description);

            // act & assert
            assertThatThrownBy(() -> brandService.createBrand(name, logoUrl, description))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.ALREADY_EXIST_BRAND_NAME));
        }

        @DisplayName("삭제된 브랜드 이름으로 다시 생성할 수 있다.")
        @Test
        void allowsCreatingBrandWithDeletedName() {
            // arrange
            var name = "brand name";
            var logoUrl = "logo url";
            var description = "brand description";
            var brandResult = brandService.createBrand(name, logoUrl, description);
            var savedBrand = brandRepository.findById(brandResult.id()).orElseThrow();
            savedBrand.delete();
            brandRepository.save(savedBrand);

            // act
            var result = brandService.createBrand(name, logoUrl, description);

            // assert
            var savedBrand2 = brandRepository.findById(result.id()).orElseThrow();
            assertAll(
                    () -> assertThat(savedBrand2.getId()).isEqualTo(result.id()),
                    () -> assertThat(savedBrand2.getName()).isEqualTo(name),
                    () -> assertThat(savedBrand2.getLogoUrl()).isEqualTo(logoUrl),
                    () -> assertThat(savedBrand2.getDescription()).isEqualTo(description)
            );
        }
    }

    @DisplayName("브랜드 목록을 조회할 때,")
    @Nested
    class GetBrands {

        @DisplayName("저장된 브랜드가 존재하면, 브랜드 목록이 반환된다.")
        @Test
        void returnsBrandList_whenBrandsExist() {
            // arrange
            brandService.createBrand("brand name 1", "logo url 1", "brand description 1");
            brandService.createBrand("brand name 2", "logo url 2", "brand description 2");

            // act
            var result = brandService.getBrands(new PageSize(0, 10));

            // assert
            assertAll(
                    () -> assertThat(result.content()).hasSize(2),
                    () -> assertThat(result.content()).extracting(BrandResult::name)
                            .containsExactly("brand name 2", "brand name 1"),
                    () -> assertThat(result.hasNext()).isFalse()
            );
        }

        @DisplayName("브랜드가 없으면, 빈 리스트가 반환된다.")
        @Test
        void returnsEmptyList_whenNoBrandsExist() {
            // act
            var result = brandService.getBrands(new PageSize(0, 10));

            // assert
            assertAll(
                    () -> assertThat(result.content()).isEmpty(),
                    () -> assertThat(result.hasNext()).isFalse()
            );
        }

        @DisplayName("페이지 크기보다 브랜드가 많으면, hasNext가 true이다.")
        @Test
        void returnsHasNextTrue_whenMoreBrandsExist() {
            // arrange
            brandService.createBrand("brand 1", "logo 1", "desc 1");
            brandService.createBrand("brand 2", "logo 2", "desc 2");
            brandService.createBrand("brand 3", "logo 3", "desc 3");

            // act
            var result = brandService.getBrands(new PageSize(0, 2));

            // assert
            assertAll(
                    () -> assertThat(result.content()).hasSize(2),
                    () -> assertThat(result.hasNext()).isTrue()
            );
        }

        @DisplayName("생성일 내림차순으로 정렬된다.")
        @Test
        void returnsBrandsSortedByCreatedAtDesc() {
            // arrange
            brandService.createBrand("first brand", "logo 1", "desc 1");
            brandService.createBrand("second brand", "logo 2", "desc 2");
            brandService.createBrand("third brand", "logo 3", "desc 3");

            // act
            var result = brandService.getBrands(new PageSize(0, 10));

            // assert
            assertThat(result.content()).extracting(BrandResult::name)
                    .containsExactly("third brand", "second brand", "first brand");
        }

        @DisplayName("삭제된 브랜드도 조회 대상에 포함된다.")
        @Test
        void includesDeletedBrands() {
            // arrange
            var created = brandService.createBrand("brand to delete", "logo", "desc");
            var brand = brandRepository.findById(created.id()).orElseThrow();
            brand.delete();
            brandRepository.save(brand);

            brandService.createBrand("active brand", "logo 2", "desc 2");

            // act
            var result = brandService.getBrands(new PageSize(0, 10));

            // assert
            assertAll(
                    () -> assertThat(result.content()).hasSize(2),
                    () -> assertThat(result.content()).extracting(BrandResult::name)
                            .containsExactly("active brand", "brand to delete"),
                    () -> assertThat(result.content().get(0).deletedAt()).isNull(),
                    () -> assertThat(result.content().get(1).deletedAt()).isNotNull()
            );
        }
    }

    @DisplayName("브랜드를 조회할 때,")
    @Nested
    class GetBrand {

        @DisplayName("존재하는 브랜드 ID를 입력하면, 브랜드가 반환된다.")
        @Test
        void returnsBrand_whenExistingBrandIdProvided() {
            // arrange
            var created = brandService.createBrand("brand name", "logo url", "brand description");

            // act
            var result = brandService.getBrand(created.id());

            // assert
            assertAll(
                    () -> assertThat(result.id()).isEqualTo(created.id()),
                    () -> assertThat(result.name()).isEqualTo("brand name"),
                    () -> assertThat(result.logoUrl()).isEqualTo("logo url"),
                    () -> assertThat(result.description()).isEqualTo("brand description")
            );
        }

        @DisplayName("삭제된 브랜드 ID를 입력하면, 브랜드가 반환된다.")
        @Test
        void returnsBrand_whenDeletedBrandIdProvided() {
            // arrange
            var created = brandService.createBrand("brand name", "logo url", "brand description");
            var brand = brandRepository.findById(created.id()).orElseThrow();
            brand.delete();
            brandRepository.save(brand);

            // act
            var result = brandService.getBrand(created.id());

            // assert
            assertAll(
                    () -> assertThat(result.id()).isEqualTo(created.id()),
                    () -> assertThat(result.name()).isEqualTo("brand name"),
                    () -> assertThat(result.logoUrl()).isEqualTo("logo url"),
                    () -> assertThat(result.description()).isEqualTo("brand description"),
                    () -> assertThat(result.deletedAt()).isNotNull()
            );
        }

        @DisplayName("존재하지 않는 브랜드 ID를 입력하면, 예외가 발생한다.")
        @Test
        void throwsException_whenNonExistingBrandIdProvided() {
            // act & assert
            assertThatThrownBy(() -> brandService.getBrand(999L))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BRAND_NOT_FOUND));
        }
    }

    @DisplayName("활성 브랜드를 조회할 때,")
    @Nested
    class GetActiveBrand {

        @DisplayName("존재하는 활성 브랜드 ID를 입력하면, 브랜드가 반환된다.")
        @Test
        void returnsActiveBrand_whenExistingActiveBrandIdProvided() {
            // arrange
            var created = brandService.createBrand("brand name", "logo url", "brand description");

            // act
            var result = brandService.getActiveBrand(created.id());

            // assert
            assertAll(
                    () -> assertThat(result.id()).isEqualTo(created.id()),
                    () -> assertThat(result.name()).isEqualTo("brand name"),
                    () -> assertThat(result.logoUrl()).isEqualTo("logo url"),
                    () -> assertThat(result.description()).isEqualTo("brand description")
            );
        }

        @DisplayName("삭제된 브랜드 ID를 입력하면, 예외가 발생한다.")
        @Test
        void throwsException_whenDeletedBrandIdProvided() {
            // arrange
            var created = brandService.createBrand("brand name", "logo url", "brand description");
            var brand = brandRepository.findById(created.id()).orElseThrow();
            brand.delete();
            brandRepository.save(brand);

            // act & assert
            assertThatThrownBy(() -> brandService.getActiveBrand(created.id()))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BRAND_NOT_FOUND));
        }

        @DisplayName("존재하지 않는 브랜드 ID를 입력하면, 예외가 발생한다.")
        @Test
        void throwsException_whenNonExistingBrandIdProvided() {
            // act & assert
            assertThatThrownBy(() -> brandService.getActiveBrand(999L))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BRAND_NOT_FOUND));
        }
    }

    @DisplayName("브랜드를 수정할 때,")
    @Nested
    class UpdateBrand {

        @DisplayName("존재하는 브랜드 ID와 유효한 정보를 입력하면, 브랜드가 수정된다.")
        @Test
        void updatesBrand_whenExistingBrandIdAndValidInputProvided() {
            // arrange
            var created = brandService.createBrand("brand name", "logo url", "brand description");
            var brandId = created.id();
            var newName = "new brand name";
            var newLogoUrl = "new logo url";
            var newDescription = "new brand description";

            // act
            brandService.updateBrand(brandId, newName, newLogoUrl, newDescription);

            // assert
            var updatedBrand = brandRepository.findById(brandId).orElseThrow();
            assertAll(
                    () -> assertThat(updatedBrand.getId()).isEqualTo(brandId),
                    () -> assertThat(updatedBrand.getName()).isEqualTo(newName),
                    () -> assertThat(updatedBrand.getLogoUrl()).isEqualTo(newLogoUrl),
                    () -> assertThat(updatedBrand.getDescription()).isEqualTo(newDescription)
            );
        }

        @DisplayName("존재하지 않는 브랜드 ID를 입력하면, 예외가 발생한다.")
        @Test
        void throwsException_whenBrandNotFound() {
            // act & assert
            assertThatThrownBy(() -> brandService.updateBrand(999L, "new name", "new logo", "new desc"))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BRAND_NOT_FOUND));
        }

        @DisplayName("다른 활성 브랜드와 동일한 이름으로 수정하면, 예외가 발생한다.")
        @Test
        void throwsException_whenDuplicateNameExists() {
            // arrange
            brandService.createBrand("existing brand", "logo1", "desc1");
            var created = brandService.createBrand("my brand", "logo2", "desc2");

            // act & assert
            assertThatThrownBy(() -> brandService.updateBrand(created.id(), "existing brand", "logo2", "desc2"))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.ALREADY_EXIST_BRAND_NAME));
        }

        @DisplayName("자기 자신과 동일한 이름으로 수정하면, 정상적으로 수정된다.")
        @Test
        void updatesBrand_whenKeepingSameName() {
            // arrange
            var created = brandService.createBrand("brand name", "logo url", "description");

            // act
            brandService.updateBrand(created.id(), "brand name", "new logo url", "new description");

            // assert
            var updatedBrand = brandRepository.findById(created.id()).orElseThrow();
            assertAll(
                    () -> assertThat(updatedBrand.getName()).isEqualTo("brand name"),
                    () -> assertThat(updatedBrand.getLogoUrl()).isEqualTo("new logo url"),
                    () -> assertThat(updatedBrand.getDescription()).isEqualTo("new description")
            );
        }

        @DisplayName("삭제된 브랜드와 동일한 이름으로 수정하면, 정상적으로 수정된다.")
        @Test
        void updatesBrand_whenNameMatchesDeletedBrand() {
            // arrange
            var deleted = brandService.createBrand("deleted brand", "logo1", "desc1");
            var brand = brandRepository.findById(deleted.id()).orElseThrow();
            brand.delete();
            brandRepository.save(brand);

            var created = brandService.createBrand("my brand", "logo2", "desc2");

            // act
            brandService.updateBrand(created.id(), "deleted brand", "logo2", "desc2");

            // assert
            var updatedBrand = brandRepository.findById(created.id()).orElseThrow();
            assertThat(updatedBrand.getName()).isEqualTo("deleted brand");
        }
    }
}
