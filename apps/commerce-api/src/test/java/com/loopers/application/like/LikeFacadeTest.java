package com.loopers.application.like;

import com.loopers.domain.like.Like;
import com.loopers.domain.like.LikeService;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.user.User;
import com.loopers.domain.user.UserRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LikeFacadeTest {

    private LikeRepository likeRepository;
    private ProductRepository productRepository;
    private UserRepository userRepository;
    private BrandRepository brandRepository;
    private LikeFacade likeFacade;

    private final Long userId = 1L;
    private final Long productId = 2L;

    @BeforeEach
    void setUp() {
        likeRepository = mock(LikeRepository.class);
        productRepository = mock(ProductRepository.class);
        userRepository = mock(UserRepository.class);
        brandRepository = mock(BrandRepository.class);
        likeFacade = new LikeFacade(
            likeRepository,
            productRepository,
            userRepository,
            brandRepository,
            new LikeService()
        );
    }

    // ── like ──────────────────────────────────────────────────────────────────

    @DisplayName("like: 유저와 상품이 존재하고 좋아요가 없으면 좋아요 등록에 성공한다")
    @Test
    void like_success() {
        User user = mock(User.class);
        Product product = Product.create(1L, "신발", "설명", BigDecimal.valueOf(50000), 10);
        Like like = Like.create(userId, productId);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(likeRepository.findByUserIdAndProductIdIncludingDeleted(userId, productId)).thenReturn(Optional.empty());
        when(likeRepository.save(any(Like.class))).thenReturn(like);
        when(productRepository.save(product)).thenReturn(product);

        LikeDto.LikeInfo result = likeFacade.like(userId, productId);

        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.productId()).isEqualTo(productId);
        assertThat(product.getLikeCount()).isEqualTo(1);
        verify(likeRepository).save(any(Like.class));
        verify(productRepository).save(product);
    }

    @DisplayName("like: 존재하지 않는 유저면 예외가 발생한다")
    @Test
    void like_fail_userNotFound() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> likeFacade.like(userId, productId))
            .isInstanceOf(CoreException.class)
            .satisfies(e -> assertThat(((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.USER_NOT_FOUND));

        verify(likeRepository, never()).save(any());
        verify(productRepository, never()).save(any());
    }

    @DisplayName("like: 존재하지 않는 상품이면 예외가 발생한다")
    @Test
    void like_fail_productNotFound() {
        User user = mock(User.class);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(productRepository.findById(productId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> likeFacade.like(userId, productId))
            .isInstanceOf(CoreException.class)
            .satisfies(e -> assertThat(((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.PRODUCT_NOT_FOUND));

        verify(likeRepository, never()).save(any());
    }

    @DisplayName("like: 이미 좋아요한 상품이면 멱등하게 성공한다")
    @Test
    void like_idempotent_whenAlreadyLiked() {
        User user = mock(User.class);
        Product product = Product.create(1L, "신발", "설명", BigDecimal.valueOf(50000), 10);
        Like existingLike = Like.create(userId, productId);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(likeRepository.findByUserIdAndProductIdIncludingDeleted(userId, productId))
            .thenReturn(Optional.of(existingLike));

        LikeDto.LikeInfo result = likeFacade.like(userId, productId);

        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.productId()).isEqualTo(productId);
        assertThat(product.getLikeCount()).isZero();
        verify(likeRepository, never()).save(any());
        verify(productRepository, never()).save(any());
    }

    @DisplayName("like: soft-delete 된 좋아요가 있으면 복구 후 likeCount를 증가시킨다")
    @Test
    void like_restore_deletedLike() {
        User user = mock(User.class);
        Product product = Product.create(1L, "신발", "설명", BigDecimal.valueOf(50000), 10);
        Like deletedLike = Like.create(userId, productId);
        deletedLike.delete();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(likeRepository.findByUserIdAndProductIdIncludingDeleted(userId, productId))
            .thenReturn(Optional.of(deletedLike));
        when(likeRepository.save(deletedLike)).thenReturn(deletedLike);
        when(productRepository.save(product)).thenReturn(product);

        LikeDto.LikeInfo result = likeFacade.like(userId, productId);

        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.productId()).isEqualTo(productId);
        assertThat(deletedLike.isDeleted()).isFalse();
        assertThat(product.getLikeCount()).isEqualTo(1);
        verify(likeRepository).save(deletedLike);
        verify(productRepository).save(product);
    }

    // ── unlike ────────────────────────────────────────────────────────────────

    @DisplayName("unlike: 좋아요가 존재하면 취소에 성공하고 상품의 likeCount가 감소한다")
    @Test
    void unlike_success() {
        User user = mock(User.class);
        Product product = Product.create(1L, "신발", "설명", BigDecimal.valueOf(50000), 10);
        product.increaseLikeCount(); // likeCount = 1
        Like like = Like.create(userId, productId);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(likeRepository.findByUserIdAndProductId(userId, productId)).thenReturn(Optional.of(like));
        when(likeRepository.save(like)).thenReturn(like);
        when(productRepository.save(product)).thenReturn(product);

        likeFacade.unlike(userId, productId);

        assertThat(like.isDeleted()).isTrue();
        assertThat(product.getLikeCount()).isZero();
        verify(likeRepository).save(like);
        verify(productRepository).save(product);
    }

    @DisplayName("unlike: 존재하지 않는 유저면 예외가 발생한다")
    @Test
    void unlike_fail_userNotFound() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> likeFacade.unlike(userId, productId))
            .isInstanceOf(CoreException.class)
            .satisfies(e -> assertThat(((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.USER_NOT_FOUND));

        verify(likeRepository, never()).save(any());
        verify(productRepository, never()).save(any());
    }

    @DisplayName("unlike: 존재하지 않는 상품이면 예외가 발생한다")
    @Test
    void unlike_fail_productNotFound() {
        User user = mock(User.class);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(productRepository.findById(productId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> likeFacade.unlike(userId, productId))
            .isInstanceOf(CoreException.class)
            .satisfies(e -> assertThat(((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.PRODUCT_NOT_FOUND));

        verify(likeRepository, never()).save(any());
    }

    @DisplayName("unlike: 좋아요 기록이 없으면 멱등하게 성공한다")
    @Test
    void unlike_idempotent_whenLikeNotFound() {
        User user = mock(User.class);
        Product product = Product.create(1L, "신발", "설명", BigDecimal.valueOf(50000), 10);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(likeRepository.findByUserIdAndProductId(userId, productId)).thenReturn(Optional.empty());

        likeFacade.unlike(userId, productId);

        verify(likeRepository, never()).save(any());
        verify(productRepository, never()).save(any());
    }

    @DisplayName("unlike: likeCount는 0 미만으로 내려가지 않는다")
    @Test
    void unlike_likeCount_neverNegative() {
        User user = mock(User.class);
        Product product = Product.create(1L, "신발", "설명", BigDecimal.valueOf(50000), 10);
        // likeCount = 0 인 상태에서 unlike
        Like like = Like.create(userId, productId);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(likeRepository.findByUserIdAndProductId(userId, productId)).thenReturn(Optional.of(like));
        when(likeRepository.save(like)).thenReturn(like);
        when(productRepository.save(product)).thenReturn(product);

        likeFacade.unlike(userId, productId);

        assertThat(product.getLikeCount()).isZero();
    }
}
