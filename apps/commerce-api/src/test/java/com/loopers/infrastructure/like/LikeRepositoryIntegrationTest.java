package com.loopers.infrastructure.like;

import com.loopers.domain.like.LikeModel;
import com.loopers.domain.like.LikeRepository;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(MySqlTestContainersConfig.class)
class LikeRepositoryIntegrationTest {

    private static final Long USER_ID = 1L;
    private static final Long PRODUCT_ID = 100L;

    @Autowired
    private LikeRepository likeRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("save 시")
    @Nested
    class Save {

        @DisplayName("저장한 좋아요를 조회할 수 있다.")
        @Test
        void save_shouldPersist() {
            // given
            LikeModel like = LikeModel.create(USER_ID, PRODUCT_ID);

            // when
            LikeModel saved = likeRepository.save(like);

            // then
            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getUserId()).isEqualTo(USER_ID);
            assertThat(saved.getProductId()).isEqualTo(PRODUCT_ID);
            assertThat(likeRepository.existsByUserIdAndProductId(USER_ID, PRODUCT_ID)).isTrue();
        }
    }

    @DisplayName("existsByUserIdAndProductId 시")
    @Nested
    class ExistsByUserIdAndProductId {

        @DisplayName("저장된 좋아요가 있으면 true를 반환한다.")
        @Test
        void existsByUserIdAndProductId_whenExists_shouldReturnTrue() {
            // given
            LikeModel like = LikeModel.create(USER_ID, PRODUCT_ID);
            likeRepository.save(like);

            // when
            boolean exists = likeRepository.existsByUserIdAndProductId(USER_ID, PRODUCT_ID);

            // then
            assertThat(exists).isTrue();
        }

        @DisplayName("저장된 좋아요가 없으면 false를 반환한다.")
        @Test
        void existsByUserIdAndProductId_whenNotExists_shouldReturnFalse() {
            // when
            boolean exists = likeRepository.existsByUserIdAndProductId(999L, 999L);

            // then
            assertThat(exists).isFalse();
        }
    }

    @DisplayName("findByUserIdAndProductId 시")
    @Nested
    class FindByUserIdAndProductId {

        @DisplayName("저장된 좋아요가 있으면 Optional로 반환한다.")
        @Test
        void findByUserIdAndProductId_whenExists_shouldReturnPresent() {
            // given
            LikeModel like = LikeModel.create(USER_ID, PRODUCT_ID);
            LikeModel saved = likeRepository.save(like);

            // when
            Optional<LikeModel> result = likeRepository.findByUserIdAndProductId(USER_ID, PRODUCT_ID);

            // then
            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(saved.getId());
        }

        @DisplayName("저장된 좋아요가 없으면 empty를 반환한다.")
        @Test
        void findByUserIdAndProductId_whenNotExists_shouldReturnEmpty() {
            // when
            Optional<LikeModel> result = likeRepository.findByUserIdAndProductId(999L, 999L);

            // then
            assertThat(result).isEmpty();
        }
    }

    @DisplayName("delete 시")
    @Nested
    class Delete {

        @DisplayName("삭제 후 existsByUserIdAndProductId는 false를 반환한다.")
        @Test
        void delete_shouldRemoveLike() {
            // given
            LikeModel like = LikeModel.create(USER_ID, PRODUCT_ID);
            LikeModel saved = likeRepository.save(like);

            // when
            likeRepository.delete(saved);

            // then
            assertThat(likeRepository.existsByUserIdAndProductId(USER_ID, PRODUCT_ID)).isFalse();
        }
    }

    @DisplayName("findByUserId 시")
    @Nested
    class FindByUserId {

        @DisplayName("해당 사용자의 좋아요 목록을 페이지로 조회할 수 있다.")
        @Test
        void findByUserId_shouldReturnPage() {
            // given
            likeRepository.save(LikeModel.create(USER_ID, PRODUCT_ID));
            likeRepository.save(LikeModel.create(USER_ID, PRODUCT_ID + 1));
            Pageable pageable = PageRequest.of(0, 10);

            // when
            var page = likeRepository.findByUserId(USER_ID, pageable);

            // then
            assertThat(page.getContent()).hasSize(2);
            assertThat(page.getTotalElements()).isEqualTo(2);
        }

        @DisplayName("저장된 좋아요가 없으면 빈 페이지를 반환한다.")
        @Test
        void findByUserId_whenNoLikes_shouldReturnEmpty() {
            // given
            Pageable pageable = PageRequest.of(0, 10);

            // when
            var page = likeRepository.findByUserId(999L, pageable);

            // then
            assertThat(page.getContent()).isEmpty();
        }
    }

    @DisplayName("countByProductId 시")
    @Nested
    class CountByProductId {

        @DisplayName("해당 상품의 좋아요 수를 반환한다.")
        @Test
        void countByProductId_shouldReturnCount() {
            // given
            likeRepository.save(LikeModel.create(USER_ID, PRODUCT_ID));
            likeRepository.save(LikeModel.create(USER_ID + 1, PRODUCT_ID));

            // when
            long count = likeRepository.countByProductId(PRODUCT_ID);

            // then
            assertThat(count).isEqualTo(2);
        }

        @DisplayName("좋아요가 없으면 0을 반환한다.")
        @Test
        void countByProductId_whenNoLikes_shouldReturnZero() {
            // when
            long count = likeRepository.countByProductId(999L);

            // then
            assertThat(count).isZero();
        }
    }
}
