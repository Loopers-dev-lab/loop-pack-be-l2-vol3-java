package com.loopers.domain.productlike;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductLikeService 단위 테스트")
class ProductLikeServiceTest {

    @Mock
    private ProductLikeRepository productLikeRepository;

    @InjectMocks
    private ProductLikeService productLikeService;

    @Nested
    @DisplayName("좋아요 등록")
    class RegisterLike {

        @Test
        @DisplayName("성공: 유효한 데이터로 좋아요를 등록한다")
        void registerLike_Success() {
            // Given
            Long userId = 1L;
            Long productId = 1L;

            ProductLike productLike = ProductLike.create(userId, productId);

            given(productLikeRepository.existsByUserIdAndProductId(userId, productId)).willReturn(false);
            given(productLikeRepository.save(any(ProductLike.class))).willReturn(productLike);

            // When
            ProductLike result = productLikeService.registerLike(userId, productId);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getUserId()).isEqualTo(userId);
            assertThat(result.getProductId()).isEqualTo(productId);
            verify(productLikeRepository).existsByUserIdAndProductId(userId, productId);
            verify(productLikeRepository).save(any(ProductLike.class));
        }

        @Test
        @DisplayName("실패: 이미 좋아요한 상품이면 CONFLICT 예외를 던진다")
        void registerLike_AlreadyLiked() {
            // Given
            Long userId = 1L;
            Long productId = 1L;

            given(productLikeRepository.existsByUserIdAndProductId(userId, productId)).willReturn(true);

            // When & Then
            assertThatThrownBy(() -> productLikeService.registerLike(userId, productId))
                    .isInstanceOf(CoreException.class)
                    .hasFieldOrPropertyWithValue("errorType", ErrorType.CONFLICT)
                    .hasMessage("이미 좋아요한 상품입니다.");
        }
    }

    @Nested
    @DisplayName("좋아요 취소")
    class CancelLike {

        @Test
        @DisplayName("성공: 좋아요를 취소한다")
        void cancelLike_Success() {
            // Given
            Long userId = 1L;
            Long productId = 1L;
            
            ProductLike productLike = ProductLike.create(userId, productId);

            given(productLikeRepository.findByUserIdAndProductId(userId, productId)).willReturn(Optional.of(productLike));

            // When
            productLikeService.cancelLike(userId, productId);

            // Then
            verify(productLikeRepository).findByUserIdAndProductId(userId, productId);
            verify(productLikeRepository).delete(productLike);
        }

        @Test
        @DisplayName("실패: 좋아요하지 않은 상품이면 NOT_FOUND 예외를 던진다")
        void cancelLike_NotLiked() {
            // Given
            Long userId = 1L;
            Long productId = 1L;

            given(productLikeRepository.findByUserIdAndProductId(userId, productId)).willReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> productLikeService.cancelLike(userId, productId))
                    .isInstanceOf(CoreException.class)
                    .hasFieldOrPropertyWithValue("errorType", ErrorType.NOT_FOUND)
                    .hasMessage("좋아요를 찾을 수 없습니다.");
        }
    }
}
