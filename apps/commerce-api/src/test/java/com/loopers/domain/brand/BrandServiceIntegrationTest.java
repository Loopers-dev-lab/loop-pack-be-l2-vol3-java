package com.loopers.domain.brand;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Import(MySqlTestContainersConfig.class)
class BrandServiceIntegrationTest {

    @Autowired
    private BrandService brandService;

    @Autowired
    private ProductService productService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("register 시")
    @Nested
    class Register {

        @DisplayName("유효한 이름이 주어지면, 저장 후 ID가 부여된 브랜드를 반환한다.")
        @Test
        void register_withValidName_shouldPersistAndReturn() {
            // given
            String name = "통합테스트 브랜드";

            // when
            BrandModel saved = brandService.register(name);

            // then
            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getName()).isEqualTo(name);
            assertThat(saved.isDeleted()).isFalse();
        }
    }

    @DisplayName("findById 시")
    @Nested
    class FindById {

        @DisplayName("저장된 브랜드 ID로 조회하면 Optional에 담아 반환한다.")
        @Test
        void findById_withSavedBrand_shouldReturnPresent() {
            // given
            BrandModel saved = brandService.register("조회용 브랜드");

            // when
            Optional<BrandModel> result = brandService.findById(saved.getId());

            // then
            assertThat(result).isPresent();
            assertThat(result.get().getName()).isEqualTo("조회용 브랜드");
        }

        @DisplayName("존재하지 않는 ID로 조회하면 empty를 반환한다.")
        @Test
        void findById_withNonExistentId_shouldReturnEmpty() {
            // when
            Optional<BrandModel> result = brandService.findById(999_999L);

            // then
            assertThat(result).isEmpty();
        }
    }

    @DisplayName("findByIdAndNotDeleted 시")
    @Nested
    class FindByIdAndNotDeleted {

        @DisplayName("미삭제 브랜드 ID로 조회하면 Optional에 담아 반환한다.")
        @Test
        void findByIdAndNotDeleted_withNotDeletedBrand_shouldReturnPresent() {
            // given
            BrandModel saved = brandService.register("미삭제 브랜드");

            // when
            Optional<BrandModel> result = brandService.findByIdAndNotDeleted(saved.getId());

            // then
            assertThat(result).isPresent();
            assertThat(result.get().getName()).isEqualTo("미삭제 브랜드");
        }

        @DisplayName("삭제된 브랜드 ID로 조회하면 empty를 반환한다.")
        @Test
        void findByIdAndNotDeleted_withDeletedBrand_shouldReturnEmpty() {
            // given
            BrandModel saved = brandService.register("삭제될 브랜드");
            brandService.delete(saved.getId());

            // when
            Optional<BrandModel> result = brandService.findByIdAndNotDeleted(saved.getId());

            // then
            assertThat(result).isEmpty();
        }
    }

    @DisplayName("update 시")
    @Nested
    class Update {

        @DisplayName("존재하지 않는 ID로 수정하면 NOT_FOUND 예외가 발생한다.")
        @Test
        void update_withNonExistentId_shouldThrowNotFound() {
            // when & then
            CoreException exception = assertThrows(CoreException.class, () -> {
                brandService.update(999_999L, "새 이름");
            });
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("유효한 ID와 이름이 주어지면, DB에 이름이 갱신된다.")
        @Test
        void update_withValidIdAndName_shouldPersistUpdate() {
            // given
            BrandModel saved = brandService.register("기존 이름");

            // when
            BrandModel updated = brandService.update(saved.getId(), "갱신된 이름");

            // then
            assertThat(updated.getName()).isEqualTo("갱신된 이름");
            Optional<BrandModel> found = brandService.findByIdAndNotDeleted(saved.getId());
            assertThat(found).isPresent();
            assertThat(found.get().getName()).isEqualTo("갱신된 이름");
        }
    }

    @DisplayName("delete 시")
    @Nested
    class Delete {

        @DisplayName("존재하지 않는 ID로 삭제하면 NOT_FOUND 예외가 발생한다.")
        @Test
        void delete_withNonExistentId_shouldThrowNotFound() {
            // when & then
            CoreException exception = assertThrows(CoreException.class, () -> {
                brandService.delete(999_999L);
            });
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("존재하는 ID로 삭제하면 soft delete되어 findByIdAndNotDeleted에서 조회되지 않는다.")
        @Test
        void delete_withExistingId_shouldSoftDelete() {
            // given
            BrandModel saved = brandService.register("삭제 대상");

            // when
            brandService.delete(saved.getId());

            // then
            assertThat(brandService.findByIdAndNotDeleted(saved.getId())).isEmpty();
            Optional<BrandModel> byId = brandService.findById(saved.getId());
            assertThat(byId).isPresent();
            assertThat(byId.get().isDeleted()).isTrue();
        }

        @DisplayName("브랜드 삭제 시 해당 브랜드의 모든 상품도 연쇄 soft delete된다.")
        @Test
        void delete_whenBrandHasProducts_shouldCascadeSoftDeleteProducts() {
            // given
            BrandModel brand = brandService.register("연쇄삭제 대상 브랜드");
            Long brandId = brand.getId();
            ProductModel p1 = productService.register(brandId, "상품1", new BigDecimal("1000"), 5);
            ProductModel p2 = productService.register(brandId, "상품2", new BigDecimal("2000"), 10);

            // when
            brandService.delete(brandId);

            // then: 브랜드 soft delete
            assertThat(brandService.findByIdAndNotDeleted(brandId)).isEmpty();
            // then: 해당 브랜드 상품들도 soft delete (findByIdAndNotDeleted empty, findById로는 deletedAt 설정됨)
            assertThat(productService.findByIdAndNotDeleted(p1.getId())).isEmpty();
            assertThat(productService.findByIdAndNotDeleted(p2.getId())).isEmpty();
            Optional<ProductModel> found1 = productService.findById(p1.getId());
            Optional<ProductModel> found2 = productService.findById(p2.getId());
            assertThat(found1).isPresent();
            assertThat(found1.get().isDeleted()).isTrue();
            assertThat(found2).isPresent();
            assertThat(found2.get().isDeleted()).isTrue();
        }
    }
}
