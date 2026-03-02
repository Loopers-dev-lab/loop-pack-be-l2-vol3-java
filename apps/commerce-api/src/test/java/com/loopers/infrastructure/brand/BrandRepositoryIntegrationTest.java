package com.loopers.infrastructure.brand;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(MySqlTestContainersConfig.class)
class BrandRepositoryIntegrationTest {

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("save 시")
    @Nested
    class Save {

        @DisplayName("저장한 브랜드를 findById로 조회할 수 있다.")
        @Test
        void save_shouldPersistAndFindById() {
            // given
            BrandModel brand = BrandModel.create("테스트 브랜드");

            // when
            BrandModel saved = brandRepository.save(brand);
            Optional<BrandModel> found = brandRepository.findById(saved.getId());

            // then
            assertThat(found).isPresent();
            assertThat(found.get().getId()).isEqualTo(saved.getId());
            assertThat(found.get().getName()).isEqualTo("테스트 브랜드");
            assertThat(found.get().isDeleted()).isFalse();
        }

        @DisplayName("저장한 브랜드를 findByIdAndNotDeleted로 조회할 수 있다.")
        @Test
        void save_shouldBeFoundByFindByIdAndNotDeleted() {
            // given
            BrandModel brand = BrandModel.create("미삭제 브랜드");
            BrandModel saved = brandRepository.save(brand);

            // when
            Optional<BrandModel> found = brandRepository.findByIdAndNotDeleted(saved.getId());

            // then
            assertThat(found).isPresent();
            assertThat(found.get().getName()).isEqualTo("미삭제 브랜드");
            assertThat(found.get().isDeleted()).isFalse();
        }
    }

    @DisplayName("findById 시")
    @Nested
    class FindById {

        @DisplayName("존재하지 않는 ID면 empty를 반환한다.")
        @Test
        void findById_withNonExistentId_shouldReturnEmpty() {
            // given
            Long nonExistentId = 999_999L;

            // when
            Optional<BrandModel> found = brandRepository.findById(nonExistentId);

            // then
            assertThat(found).isEmpty();
        }

    }

    @DisplayName("findByIdAndNotDeleted 시")
    @Nested
    class FindByIdAndNotDeleted {

        @DisplayName("존재하지 않는 ID면 empty를 반환한다.")
        @Test
        void findByIdAndNotDeleted_withNonExistentId_shouldReturnEmpty() {
            // given
            Long nonExistentId = 999_999L;

            // when
            Optional<BrandModel> found = brandRepository.findByIdAndNotDeleted(nonExistentId);

            // then
            assertThat(found).isEmpty();
        }

        @DisplayName("soft delete된 브랜드는 조회되지 않는다.")
        @Test
        void findByIdAndNotDeleted_whenBrandIsDeleted_shouldReturnEmpty() {
            // given
            BrandModel brand = BrandModel.create("삭제될 브랜드");
            BrandModel saved = brandRepository.save(brand);
            saved.delete();
            brandRepository.save(saved);

            // when
            Optional<BrandModel> found = brandRepository.findByIdAndNotDeleted(saved.getId());

            // then
            assertThat(found).isEmpty();
        }

        @DisplayName("soft delete된 브랜드는 findById로는 조회된다.")
        @Test
        void findById_whenBrandIsDeleted_shouldStillReturnBrand() {
            // given
            BrandModel brand = BrandModel.create("삭제된 브랜드");
            BrandModel saved = brandRepository.save(brand);
            saved.delete();
            brandRepository.save(saved);

            // when
            Optional<BrandModel> found = brandRepository.findById(saved.getId());

            // then
            assertThat(found).isPresent();
            assertThat(found.get().isDeleted()).isTrue();
        }
    }
}
