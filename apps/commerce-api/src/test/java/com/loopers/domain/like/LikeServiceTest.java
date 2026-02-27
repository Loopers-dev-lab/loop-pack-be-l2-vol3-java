package com.loopers.domain.like;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
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
            ProductModel product = createTestProduct();
            when(productService.findById("product-1")).thenReturn(product);
            when(likeRepository.findById(new LikeId("user-1", "product-1")))
                    .thenReturn(Optional.empty());
            when(likeRepository.save(any(LikeModel.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            likeService.addLike("user-1", "product-1");

            verify(likeRepository).save(any(LikeModel.class));
        }

        @Test
        @DisplayName("이미 좋아요한 상품에 다시 좋아요하면 save가 호출되지 않는다 (멱등)")
        void addLike_AlreadyLiked_ShouldBeIdempotent() {
            ProductModel product = createTestProduct();
            when(productService.findById("product-1")).thenReturn(product);
            LikeModel existingLike = LikeModel.create("user-1", "product-1");
            when(likeRepository.findById(new LikeId("user-1", "product-1")))
                    .thenReturn(Optional.of(existingLike));

            likeService.addLike("user-1", "product-1");

            verify(likeRepository, never()).save(any());
        }

        @Test
        @DisplayName("존재하지 않는 상품에 좋아요 시 LIKE_PRODUCT_NOT_FOUND 예외가 발생한다")
        void addLike_ProductNotFound_ShouldThrow() {
            when(productService.findById("nonexistent"))
                    .thenThrow(new CoreException(ErrorType.PRODUCT_NOT_FOUND));

            assertThatThrownBy(() -> likeService.addLike("user-1", "nonexistent"))
                    .isInstanceOf(CoreException.class)
                    .satisfies(ex -> assertThat(((CoreException) ex).getErrorType())
                            .isEqualTo(ErrorType.LIKE_PRODUCT_NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("좋아요 취소")
    class RemoveLikeTests {

        @Test
        @DisplayName("기존 좋아요를 삭제한다")
        void removeLike_Existing_ShouldDelete() {
            LikeModel like = LikeModel.create("user-1", "product-1");
            when(likeRepository.findById(new LikeId("user-1", "product-1")))
                    .thenReturn(Optional.of(like));

            likeService.removeLike("user-1", "product-1");

            verify(likeRepository).delete(like);
        }

        @Test
        @DisplayName("좋아요하지 않은 상품 취소 시 에러 없이 통과한다 (멱등)")
        void removeLike_NotLiked_ShouldBeIdempotent() {
            when(likeRepository.findById(new LikeId("user-1", "product-1")))
                    .thenReturn(Optional.empty());

            assertThatCode(() -> likeService.removeLike("user-1", "product-1"))
                    .doesNotThrowAnyException();
            verify(likeRepository, never()).delete(any());
        }
    }

    @Nested
    @DisplayName("좋아요 조회")
    class QueryTests {

        @Test
        @DisplayName("사용자의 좋아요 목록이 LikeInfo 리스트로 반환된다")
        void getMyLikes_ShouldReturnLikeListForUser() {
            LikeModel like1 = LikeModel.create("user-1", "product-1");
            LikeModel like2 = LikeModel.create("user-1", "product-2");
            when(likeRepository.findAllByUserId("user-1")).thenReturn(List.of(like1, like2));

            List<LikeModel> result = likeService.getMyLikes("user-1");

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("좋아요 카운트가 올바르게 반환된다")
        void countByProductId_ShouldReturnCount() {
            when(likeRepository.countByProductId("product-1")).thenReturn(42L);

            long result = likeService.countByProductId("product-1");

            assertThat(result).isEqualTo(42L);
        }

        @Test
        @DisplayName("여러 상품의 좋아요 수가 배치로 올바르게 반환된다")
        void countByProductIds_ShouldReturnBatchCounts() {
            List<String> productIds = List.of("product-1", "product-2");
            when(likeRepository.countByProductIds(productIds))
                    .thenReturn(Map.of("product-1", 5L, "product-2", 3L));

            Map<String, Long> result = likeService.countByProductIds(productIds);

            assertThat(result).hasSize(2);
            assertThat(result.get("product-1")).isEqualTo(5L);
            assertThat(result.get("product-2")).isEqualTo(3L);
        }
    }

    private ProductModel createTestProduct() {
        return ProductModel.create("테스트상품", "brand-id", BigDecimal.valueOf(10000),
                "설명", null, null, null, null, null, null);
    }
}
