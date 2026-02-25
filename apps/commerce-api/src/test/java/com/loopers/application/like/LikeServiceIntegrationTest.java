package com.loopers.application.like;

import com.loopers.domain.like.Like;
import com.loopers.domain.like.LikeRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class LikeServiceIntegrationTest {

    @Autowired
    private LikeService likeService;

    @Autowired
    private LikeRepository likeRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    class 좋아요_등록 {

        @Test
        void 신규_좋아요면_저장되고_생성됨을_반환한다() {
            boolean result = likeService.like(1L, 1L);

            assertThat(result).isTrue();
            Optional<Like> saved = likeRepository.findByUserIdAndProductId(1L, 1L);
            assertThat(saved).isPresent();
            assertThat(saved.get().getUserId()).isEqualTo(1L);
            assertThat(saved.get().getProductId()).isEqualTo(1L);
        }

        @Test
        void 이미_좋아요한_상태이면_생성되지_않는다() {
            likeService.like(1L, 1L);

            boolean result = likeService.like(1L, 1L);

            assertThat(result).isFalse();
        }
    }

    @Nested
    class 좋아요_취소 {

        @Test
        void 좋아요가_존재하면_물리적_삭제된다() {
            likeService.like(1L, 1L);

            boolean result = likeService.unlike(1L, 1L);

            assertThat(result).isTrue();
            Optional<Like> found = likeRepository.findByUserIdAndProductId(1L, 1L);
            assertThat(found).isEmpty();
        }

        @Test
        void 좋아요가_없으면_삭제되지_않는다() {
            boolean result = likeService.unlike(1L, 1L);

            assertThat(result).isFalse();
        }
    }
}
