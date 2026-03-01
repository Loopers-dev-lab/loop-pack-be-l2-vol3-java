package com.loopers.domain.like;

import com.loopers.domain.brand.BrandDomainService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductDomainService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class LikeDomainServiceIntegrationTest {

    @Autowired
    private LikeDomainService likeService;

    @Autowired
    private ProductDomainService productService;

    @Autowired
    private BrandDomainService brandService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private Long brandId;
    private Long productId;

    @BeforeEach
    void setUp() {
        brandId = brandService.register("나이키").getId();
        Product product = productService.register(brandId, "에어맥스", 129000, 100);
        productId = product.getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("좋아요를 등록할 때, ")
    @Nested
    class LikeProduct {

        @DisplayName("처음 좋아요하면, 좋아요가 저장된다.")
        @Test
        void savesLike_whenFirstTime() {
            likeService.like(1L, productId);

            List<Like> likes = likeService.getMyLikes(1L);
            assertAll(
                () -> assertThat(likes).hasSize(1),
                () -> assertThat(likes.get(0).getUserId()).isEqualTo(1L),
                () -> assertThat(likes.get(0).getProductId()).isEqualTo(productId)
            );
        }

        @DisplayName("이미 좋아요한 상품이면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenAlreadyLiked() {
            likeService.like(1L, productId);

            CoreException result = assertThrows(CoreException.class, () -> likeService.like(1L, productId));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }

    @DisplayName("좋아요를 취소할 때, ")
    @Nested
    class UnlikeProduct {

        @DisplayName("좋아요가 존재하면, 삭제된다.")
        @Test
        void deletesLike_whenLikeExists() {
            likeService.like(1L, productId);

            likeService.unlike(1L, productId);

            List<Like> likes = likeService.getMyLikes(1L);
            assertThat(likes).isEmpty();
        }

        @DisplayName("좋아요가 존재하지 않으면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenLikeDoesNotExist() {
            CoreException result = assertThrows(CoreException.class, () -> likeService.unlike(1L, productId));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("내 좋아요 목록을 조회할 때, ")
    @Nested
    class GetMyLikes {

        @DisplayName("좋아요한 상품이 있으면, 목록을 반환한다.")
        @Test
        void returnsLikes_whenLikesExist() {
            Product product2 = productService.register(brandId, "에어포스1", 109000, 200);
            likeService.like(1L, productId);
            likeService.like(1L, product2.getId());

            List<Like> result = likeService.getMyLikes(1L);

            assertThat(result).hasSize(2);
        }

        @DisplayName("좋아요한 상품이 없으면, 빈 목록을 반환한다.")
        @Test
        void returnsEmptyList_whenNoLikes() {
            List<Like> result = likeService.getMyLikes(1L);

            assertThat(result).isEmpty();
        }
    }
}
