package com.loopers.domain.like;

import com.loopers.infrastructure.like.LikeJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
class LikeServiceIntegrationTest {

    @Autowired
    private LikeService likeService;

    @Autowired
    private LikeJpaRepository likeJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("좋아요를 등록할 때,")
    @Nested
    class LikeRegister {

        @DisplayName("처음 좋아요하면, DB에 저장된다.")
        @Test
        void savesLike_whenFirstTime() {
            // arrange
            Long userId = 1L;
            Long productId = 100L;

            // act
            Like result = likeService.like(userId, productId);

            // assert
            assertAll(
                () -> assertThat(result.getId()).isNotNull(),
                () -> assertThat(result.getUserId()).isEqualTo(1L),
                () -> assertThat(result.getProductId()).isEqualTo(100L)
            );

            Optional<Like> saved = likeJpaRepository.findByUserIdAndProductId(userId, productId);
            assertThat(saved).isPresent();
        }

        @DisplayName("이미 좋아요한 상태에서 다시 등록하면, 예외 없이 기존 Like를 반환한다.")
        @Test
        void returnsExistingLike_whenAlreadyLiked() {
            // arrange
            Long userId = 1L;
            Long productId = 100L;
            Like first = likeService.like(userId, productId);

            // act
            Like second = likeService.like(userId, productId);

            // assert
            assertThat(second.getId()).isEqualTo(first.getId());
            assertThat(likeJpaRepository.countByProductId(productId)).isEqualTo(1);
        }
    }

    @DisplayName("좋아요를 취소할 때,")
    @Nested
    class Unlike {

        @DisplayName("좋아요가 존재하면, DB에서 삭제된다.")
        @Test
        void deletesLike_whenExists() {
            // arrange
            Long userId = 1L;
            Long productId = 100L;
            likeService.like(userId, productId);

            // act
            likeService.unlike(userId, productId);

            // assert
            Optional<Like> found = likeJpaRepository.findByUserIdAndProductId(userId, productId);
            assertThat(found).isEmpty();
        }
    }

    @DisplayName("상품별 좋아요 수를 조회할 때,")
    @Nested
    class CountByProduct {

        @DisplayName("여러 사용자가 좋아요하면, 정확한 수를 반환한다.")
        @Test
        void returnsCorrectCount_whenMultipleUsersLiked() {
            // arrange
            Long productId = 100L;
            likeService.like(1L, productId);
            likeService.like(2L, productId);
            likeService.like(3L, productId);

            // act
            long count = likeService.countByProductId(productId);

            // assert
            assertThat(count).isEqualTo(3);
        }
    }
}
