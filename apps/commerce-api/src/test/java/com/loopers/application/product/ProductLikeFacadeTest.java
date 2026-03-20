package com.loopers.application.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.loopers.application.product.dto.ProductLikeResult;
import com.loopers.domain.product.ProductLikeModel;
import com.loopers.domain.product.ProductLikeService;
import com.loopers.domain.product.ProductService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("ProductLikeFacade 단위 테스트")
@ExtendWith(MockitoExtension.class)
class ProductLikeFacadeTest {

    @Mock
    private ProductLikeService productLikeService;

    @Mock
    private ProductService productService;

    @InjectMocks
    private ProductLikeFacade productLikeFacade;

    @DisplayName("좋아요를 등록할 때, ")
    @Nested
    class Like {

        @DisplayName("상품 존재를 검증하고 like를 호출하고 좋아요 수를 증가시킨다.")
        @Test
        void like_validatesProductAndCallsLikeAndIncrementsCount() {
            // act
            productLikeFacade.like(1L, 2L);

            // assert
            verify(productService).validateExists(2L);
            verify(productLikeService).like(1L, 2L);
            verify(productService).incrementLikeCount(2L);
        }
    }

    @DisplayName("좋아요를 취소할 때, ")
    @Nested
    class Unlike {

        @DisplayName("unlike를 호출하고 좋아요 수를 감소시킨다.")
        @Test
        void unlike_callsUnlikeAndDecrementsCount() {
            // act
            productLikeFacade.unlike(1L, 2L);

            // assert
            verify(productLikeService).unlike(1L, 2L);
            verify(productService).decrementLikeCount(2L);
        }
    }

    @DisplayName("좋아요 목록을 조회할 때, ")
    @Nested
    class GetLikesByUserId {

        @DisplayName("사용자의 좋아요 목록을 조회하면, ProductLikeResult 목록을 반환한다.")
        @Test
        void getLikesByUserId_returnsProductLikeResultList() {
            // arrange
            Long userId = 1L;
            List<ProductLikeModel> likes = List.of(
                ProductLikeModel.create(userId, 10L),
                ProductLikeModel.create(userId, 20L)
            );
            when(productLikeService.getLikesByUserId(userId)).thenReturn(likes);
            when(productService.existsById(10L)).thenReturn(true);
            when(productService.existsById(20L)).thenReturn(true);

            // act
            List<ProductLikeResult> result = productLikeFacade.getMyLikedProducts(userId);

            // assert
            assertThat(result).hasSize(2);
        }

        @DisplayName("삭제된 상품의 좋아요는 목록에서 제외된다.")
        @Test
        void getLikesByUserId_excludesDeletedProducts() {
            // arrange
            Long userId = 1L;
            List<ProductLikeModel> likes = List.of(
                ProductLikeModel.create(userId, 10L),
                ProductLikeModel.create(userId, 20L)
            );
            when(productLikeService.getLikesByUserId(userId)).thenReturn(likes);
            when(productService.existsById(10L)).thenReturn(true);
            when(productService.existsById(20L)).thenReturn(false);

            // act
            List<ProductLikeResult> result = productLikeFacade.getMyLikedProducts(userId);

            // assert
            assertThat(result).hasSize(1);
            assertThat(result.get(0).productId()).isEqualTo(10L);
        }
    }
}
