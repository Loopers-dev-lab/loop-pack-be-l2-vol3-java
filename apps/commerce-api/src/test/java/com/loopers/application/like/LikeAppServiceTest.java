package com.loopers.application.like;

import com.loopers.domain.like.Like;
import com.loopers.domain.like.LikeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("LikeAppService 단위 테스트")
class LikeAppServiceTest {

    private LikeAppService likeAppService;
    private LikeRepository likeRepository;

    @BeforeEach
    void setUp() {
        likeRepository = mock(LikeRepository.class);
        likeAppService = new LikeAppService(likeRepository);
    }

    @Nested
    @DisplayName("좋아요 등록")
    class AddLikeTest {

        @Test
        @DisplayName("새로운 좋아요를 등록하면 저장된 Like를 반환한다")
        void addLike_newLike_returnsLike() {
            // given
            Long userId = 1L;
            Long productId = 100L;
            Like savedLike = Like.of(1L, userId, productId);

            given(likeRepository.findByUserIdAndProductId(userId, productId))
                    .willReturn(Optional.empty());
            given(likeRepository.save(any(Like.class))).willReturn(savedLike);

            // when
            Like result = likeAppService.addLike(userId, productId);

            // then
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getUserId()).isEqualTo(userId);
            assertThat(result.getProductId()).isEqualTo(productId);
            verify(likeRepository).save(any(Like.class));
        }

        @Test
        @DisplayName("이미 좋아요가 존재하면 기존 Like를 반환하고 새로 저장하지 않는다")
        void addLike_alreadyExists_returnsExisting() {
            // given
            Long userId = 1L;
            Long productId = 100L;
            Like existingLike = Like.of(1L, userId, productId);

            given(likeRepository.findByUserIdAndProductId(userId, productId))
                    .willReturn(Optional.of(existingLike));

            // when
            Like result = likeAppService.addLike(userId, productId);

            // then
            assertThat(result).isEqualTo(existingLike);
            verify(likeRepository, never()).save(any(Like.class));
        }
    }

    @Nested
    @DisplayName("좋아요 취소")
    class RemoveLikeTest {

        @Test
        @DisplayName("좋아요가 존재하면 삭제한다")
        void removeLike_exists_deletesLike() {
            // given
            Long userId = 1L;
            Long productId = 100L;
            Like existingLike = Like.of(1L, userId, productId);

            given(likeRepository.findByUserIdAndProductId(userId, productId))
                    .willReturn(Optional.of(existingLike));

            // when
            likeAppService.removeLike(userId, productId);

            // then
            verify(likeRepository).delete(existingLike);
        }

        @Test
        @DisplayName("좋아요가 존재하지 않으면 삭제하지 않는다")
        void removeLike_notExists_doesNothing() {
            // given
            Long userId = 1L;
            Long productId = 100L;

            given(likeRepository.findByUserIdAndProductId(userId, productId))
                    .willReturn(Optional.empty());

            // when
            likeAppService.removeLike(userId, productId);

            // then
            verify(likeRepository, never()).delete(any(Like.class));
        }
    }

    @Nested
    @DisplayName("좋아요 수 조회")
    class CountByProductIdTest {

        @Test
        @DisplayName("상품의 좋아요 수를 반환한다")
        void countByProductId_returnsCount() {
            // given
            Long productId = 100L;
            given(likeRepository.countByProductId(productId)).willReturn(42L);

            // when
            long count = likeAppService.countByProductId(productId);

            // then
            assertThat(count).isEqualTo(42L);
        }
    }

    @Nested
    @DisplayName("좋아요 여부 확인")
    class IsLikedByUserTest {

        @Test
        @DisplayName("좋아요가 존재하면 true를 반환한다")
        void isLikedByUser_exists_returnsTrue() {
            // given
            Long userId = 1L;
            Long productId = 100L;
            Like existingLike = Like.of(1L, userId, productId);

            given(likeRepository.findByUserIdAndProductId(userId, productId))
                    .willReturn(Optional.of(existingLike));

            // when
            boolean result = likeAppService.isLikedByUser(userId, productId);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("좋아요가 존재하지 않으면 false를 반환한다")
        void isLikedByUser_notExists_returnsFalse() {
            // given
            Long userId = 1L;
            Long productId = 100L;

            given(likeRepository.findByUserIdAndProductId(userId, productId))
                    .willReturn(Optional.empty());

            // when
            boolean result = likeAppService.isLikedByUser(userId, productId);

            // then
            assertThat(result).isFalse();
        }
    }
}
