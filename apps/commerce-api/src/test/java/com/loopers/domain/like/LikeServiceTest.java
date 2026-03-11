package com.loopers.domain.like;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.loopers.support.error.CoreException;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LikeServiceTest {

    @InjectMocks
    private LikeService likeService;

    @Mock
    private LikeRepository likeRepository;

    @Nested
    @DisplayName("좋아요 등록")
    class LikeTest {

        @Test
        @DisplayName("좋아요를 등록한다")
        void success_like() {
            Long productId = 1L;
            Long userId = 2L;
            Like like = Like.create(1L, productId, userId);

            given(likeRepository.existByUniqueId(productId, userId)).willReturn(false);
            given(likeRepository.save(any(Like.class))).willReturn(like);

            Like result = likeService.like(productId, userId);

            assertThat(result.getRefProductId()).isEqualTo(productId);
            assertThat(result.getRefUserId()).isEqualTo(userId);
        }

        @Test
        @DisplayName("이미 좋아요를 누른 상품이면, 예외를 던진다")
        void fail_when_already_liked() {
            Long productId = 1L;
            Long userId = 2L;

            given(likeRepository.existByUniqueId(productId, userId)).willReturn(true);

            assertThatThrownBy(() -> likeService.like(productId, userId))
                .isInstanceOf(CoreException.class)
                .hasMessage("이미 좋아요를 누른 상품입니다");
        }
    }

    @Nested
    @DisplayName("좋아요 취소")
    class UnlikeTest {

        @Test
        @DisplayName("좋아요를 취소한다")
        void success_unlike() {
            Long productId = 1L;
            Long userId = 2L;
            Like like = Like.create(1L, productId, userId);

            given(likeRepository.findByUniqueId(productId, userId)).willReturn(Optional.of(like));

            likeService.unlike(productId, userId);

            then(likeRepository).should().delete(like);
        }

        @Test
        @DisplayName("좋아요가 존재하지 않으면, 예외를 던진다")
        void fail_when_not_liked() {
            Long productId = 1L;
            Long userId = 2L;

            given(likeRepository.findByUniqueId(productId, userId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> likeService.unlike(productId, userId))
                .isInstanceOf(CoreException.class)
                .hasMessage("좋아요 객체를 찾을 수 없습니다");
        }
    }
}
