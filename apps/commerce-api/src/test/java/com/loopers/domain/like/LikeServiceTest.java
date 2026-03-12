package com.loopers.domain.like;

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

    @DisplayName("좋아요 등록")
    @Nested
    class Register {

        @DisplayName("좋아요가 없으면 새로 생성하고 true를 반환한다")
        @Test
        void returnsTrueWhenNewLike() {
            // arrange
            Long memberId = 1L;
            Long productId = 100L;
            given(likeRepository.findByMemberIdAndProductId(memberId, productId)).willReturn(Optional.empty());
            given(likeRepository.save(any(LikeModel.class))).willReturn(new LikeModel(memberId, productId));
            // act
            boolean result = likeService.register(memberId, productId);
            // assert
            assertThat(result).isTrue();
            then(likeRepository).should().save(any(LikeModel.class));
        }

        @DisplayName("이미 좋아요가 존재하면 false를 반환한다")
        @Test
        void returnsFalseWhenAlreadyExists() {
            // arrange
            Long memberId = 1L;
            Long productId = 100L;
            given(likeRepository.findByMemberIdAndProductId(memberId, productId))
                .willReturn(Optional.of(new LikeModel(memberId, productId)));
            // act
            boolean result = likeService.register(memberId, productId);
            // assert
            assertThat(result).isFalse();
        }
    }

    @DisplayName("좋아요 취소")
    @Nested
    class Cancel {

        @DisplayName("좋아요가 존재하면 삭제하고 true를 반환한다")
        @Test
        void returnsTrueWhenCancelled() {
            // arrange
            Long memberId = 1L;
            Long productId = 100L;
            LikeModel like = new LikeModel(memberId, productId);
            given(likeRepository.findByMemberIdAndProductId(memberId, productId)).willReturn(Optional.of(like));
            // act
            boolean result = likeService.cancel(memberId, productId);
            // assert
            assertThat(result).isTrue();
            then(likeRepository).should().delete(like);
        }

        @DisplayName("좋아요가 없으면 false를 반환한다")
        @Test
        void returnsFalseWhenNotExists() {
            // arrange
            Long memberId = 1L;
            Long productId = 100L;
            given(likeRepository.findByMemberIdAndProductId(memberId, productId)).willReturn(Optional.empty());
            // act
            boolean result = likeService.cancel(memberId, productId);
            // assert
            assertThat(result).isFalse();
        }
    }

    @DisplayName("내 좋아요 목록 조회")
    @Nested
    class GetMyLikes {

        @DisplayName("회원의 좋아요 목록을 페이징으로 반환한다")
        @Test
        void returnsPagedLikes() {
            // arrange
            Long memberId = 1L;
            Pageable pageable = PageRequest.of(0, 10);
            List<LikeModel> likes = List.of(new LikeModel(memberId, 1L), new LikeModel(memberId, 2L));
            given(likeRepository.findAllByMemberId(memberId, pageable)).willReturn(new PageImpl<>(likes));
            // act
            Page<LikeModel> result = likeService.getMyLikes(memberId, pageable);
            // assert
            assertThat(result.getContent()).hasSize(2);
        }
    }
}
