package com.loopers.application.like;

import com.loopers.domain.like.InMemoryLikeRepository;
import com.loopers.domain.like.Like;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class LikeApplicationServiceTest {

    private InMemoryLikeRepository likeRepository;
    private LikeApplicationService likeService;

    @BeforeEach
    void setUp() {
        likeRepository = new InMemoryLikeRepository();
        likeService = new LikeApplicationService(likeRepository);
    }

    @DisplayName("좋아요 등록 시, ")
    @Nested
    class Register {
        @DisplayName("이미 좋아요한 상품이면 예외가 발생한다.")
        @Test
        void throwsException_whenAlreadyLiked() {
            // arrange
            likeRepository.save(Like.create(1L, 100L));

            // act & assert
            assertThatThrownBy(() -> likeService.register(1L, 100L))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.ALREADY_LIKED));
        }
    }

    @DisplayName("좋아요 취소 시, ")
    @Nested
    class Cancel {

        @DisplayName("좋아요가 존재하면 삭제 후 true를 반환한다.")
        @Test
        void returnsTrue_whenLikeExists() {
            // arrange
            likeRepository.save(Like.create(1L, 100L));

            // act
            boolean result = likeService.cancel(1L, 100L);

            // assert
            assertThat(result).isTrue();
        }

        @DisplayName("좋아요가 존재하지 않으면 false를 반환한다.")
        @Test
        void returnsFalse_whenLikeDoesNotExist() {
            // act
            boolean result = likeService.cancel(1L, 100L);

            // assert
            assertThat(result).isFalse();
        }
    }
}
