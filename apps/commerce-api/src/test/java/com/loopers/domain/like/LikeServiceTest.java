package com.loopers.domain.like;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.LikeErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class LikeServiceTest {

    private ProductLikeRepository productLikeRepository;
    private LikeService likeService;

    @BeforeEach
    void setUp() {
        productLikeRepository = Mockito.mock(ProductLikeRepository.class);
        likeService = new LikeService(productLikeRepository);
    }

    @DisplayName("좋아요를 생성할 때,")
    @Nested
    class 생성 {

        @Test
        void 이미_좋아요한_상품이면_예외가_발생한다() {
            // arrange
            when(productLikeRepository.existsByUserIdAndProductId(1L, 100L)).thenReturn(true);

            // act & assert
            assertThatThrownBy(() -> likeService.like(1L, 100L))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(LikeErrorType.ALREADY_LIKED);
        }

        @Test
        void 이미_좋아요_시_save가_호출되지_않는다() {
            // arrange
            when(productLikeRepository.existsByUserIdAndProductId(1L, 100L)).thenReturn(true);

            // act
            try { likeService.like(1L, 100L); } catch (CoreException ignored) {}

            // assert
            verify(productLikeRepository, never()).save(any(ProductLike.class));
        }

        @Test
        void 좋아요가_없으면_정상적으로_생성된다() {
            // arrange
            when(productLikeRepository.existsByUserIdAndProductId(1L, 100L)).thenReturn(false);
            when(productLikeRepository.save(any(ProductLike.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // act
            ProductLike result = likeService.like(1L, 100L);

            // assert
            assertThat(result)
                    .extracting(ProductLike::getUserId, ProductLike::getProductId)
                    .containsExactly(1L, 100L);
        }

        @Test
        void 생성_시_save가_호출된다() {
            // arrange
            when(productLikeRepository.existsByUserIdAndProductId(1L, 100L)).thenReturn(false);
            when(productLikeRepository.save(any(ProductLike.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // act
            likeService.like(1L, 100L);

            // assert
            verify(productLikeRepository).save(any(ProductLike.class));
        }
    }

    @DisplayName("좋아요를 취소할 때,")
    @Nested
    class 취소 {

        @Test
        void 좋아요가_존재하지_않으면_예외가_발생한다() {
            // arrange
            when(productLikeRepository.findByUserIdAndProductId(1L, 100L)).thenReturn(Optional.empty());

            // act & assert
            assertThatThrownBy(() -> likeService.unlike(1L, 100L))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(LikeErrorType.LIKE_NOT_FOUND);
        }

        @Test
        void 좋아요가_존재하면_delete가_호출된다() {
            // arrange
            ProductLike productLike = ProductLike.create(1L, 100L);
            when(productLikeRepository.findByUserIdAndProductId(1L, 100L)).thenReturn(Optional.of(productLike));

            // act
            likeService.unlike(1L, 100L);

            // assert
            verify(productLikeRepository).delete(productLike);
        }
    }
}
