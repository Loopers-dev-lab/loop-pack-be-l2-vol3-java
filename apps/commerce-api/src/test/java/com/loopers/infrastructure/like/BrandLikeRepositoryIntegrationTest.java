package com.loopers.infrastructure.like;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.brand.BrandStatus;
import com.loopers.domain.like.BrandLike;
import com.loopers.domain.like.BrandLikeRepository;
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
class BrandLikeRepositoryIntegrationTest {

    @Autowired
    private BrandLikeRepository brandLikeRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private Brand createActiveBrand(String name) {
        return brandRepository.save(Brand.create(name, name + " 설명"));
    }

    @Nested
    @DisplayName("save 메서드는")
    class Save {

        @Test
        void 새로운_좋아요를_저장하면_ID가_생성된다() {
            // arrange
            Brand brand = createActiveBrand("나이키");

            // act
            BrandLike saved = brandLikeRepository.save(BrandLike.create(1L, brand.getId()));

            // assert
            assertThat(saved.getId()).isNotNull();
        }

        @Test
        void 저장된_좋아요의_필드가_올바르게_저장된다() {
            // arrange
            Brand brand = createActiveBrand("나이키");

            // act
            BrandLike saved = brandLikeRepository.save(BrandLike.create(1L, brand.getId()));

            // assert
            assertThat(saved)
                    .extracting(BrandLike::getUserId, BrandLike::getBrandId)
                    .containsExactly(1L, brand.getId());
            assertThat(saved.getCreatedAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("findByUserIdAndBrandId 메서드는")
    class FindByUserIdAndBrandId {

        @Test
        void 존재하는_좋아요를_반환한다() {
            // arrange
            Brand brand = createActiveBrand("나이키");
            brandLikeRepository.save(BrandLike.create(1L, brand.getId()));

            // act
            Optional<BrandLike> result = brandLikeRepository.findByUserIdAndBrandId(1L, brand.getId());

            // assert
            assertThat(result).isPresent();
        }

        @Test
        void 존재하지_않으면_empty를_반환한다() {
            // act
            Optional<BrandLike> result = brandLikeRepository.findByUserIdAndBrandId(1L, 999L);

            // assert
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("delete 메서드는")
    class Delete {

        @Test
        void 좋아요를_완전히_삭제한다() {
            // arrange
            Brand brand = createActiveBrand("나이키");
            BrandLike saved = brandLikeRepository.save(BrandLike.create(1L, brand.getId()));

            // act
            brandLikeRepository.delete(saved);

            // assert
            Optional<BrandLike> result = brandLikeRepository.findByUserIdAndBrandId(1L, brand.getId());
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findActiveByUserId 메서드는")
    class FindActiveByUserId {

        @Test
        void 비활성_브랜드의_좋아요는_제외한다() {
            // arrange
            Brand active = createActiveBrand("나이키");
            Brand inactive = Brand.create("비활성", "설명");
            inactive.changeStatus(BrandStatus.INACTIVE);
            brandRepository.save(inactive);

            brandLikeRepository.save(BrandLike.create(1L, active.getId()));
            brandLikeRepository.save(BrandLike.create(1L, inactive.getId()));

            // act
            List<BrandLike> result = brandLikeRepository.findActiveByUserId(1L, 0, 20);

            // assert
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getBrandId()).isEqualTo(active.getId());
        }

        @Test
        void 삭제된_브랜드의_좋아요는_제외한다() {
            // arrange
            Brand active = createActiveBrand("나이키");
            Brand deleted = Brand.create("삭제됨", "설명");
            deleted.delete();
            brandRepository.save(deleted);

            brandLikeRepository.save(BrandLike.create(1L, active.getId()));
            brandLikeRepository.save(BrandLike.create(1L, deleted.getId()));

            // act
            List<BrandLike> result = brandLikeRepository.findActiveByUserId(1L, 0, 20);

            // assert
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getBrandId()).isEqualTo(active.getId());
        }

        @Test
        void 최근_좋아요순으로_반환한다() {
            // arrange
            Brand brand1 = createActiveBrand("나이키");
            Brand brand2 = createActiveBrand("아디다스");

            brandLikeRepository.save(BrandLike.create(1L, brand1.getId()));
            brandLikeRepository.save(BrandLike.create(1L, brand2.getId()));

            // act
            List<BrandLike> result = brandLikeRepository.findActiveByUserId(1L, 0, 20);

            // assert
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getBrandId()).isEqualTo(brand2.getId());
        }
    }

    @Nested
    @DisplayName("countActiveByUserId 메서드는")
    class CountActiveByUserId {

        @Test
        void 비활성_브랜드를_제외한_좋아요_수를_반환한다() {
            // arrange
            Brand active = createActiveBrand("나이키");
            Brand inactive = Brand.create("비활성", "설명");
            inactive.changeStatus(BrandStatus.INACTIVE);
            brandRepository.save(inactive);

            brandLikeRepository.save(BrandLike.create(1L, active.getId()));
            brandLikeRepository.save(BrandLike.create(1L, inactive.getId()));

            // act
            long count = brandLikeRepository.countActiveByUserId(1L);

            // assert
            assertThat(count).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("existsByUserIdAndBrandId 메서드는")
    class ExistsByUserIdAndBrandId {

        @Test
        void 존재하면_true를_반환한다() {
            // arrange
            Brand brand = createActiveBrand("나이키");
            brandLikeRepository.save(BrandLike.create(1L, brand.getId()));

            // act & assert
            assertThat(brandLikeRepository.existsByUserIdAndBrandId(1L, brand.getId())).isTrue();
        }

        @Test
        void 존재하지_않으면_false를_반환한다() {
            // act & assert
            assertThat(brandLikeRepository.existsByUserIdAndBrandId(1L, 999L)).isFalse();
        }
    }
}
