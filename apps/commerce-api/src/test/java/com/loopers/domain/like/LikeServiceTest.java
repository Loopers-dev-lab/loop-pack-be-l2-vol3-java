package com.loopers.domain.like;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("LikeService 도메인 서비스 테스트")
class LikeServiceTest {

    @Mock
    LikeRepository likeRepository;

    @Mock
    ProductService productService;

    @InjectMocks
    LikeService likeService;

    @Nested
    @DisplayName("좋아요 등록")
    class AddLikeTests {

        @Test
        @DisplayName("처음 좋아요 시 save가 호출된다")
        void addLike_NewLike_ShouldCreate() {
            ProductModel product = mock(ProductModel.class);
            when(productService.findByIdWithLock(1L)).thenReturn(product);
            when(likeRepository.findById(new LikeId(1L, 1L)))
                    .thenReturn(Optional.empty());
            when(likeRepository.save(any(LikeModel.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            likeService.addLike(1L, 1L);

            verify(productService).findByIdWithLock(1L);
            verify(likeRepository).save(any(LikeModel.class));
            verify(productService).incrementLikeCount(1L);
        }

        @Test
        @DisplayName("이미 좋아요한 상품에 다시 좋아요하면 save가 호출되지 않는다 (멱등)")
        void addLike_AlreadyLiked_ShouldBeIdempotent() {
            ProductModel product = mock(ProductModel.class);
            when(productService.findByIdWithLock(1L)).thenReturn(product);
            LikeModel existingLike = LikeModel.create(1L, 1L);
            when(likeRepository.findById(new LikeId(1L, 1L)))
                    .thenReturn(Optional.of(existingLike));

            likeService.addLike(1L, 1L);

            verify(productService).findByIdWithLock(1L);
            verify(likeRepository, never()).save(any());
            verify(productService, never()).incrementLikeCount(anyLong());
        }
    }

    @Nested
    @DisplayName("좋아요 취소")
    class RemoveLikeTests {

        @Test
        @DisplayName("기존 좋아요를 삭제한다")
        void removeLike_Existing_ShouldDelete() {
            ProductModel product = mock(ProductModel.class);
            when(productService.findByIdWithLock(1L)).thenReturn(product);
            LikeModel like = LikeModel.create(1L, 1L);
            when(likeRepository.findById(new LikeId(1L, 1L)))
                    .thenReturn(Optional.of(like));

            likeService.removeLike(1L, 1L);

            verify(productService).findByIdWithLock(1L);
            verify(likeRepository).delete(like);
            verify(productService).decrementLikeCount(1L);
        }

        @Test
        @DisplayName("좋아요하지 않은 상품 취소 시 에러 없이 통과한다 (멱등)")
        void removeLike_NotLiked_ShouldBeIdempotent() {
            ProductModel product = mock(ProductModel.class);
            when(productService.findByIdWithLock(1L)).thenReturn(product);
            when(likeRepository.findById(new LikeId(1L, 1L)))
                    .thenReturn(Optional.empty());

            assertThatCode(() -> likeService.removeLike(1L, 1L))
                    .doesNotThrowAnyException();
            verify(likeRepository, never()).delete(any());
            verify(productService, never()).decrementLikeCount(anyLong());
        }
    }

    @Nested
    @DisplayName("좋아요 조회")
    class QueryTests {

        @Test
        @DisplayName("사용자의 좋아요 목록이 LikeModel 리스트로 반환된다")
        void getMyLikes_ShouldReturnLikeListForUser() {
            LikeModel like1 = LikeModel.create(1L, 1L);
            LikeModel like2 = LikeModel.create(1L, 2L);
            when(likeRepository.findAllByUserId(1L)).thenReturn(List.of(like1, like2));

            List<LikeModel> result = likeService.getMyLikes(1L);

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("좋아요 카운트가 올바르게 반환된다")
        void countByProductId_ShouldReturnCount() {
            when(likeRepository.countByProductId(1L)).thenReturn(42L);

            long result = likeService.countByProductId(1L);

            assertThat(result).isEqualTo(42L);
        }

        @Test
        @DisplayName("여러 상품의 좋아요 수가 배치로 올바르게 반환된다")
        void countByProductIds_ShouldReturnBatchCounts() {
            List<Long> productIds = List.of(1L, 2L);
            when(likeRepository.countByProductIds(productIds))
                    .thenReturn(Map.of(1L, 5L, 2L, 3L));

            Map<Long, Long> result = likeService.countByProductIds(productIds);

            assertThat(result).hasSize(2);
            assertThat(result.get(1L)).isEqualTo(5L);
            assertThat(result.get(2L)).isEqualTo(3L);
        }
    }
}
