package com.loopers.domain.brand;

import com.loopers.domain.PageResult;
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
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class BrandServiceIntegrationTest {

    @Autowired
    private BrandService brandService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("브랜드를 등록할 때, ")
    @Nested
    class Register {

        @DisplayName("올바른 이름이면, 브랜드가 저장되고 반환된다.")
        @Test
        void savesAndReturnsBrand_whenNameIsValid() {
            // act
            Brand result = brandService.register("나이키");

            // assert
            assertAll(
                () -> assertThat(result.getId()).isNotNull(),
                () -> assertThat(result.getName()).isEqualTo("나이키")
            );
        }
    }

    @DisplayName("브랜드를 조회할 때, ")
    @Nested
    class GetById {

        @DisplayName("존재하는 브랜드이면, 브랜드를 반환한다.")
        @Test
        void returnsBrand_whenBrandExists() {
            // arrange
            Brand brand = brandService.register("나이키");

            // act
            Brand result = brandService.getById(brand.getId());

            // assert
            assertThat(result.getName()).isEqualTo("나이키");
        }

        @DisplayName("존재하지 않는 브랜드이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenBrandDoesNotExist() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> brandService.getById(999L));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("삭제된 브랜드이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenBrandIsDeleted() {
            // arrange
            Brand brand = brandService.register("나이키");
            brandService.delete(brand.getId());

            // act
            CoreException result = assertThrows(CoreException.class, () -> brandService.getById(brand.getId()));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("브랜드 목록을 조회할 때, ")
    @Nested
    class GetAll {

        @DisplayName("브랜드가 존재하면, 페이지 결과를 반환한다.")
        @Test
        void returnsPageResult_whenBrandsExist() {
            // arrange
            brandService.register("나이키");
            brandService.register("아디다스");
            brandService.register("뉴발란스");

            // act
            PageResult<Brand> result = brandService.getAll(0, 2);

            // assert
            assertAll(
                () -> assertThat(result.items()).hasSize(2),
                () -> assertThat(result.totalElements()).isEqualTo(3),
                () -> assertThat(result.totalPages()).isEqualTo(2),
                () -> assertThat(result.page()).isEqualTo(0)
            );
        }

        @DisplayName("삭제된 브랜드는 목록에 포함되지 않는다.")
        @Test
        void excludesDeletedBrands() {
            // arrange
            Brand brand = brandService.register("나이키");
            brandService.register("아디다스");
            brandService.delete(brand.getId());

            // act
            PageResult<Brand> result = brandService.getAll(0, 20);

            // assert
            assertAll(
                () -> assertThat(result.items()).hasSize(1),
                () -> assertThat(result.items().get(0).getName()).isEqualTo("아디다스")
            );
        }
    }

    @DisplayName("브랜드를 수정할 때, ")
    @Nested
    class Update {

        @DisplayName("올바른 이름이면, 브랜드 이름이 수정된다.")
        @Test
        void updatesBrandName_whenNameIsValid() {
            // arrange
            Brand brand = brandService.register("나이키");

            // act
            Brand result = brandService.update(brand.getId(), "아디다스");

            // assert
            assertThat(result.getName()).isEqualTo("아디다스");
        }

        @DisplayName("존재하지 않는 브랜드이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenBrandDoesNotExist() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> brandService.update(999L, "아디다스"));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("브랜드를 삭제할 때, ")
    @Nested
    class Delete {

        @DisplayName("존재하는 브랜드이면, 논리 삭제된다.")
        @Test
        void softDeletesBrand_whenBrandExists() {
            // arrange
            Brand brand = brandService.register("나이키");

            // act
            brandService.delete(brand.getId());

            // assert
            CoreException result = assertThrows(CoreException.class, () -> brandService.getById(brand.getId()));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("존재하지 않는 브랜드이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenBrandDoesNotExist() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> brandService.delete(999L));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
