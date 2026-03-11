package com.loopers.infrastructure.brand;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.brand.BrandStatus;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class BrandRepositoryIntegrationTest {

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private Brand createAndSaveBrand(String name, String description) {
        Brand brand = Brand.register(name, description);
        return brandRepository.save(brand);
    }

    @Nested
    @DisplayName("save 메서드는")
    class Save {

        @Test
        void 새로운_브랜드를_저장하면_ID가_생성된다() {
            // arrange
            Brand brand = Brand.register("나이키", "스포츠 브랜드");

            // act
            Brand saved = brandRepository.save(brand);

            // assert
            assertThat(saved.getId()).isNotNull();
        }

        @Test
        void 저장된_브랜드의_필드가_올바르게_저장된다() {
            // arrange & act
            Brand saved = createAndSaveBrand("나이키", "스포츠 브랜드");

            // assert
            assertThat(saved)
                    .extracting(Brand::getName, Brand::getDescription, Brand::getStatus)
                    .containsExactly("나이키", "스포츠 브랜드", BrandStatus.ACTIVE);
        }
    }

    @Nested
    @DisplayName("findById 메서드는")
    class FindById {

        @Test
        void 존재하는_ID로_조회하면_브랜드를_반환한다() {
            // arrange
            Brand saved = createAndSaveBrand("나이키", "스포츠 브랜드");

            // act
            Optional<Brand> result = brandRepository.findById(saved.getId());

            // assert
            assertThat(result).isPresent();
        }

        @Test
        void 존재하지_않는_ID로_조회하면_empty를_반환한다() {
            // act
            Optional<Brand> result = brandRepository.findById(999L);

            // assert
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findAll(page, size) 메서드는")
    class FindAllPaged {

        @Test
        void 저장된_브랜드를_페이지네이션으로_반환한다() {
            // arrange
            createAndSaveBrand("나이키", "스포츠 브랜드");
            createAndSaveBrand("아디다스", "독일 브랜드");
            createAndSaveBrand("뉴발란스", "미국 브랜드");

            // act - 첫 페이지, 2개씩
            List<Brand> result = brandRepository.findAll(0, 2);

            // assert
            assertThat(result).hasSize(2);
        }

        @Test
        void 두번째_페이지를_올바르게_반환한다() {
            // arrange
            createAndSaveBrand("나이키", "스포츠 브랜드");
            createAndSaveBrand("아디다스", "독일 브랜드");
            createAndSaveBrand("뉴발란스", "미국 브랜드");

            // act - 두번째 페이지, 2개씩
            List<Brand> result = brandRepository.findAll(1, 2);

            // assert
            assertThat(result).hasSize(1);
        }
    }

    @Nested
    @DisplayName("count 메서드는")
    class Count {

        @Test
        void 저장된_전체_브랜드_수를_반환한다() {
            // arrange
            createAndSaveBrand("나이키", "스포츠 브랜드");
            createAndSaveBrand("아디다스", "독일 브랜드");

            // act
            long result = brandRepository.count();

            // assert
            assertThat(result).isEqualTo(2);
        }

        @Test
        void 브랜드가_없으면_0을_반환한다() {
            // act
            long result = brandRepository.count();

            // assert
            assertThat(result).isZero();
        }
    }

    @Nested
    @DisplayName("findAllActive 메서드는")
    class FindAllActive {

        @Test
        void ACTIVE_상태이고_삭제되지_않은_브랜드만_반환한다() {
            // arrange
            createAndSaveBrand("나이키", "스포츠 브랜드");

            Brand inactive = Brand.register("비활성브랜드", "설명");
            inactive.changeStatus(BrandStatus.INACTIVE);
            brandRepository.save(inactive);

            Brand deleted = Brand.register("삭제브랜드", "설명");
            deleted.discontinue();
            brandRepository.save(deleted);

            // act
            List<Brand> result = brandRepository.findAllActive();

            // assert
            assertThat(result).hasSize(1);
        }

        @Test
        void ACTIVE_브랜드가_없으면_빈_리스트를_반환한다() {
            // act
            List<Brand> result = brandRepository.findAllActive();

            // assert
            assertThat(result).isEmpty();
        }
    }
}
