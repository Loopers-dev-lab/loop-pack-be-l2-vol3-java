package com.loopers.application.like;

import com.loopers.application.product.ProductCreateCommand;
import com.loopers.application.product.ProductInfo;
import com.loopers.application.product.ProductService;
import com.loopers.domain.like.InMemoryLikeRepository;
import com.loopers.domain.product.InMemoryProductRepository;
import com.loopers.domain.product.Product;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class LikeFacadeTest {

    private InMemoryLikeRepository likeRepository;
    private InMemoryProductRepository productRepository;
    private LikeService likeService;
    private ProductService productService;
    private LikeFacade likeFacade;

    @BeforeEach
    void setUp() {
        likeRepository = new InMemoryLikeRepository();
        productRepository = new InMemoryProductRepository();
        likeService = new LikeService(likeRepository);
        productService = new ProductService(productRepository);
        likeFacade = new LikeFacade(likeService, productService);
    }

    @DisplayName("좋아요 등록 시, ")
    @Nested
    class Register {

        @DisplayName("성공하면 상품 likeCount가 1 증가한다.")
        @Test
        void increases_like_count_by_1_on_success() {
            // arrange
            long userId = 1L;
            ProductInfo product = productService.register(new ProductCreateCommand(1L, "에어맥스", "신발", 150000, 10));

            // act
            likeFacade.register(userId, product.id());

            // assert
            assertThat(productService.getProduct(product.id()).likeCount()).isEqualTo(1);
        }

        @DisplayName("이미 좋아요한 상품이면 예외가 발생한다.")
        @Test
        void throws_when_already_liked() {
            // arrange
            long userId = 1L;
            ProductInfo product = productService.register(new ProductCreateCommand(1L, "에어맥스", "신발", 150000, 10));
            likeFacade.register(userId, product.id());

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                likeFacade.register(userId, product.id());
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.ALREADY_LIKED);
        }
    }

    @DisplayName("좋아요 취소 시, ")
    @Nested
    class Cancel {

        @DisplayName("좋아요가 있을 때 취소하면 likeCount가 1 감소한다.")
        @Test
        void decreases_like_count_by_1_when_like_exists() {
            // arrange
            long userId = 1L;
            ProductInfo product = productService.register(new ProductCreateCommand(1L, "에어맥스", "신발", 150000, 10));
            likeFacade.register(userId, product.id());

            // act
            likeFacade.cancel(userId, product.id());

            // assert
            assertThat(productService.getProduct(product.id()).likeCount()).isEqualTo(0);
        }

        @DisplayName("좋아요가 없을 때 취소하면 예외 없이 처리되고 likeCount는 감소하지 않는다.")
        @Test
        void noop_when_like_does_not_exist() {
            // arrange
            long userId = 1L;
            ProductInfo product = productService.register(
                    new ProductCreateCommand(1L, "에어맥스", "신발", 150000, 10)
            );

            long productId = product.id();
            int before = productService.getProduct(productId).likeCount();

            // act
            likeFacade.cancel(userId, productId);

            // assert
            int after = productService.getProduct(productId).likeCount();
            assertThat(after).isEqualTo(before);
        }
    }

    @DisplayName("좋아요 목록 조회 시, ")
    @Nested
    class GetLikes {

        @DisplayName("삭제된 상품의 좋아요는 제외된다.")
        @Test
        void excludesLikes_whenProductIsDeleted() {
            // arrange
            long userId = 1L;
            ProductInfo activeProduct = productService.register(new ProductCreateCommand(1L, "에어맥스", "신발", 150000, 10));
            ProductInfo deletedProduct = productService.register(new ProductCreateCommand(1L, "조던", "농구화", 200000, 5));

            likeService.register(userId, activeProduct.id());
            likeService.register(userId, deletedProduct.id());

            productService.delete(deletedProduct.id());

            // act
            List<LikedProductInfo> likes = likeFacade.getLikedProductsByUserId(userId);

            // assert
            assertThat(likes)
                    .extracting(info -> info.product().id())
                    .doesNotContain(deletedProduct.id());
        }

        @DisplayName("HIDDEN 상태인 상품의 좋아요는 제외된다.")
        @Test
        void excludesLikes_whenProductIsHidden() {
            // arrange
            long userId = 1L;
            ProductInfo activeProduct = productService.register(new ProductCreateCommand(1L, "에어맥스", "신발", 150000, 10));
            ProductInfo hiddenProduct = productService.register(new ProductCreateCommand(1L, "조던", "농구화", 200000, 5));

            likeService.register(userId, activeProduct.id());
            likeService.register(userId, hiddenProduct.id());

            productService.changeVisibility(hiddenProduct.id(), Product.Visibility.HIDDEN);

            // act
            List<LikedProductInfo> likes = likeFacade.getLikedProductsByUserId(userId);

            // assert
            assertThat(likes)
                    .extracting(info -> info.product().id())
                    .doesNotContain(hiddenProduct.id());
        }
    }
}
