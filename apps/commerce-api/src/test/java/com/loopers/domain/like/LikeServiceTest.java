package com.loopers.domain.like;

import com.loopers.domain.outbox.TransactionalOutboxWriter;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductStatsRepository;
import com.loopers.domain.product.Money;
import com.loopers.domain.product.StockQuantity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LikeServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long PRODUCT_ID = 100L;

    @Mock
    private LikeRepository likeRepository;

    @Mock
    private ProductService productService;

    @Mock
    private ProductStatsRepository productStatsRepository;

    @Mock
    private TransactionalOutboxWriter transactionalOutboxWriter;

    @InjectMocks
    private LikeService likeService;

    @BeforeEach
    void setUpSelf() {
        ReflectionTestUtils.setField(likeService, "self", likeService);
    }

    @DisplayName("addLike 시")
    @Nested
    class AddLike {

        @DisplayName("userId 또는 productId가 null이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void addLike_whenUserIdOrProductIdNull_shouldThrowBadRequest() {
            CoreException exUserId = assertThrows(CoreException.class, () -> likeService.addLike(null, PRODUCT_ID));
            assertThat(exUserId.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            assertThat(exUserId.getMessage()).contains("필수");

            CoreException exProductId = assertThrows(CoreException.class, () -> likeService.addLike(USER_ID, null));
            assertThat(exProductId.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            assertThat(exProductId.getMessage()).contains("필수");
            verifyNoInteractions(transactionalOutboxWriter);
        }

        @DisplayName("상품이 없으면 NOT_FOUND 예외가 발생한다.")
        @Test
        void addLike_whenProductNotFound_shouldThrowNotFound() {
            // given
            when(productService.findByIdAndNotDeleted(PRODUCT_ID)).thenReturn(Optional.empty());

            // when & then
            CoreException ex = assertThrows(CoreException.class, () -> likeService.addLike(USER_ID, PRODUCT_ID));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
            verifyNoInteractions(transactionalOutboxWriter);
        }

        @DisplayName("이미 좋아요한 상품이면 CONFLICT 예외가 발생한다.")
        @Test
        void addLike_whenAlreadyExists_shouldThrowConflict() {
            // given
            when(productService.findByIdAndNotDeleted(PRODUCT_ID)).thenReturn(Optional
                    .of(ProductModel.create(1L, "상품", Money.of(java.math.BigDecimal.ONE), StockQuantity.of(1))));
            when(likeRepository.existsByUserIdAndProductId(USER_ID, PRODUCT_ID)).thenReturn(true);

            // when & then
            CoreException ex = assertThrows(CoreException.class, () -> likeService.addLike(USER_ID, PRODUCT_ID));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.CONFLICT);
            verifyNoInteractions(transactionalOutboxWriter);
        }

        @DisplayName("유효한 요청이면 저장 후 Like를 반환한다.")
        @Test
        void addLike_whenValid_shouldSaveAndReturn() {
            // given
            when(productService.findByIdAndNotDeleted(PRODUCT_ID)).thenReturn(Optional
                    .of(ProductModel.create(1L, "상품", Money.of(java.math.BigDecimal.ONE), StockQuantity.of(1))));
            when(likeRepository.existsByUserIdAndProductId(USER_ID, PRODUCT_ID)).thenReturn(false);
            LikeModel like = LikeModel.create(USER_ID, PRODUCT_ID);
            when(likeRepository.save(any(LikeModel.class))).thenReturn(like);

            // when
            LikeModel result = likeService.addLike(USER_ID, PRODUCT_ID);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getUserId()).isEqualTo(USER_ID);
            assertThat(result.getProductId()).isEqualTo(PRODUCT_ID);
            verify(likeRepository).save(any(LikeModel.class));
            verify(productStatsRepository).createIfAbsent(PRODUCT_ID);
            verify(productStatsRepository).incrementLikeCount(PRODUCT_ID);
            verify(transactionalOutboxWriter).record(
                    eq("product-events"),
                    eq(String.valueOf(PRODUCT_ID)),
                    eq("PRODUCT_LIKE_CHANGED"),
                    argThat(m -> PRODUCT_ID.equals(m.get("productId"))
                            && USER_ID.equals(m.get("userId"))
                            && "LIKED".equals(m.get("action"))));
        }
    }

    @DisplayName("removeLike 시")
    @Nested
    class RemoveLike {

        @DisplayName("userId 또는 productId가 null이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void removeLike_whenUserIdOrProductIdNull_shouldThrowBadRequest() {
            CoreException exUserId = assertThrows(CoreException.class, () -> likeService.removeLike(null, PRODUCT_ID));
            assertThat(exUserId.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            assertThat(exUserId.getMessage()).contains("필수");

            CoreException exProductId = assertThrows(CoreException.class, () -> likeService.removeLike(USER_ID, null));
            assertThat(exProductId.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            assertThat(exProductId.getMessage()).contains("필수");
            verifyNoInteractions(transactionalOutboxWriter);
        }

        @DisplayName("좋아요가 없으면 NOT_FOUND 예외가 발생한다.")
        @Test
        void removeLike_whenNotFound_shouldThrowNotFound() {
            // given
            when(likeRepository.findByUserIdAndProductId(USER_ID, PRODUCT_ID)).thenReturn(Optional.empty());

            // when & then
            CoreException ex = assertThrows(CoreException.class, () -> likeService.removeLike(USER_ID, PRODUCT_ID));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
            verifyNoInteractions(transactionalOutboxWriter);
        }

        @DisplayName("존재하는 좋아요면 삭제한다.")
        @Test
        void removeLike_whenExists_shouldDelete() {
            // given
            LikeModel like = LikeModel.create(USER_ID, PRODUCT_ID);
            when(likeRepository.findByUserIdAndProductId(USER_ID, PRODUCT_ID)).thenReturn(Optional.of(like));

            // when
            likeService.removeLike(USER_ID, PRODUCT_ID);

            // then
            verify(likeRepository).delete(like);
            verify(productStatsRepository).decrementLikeCount(PRODUCT_ID);
            verify(transactionalOutboxWriter).record(
                    eq("product-events"),
                    eq(String.valueOf(PRODUCT_ID)),
                    eq("PRODUCT_LIKE_CHANGED"),
                    argThat(m -> PRODUCT_ID.equals(m.get("productId"))
                            && USER_ID.equals(m.get("userId"))
                            && "UNLIKED".equals(m.get("action"))));
        }
    }

    @DisplayName("findLikesByUserId 시")
    @Nested
    class FindLikesByUserId {

        @DisplayName("해당 사용자의 좋아요 목록 페이지를 반환한다.")
        @Test
        void findLikesByUserId_shouldReturnPage() {
            // given
            Pageable pageable = PageRequest.of(0, 10);
            Page<LikeModel> expected = new PageImpl<>(List.of(LikeModel.create(USER_ID, PRODUCT_ID)));
            when(likeRepository.findByUserId(USER_ID, pageable)).thenReturn(expected);

            // when
            Page<LikeModel> result = likeService.findLikesByUserId(USER_ID, pageable);

            // then
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getProductId()).isEqualTo(PRODUCT_ID);
        }
    }
}
