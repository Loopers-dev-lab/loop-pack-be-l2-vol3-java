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

public class LikeServiceTest {

    private InMemoryLikeRepository likeRepository;
    private LikeService likeService;

    @BeforeEach
    void setUp() {
        likeRepository = new InMemoryLikeRepository();
        likeService = new LikeService(likeRepository);
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
}
