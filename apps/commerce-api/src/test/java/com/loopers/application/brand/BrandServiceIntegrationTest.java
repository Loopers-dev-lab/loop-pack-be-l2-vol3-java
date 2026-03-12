package com.loopers.application.brand;

import com.loopers.domain.brand.BrandInfo;
import com.loopers.infrastructure.persistence.jpa.brand.BrandJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class BrandServiceIntegrationTest {

    @Autowired
    private BrandService brandService;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("브랜드를 생성할 때,")
    @Nested
    class Create {

        @DisplayName("유효한 정보로 생성하면, 브랜드가 저장된다.")
        @Test
        void createsBrand_whenValidInfoIsProvided() {
            // arrange
            BrandInfo info = new BrandInfo("Nike", "Just Do It", "https://example.com/nike.png");

            // act
            BrandResult result = brandService.create(info);

            // assert
            assertAll(
                () -> assertThat(result).isNotNull(),
                () -> assertThat(result.id()).isNotNull(),
                () -> assertThat(result.name()).isEqualTo("Nike"),
                () -> assertThat(result.description()).isEqualTo("Just Do It"),
                () -> assertThat(result.logoUrl()).isEqualTo("https://example.com/nike.png"),
                () -> assertThat(result.createdAt()).isNotNull(),
                () -> assertThat(result.updatedAt()).isNotNull()
            );
        }

        @DisplayName("이미 존재하는 브랜드명으로 생성하면, BRAND_ALREADY_EXISTS 예외가 발생한다.")
        @Test
        void throwsBrandAlreadyExistsException_whenNameAlreadyExists() {
            // arrange
            BrandInfo info = new BrandInfo("Nike", "Just Do It", null);
            brandService.create(info);

            BrandInfo duplicateInfo = new BrandInfo("Nike", "Another description", null);

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                brandService.create(duplicateInfo);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BRAND_ALREADY_EXISTS);
        }
    }

    @DisplayName("브랜드를 조회할 때,")
    @Nested
    class FindById {

        @DisplayName("존재하는 ID로 조회하면, 브랜드 정보를 반환한다.")
        @Test
        void returnsBrand_whenIdExists() {
            // arrange
            BrandInfo info = new BrandInfo("Nike", "Just Do It", "https://example.com/nike.png");
            BrandResult created = brandService.create(info);

            // act
            BrandResult result = brandService.findById(created.id());

            // assert
            assertAll(
                () -> assertThat(result).isNotNull(),
                () -> assertThat(result.id()).isEqualTo(created.id()),
                () -> assertThat(result.name()).isEqualTo("Nike")
            );
        }

        @DisplayName("존재하지 않는 ID로 조회하면, BRAND_NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsBrandNotFoundException_whenIdDoesNotExist() {
            // arrange
            Long nonExistentId = 999L;

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                brandService.findById(nonExistentId);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BRAND_NOT_FOUND);
        }

        @DisplayName("삭제된 브랜드를 조회하면, BRAND_NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsBrandNotFoundException_whenBrandIsDeleted() {
            // arrange
            BrandInfo info = new BrandInfo("Nike", "Just Do It", null);
            BrandResult created = brandService.create(info);
            brandService.delete(created.id());

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                brandService.findById(created.id());
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BRAND_NOT_FOUND);
        }
    }

    @DisplayName("브랜드 목록을 조회할 때,")
    @Nested
    class FindAll {

        @DisplayName("브랜드가 존재하면, 목록을 반환한다.")
        @Test
        void returnsBrandList_whenBrandsExist() {
            // arrange
            brandService.create(new BrandInfo("Nike", null, null));
            brandService.create(new BrandInfo("Adidas", null, null));
            brandService.create(new BrandInfo("Puma", null, null));

            // act
            List<BrandResult> result = brandService.findAll();

            // assert
            assertThat(result).hasSize(3);
        }

        @DisplayName("삭제된 브랜드는 목록에서 제외된다.")
        @Test
        void excludesDeletedBrands_fromList() {
            // arrange
            BrandResult nike = brandService.create(new BrandInfo("Nike", null, null));
            brandService.create(new BrandInfo("Adidas", null, null));
            brandService.delete(nike.id());

            // act
            List<BrandResult> result = brandService.findAll();

            // assert
            assertAll(
                () -> assertThat(result).hasSize(1),
                () -> assertThat(result.get(0).name()).isEqualTo("Adidas")
            );
        }
    }

    @DisplayName("브랜드를 수정할 때,")
    @Nested
    class Update {

        @DisplayName("유효한 정보로 수정하면, 브랜드가 업데이트된다.")
        @Test
        void updatesBrand_whenValidInfoIsProvided() {
            // arrange
            BrandInfo info = new BrandInfo("Nike", "Original", null);
            BrandResult created = brandService.create(info);

            BrandInfo updateInfo = new BrandInfo("Nike Updated", "Updated description", "https://example.com/updated.png");

            // act
            BrandResult result = brandService.update(created.id(), updateInfo);

            // assert
            assertAll(
                () -> assertThat(result.name()).isEqualTo("Nike Updated"),
                () -> assertThat(result.description()).isEqualTo("Updated description"),
                () -> assertThat(result.logoUrl()).isEqualTo("https://example.com/updated.png")
            );
        }

        @DisplayName("이미 존재하는 다른 브랜드명으로 수정하면, BRAND_ALREADY_EXISTS 예외가 발생한다.")
        @Test
        void throwsBrandAlreadyExistsException_whenUpdatingToExistingName() {
            // arrange
            brandService.create(new BrandInfo("Nike", null, null));
            BrandResult adidas = brandService.create(new BrandInfo("Adidas", null, null));

            BrandInfo updateInfo = new BrandInfo("Nike", null, null);

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                brandService.update(adidas.id(), updateInfo);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BRAND_ALREADY_EXISTS);
        }

        @DisplayName("같은 이름으로 수정하면, 정상적으로 업데이트된다.")
        @Test
        void updatesBrand_whenUpdatingWithSameName() {
            // arrange
            BrandInfo info = new BrandInfo("Nike", "Original", null);
            BrandResult created = brandService.create(info);

            BrandInfo updateInfo = new BrandInfo("Nike", "Updated description", null);

            // act
            BrandResult result = brandService.update(created.id(), updateInfo);

            // assert
            assertAll(
                () -> assertThat(result.name()).isEqualTo("Nike"),
                () -> assertThat(result.description()).isEqualTo("Updated description")
            );
        }

        @DisplayName("삭제된 브랜드를 수정하면, BRAND_NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsBrandNotFoundException_whenUpdatingDeletedBrand() {
            // arrange
            BrandInfo info = new BrandInfo("Nike", null, null);
            BrandResult created = brandService.create(info);
            brandService.delete(created.id());

            BrandInfo updateInfo = new BrandInfo("Nike Updated", null, null);

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                brandService.update(created.id(), updateInfo);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BRAND_NOT_FOUND);
        }
    }

    @DisplayName("브랜드를 삭제할 때,")
    @Nested
    class Delete {

        @DisplayName("존재하는 브랜드를 삭제하면, soft delete 처리된다.")
        @Test
        void softDeletesBrand_whenBrandExists() {
            // arrange
            BrandInfo info = new BrandInfo("Nike", null, null);
            BrandResult created = brandService.create(info);

            // act
            brandService.delete(created.id());

            // assert
            assertThat(brandJpaRepository.findById(created.id()))
                .isPresent()
                .hasValueSatisfying(brand -> assertThat(brand.getDeletedAt()).isNotNull());
        }

        @DisplayName("존재하지 않는 브랜드를 삭제하면, BRAND_NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsBrandNotFoundException_whenBrandDoesNotExist() {
            // arrange
            Long nonExistentId = 999L;

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                brandService.delete(nonExistentId);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BRAND_NOT_FOUND);
        }
    }
}
