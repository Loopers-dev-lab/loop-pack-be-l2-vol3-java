package com.loopers.domain.like;

import com.loopers.application.like.LikeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class LikeServiceTest {

    @InjectMocks
    private LikeService likeService;

    @Mock
    private LikeRepository likeRepository;

    @DisplayName("좋아요 저장")
    @Nested
    class Save {

        @DisplayName("좋아요를 저장하고 반환한다")
        @Test
        void savesAndReturns() {
            // arrange
            Long userId = 1L;
            Long productId = 100L;
            LikeModel like = new LikeModel(userId, productId);
            given(likeRepository.save(any(LikeModel.class))).willReturn(like);
            // act
            LikeModel result = likeService.save(like);
            // assert
            assertThat(result.userId()).isEqualTo(userId);
            then(likeRepository).should().save(like);
        }
    }

    @DisplayName("좋아요 조회")
    @Nested
    class Find {

        @DisplayName("userId와 productId로 좋아요를 조회한다")
        @Test
        void findsByUserIdAndProductId() {
            // arrange
            Long userId = 1L;
            Long productId = 100L;
            LikeModel like = new LikeModel(userId, productId);
            given(likeRepository.findByUserIdAndProductId(userId, productId))
                .willReturn(Optional.of(like));
            // act
            Optional<LikeModel> result = likeService.findByUserIdAndProductId(userId, productId);
            // assert
            assertThat(result).isPresent();
            assertThat(result.get().productId()).isEqualTo(productId);
        }
    }

    @DisplayName("내 좋아요 목록 조회")
    @Nested
    class GetMyLikes {

        @DisplayName("회원의 좋아요 목록을 페이징으로 반환한다")
        @Test
        void returnsPagedLikes() {
            // arrange
            Long userId = 1L;
            Pageable pageable = PageRequest.of(0, 10);
            List<LikeModel> likes = List.of(new LikeModel(userId, 1L), new LikeModel(userId, 2L));
            given(likeRepository.findActiveLikesWithActiveProduct(userId, pageable)).willReturn(new PageImpl<>(likes));
            // act
            Page<LikeModel> result = likeService.getMyLikes(userId, pageable);
            // assert
            assertThat(result.getContent()).hasSize(2);
        }
    }
}
