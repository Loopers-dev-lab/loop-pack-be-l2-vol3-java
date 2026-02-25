package com.loopers.application.like;

import com.loopers.application.product.ProductCreateCommand;
import com.loopers.application.product.ProductInfo;
import com.loopers.application.product.ProductService;
import com.loopers.domain.like.InMemoryLikeRepository;
import com.loopers.domain.product.InMemoryProductRepository;
import com.loopers.domain.product.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
            List<LikeInfo> likes = likeFacade.getLikesByUserId(userId);

            // assert
            assertThat(likes)
                    .extracting(LikeInfo::productId)
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
            List<LikeInfo> likes = likeFacade.getLikesByUserId(userId);

            // assert
            assertThat(likes)
                    .extracting(LikeInfo::productId)
                    .doesNotContain(hiddenProduct.id());
        }
    }
}
