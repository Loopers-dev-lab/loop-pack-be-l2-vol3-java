package com.loopers.application.like;

import com.loopers.application.product.ProductFacade;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.like.LikeModel;
import com.loopers.domain.product.Money;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
class LikeFacadeIntegrationTest {

    @Autowired private LikeFacade likeFacade;
    @Autowired private ProductFacade productFacade;
    @Autowired private ProductService productService;
    @Autowired private BrandService brandService;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() { databaseCleanUp.truncateAllTables(); }

    private Long createBrand(String name) { return brandService.register(name, "설명").getId(); }
    private Long createProduct(String name, int price, Long brandId) {
        return productFacade.register(name, "설명", new Money(price), brandId, 10).getId();
    }

    @DisplayName("좋아요 등록")
    @Nested
    class Like {

        @DisplayName("상품에 좋아요를 등록하면 likeCount가 증가한다")
        @Test
        void likesProductAndIncrementsCount() {
            // given
            Long brandId = createBrand("나이키");
            Long productId = createProduct("에어맥스", 129000, brandId);

            // when
            likeFacade.like(1L, productId);

            // then
            ProductModel product = productService.getProduct(productId);
            assertThat(product.likeCount()).isEqualTo(1);
        }

        @DisplayName("같은 상품에 두 번 좋아요해도 likeCount는 1이다 (멱등성)")
        @Test
        void likeIsIdempotent() {
            // given
            Long brandId = createBrand("나이키");
            Long productId = createProduct("에어맥스", 129000, brandId);

            // when
            likeFacade.like(1L, productId);
            likeFacade.like(1L, productId);

            // then
            ProductModel product = productService.getProduct(productId);
            assertThat(product.likeCount()).isEqualTo(1);
        }

        @DisplayName("좋아요 취소 후 다시 좋아요하면 복원된다")
        @Test
        void restoresAfterUnlike() {
            // given
            Long brandId = createBrand("나이키");
            Long productId = createProduct("에어맥스", 129000, brandId);
            likeFacade.like(1L, productId);
            likeFacade.unlike(1L, productId);

            // when
            likeFacade.like(1L, productId);

            // then
            ProductModel product = productService.getProduct(productId);
            assertThat(product.likeCount()).isEqualTo(1);
        }
    }

    @DisplayName("좋아요 취소")
    @Nested
    class Unlike {

        @DisplayName("좋아요를 취소하면 likeCount가 감소한다")
        @Test
        void unlikeDecrementsCount() {
            // given
            Long brandId = createBrand("나이키");
            Long productId = createProduct("에어맥스", 129000, brandId);
            likeFacade.like(1L, productId);

            // when
            likeFacade.unlike(1L, productId);

            // then
            ProductModel product = productService.getProduct(productId);
            assertThat(product.likeCount()).isEqualTo(0);
        }

        @DisplayName("좋아요하지 않은 상품을 취소해도 예외가 발생하지 않는다 (멱등성)")
        @Test
        void unlikeIsIdempotent() {
            // given
            Long brandId = createBrand("나이키");
            Long productId = createProduct("에어맥스", 129000, brandId);

            // when & then — 예외 없이 정상 완료
            likeFacade.unlike(1L, productId);
        }

        @DisplayName("이미 취소한 좋아요를 다시 취소해도 예외가 발생하지 않는다")
        @Test
        void doubleUnlikeIsIdempotent() {
            // given
            Long brandId = createBrand("나이키");
            Long productId = createProduct("에어맥스", 129000, brandId);
            likeFacade.like(1L, productId);
            likeFacade.unlike(1L, productId);

            // when & then — 예외 없이 정상 완료
            likeFacade.unlike(1L, productId);

            ProductModel product = productService.getProduct(productId);
            assertThat(product.likeCount()).isEqualTo(0);
        }
    }

    @DisplayName("좋아요 목록 조회")
    @Nested
    class GetMyLikes {

        @DisplayName("본인의 좋아요 목록을 조회할 수 있다")
        @Test
        void getsMyLikes() {
            // given
            Long brandId = createBrand("나이키");
            Long p1 = createProduct("에어맥스", 129000, brandId);
            Long p2 = createProduct("조던", 159000, brandId);
            likeFacade.like(1L, p1);
            likeFacade.like(1L, p2);

            // when
            Page<LikeModel> result = likeFacade.getMyLikes(1L,
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt")));

            // then
            assertThat(result.getContent()).hasSize(2);
        }

        @DisplayName("삭제된 상품의 좋아요는 목록에서 제외된다")
        @Test
        void excludesDeletedProducts() {
            // given
            Long brandId = createBrand("나이키");
            Long p1 = createProduct("에어맥스", 129000, brandId);
            Long p2 = createProduct("조던", 159000, brandId);
            likeFacade.like(1L, p1);
            likeFacade.like(1L, p2);
            productService.delete(p2);

            // when
            Page<LikeModel> result = likeFacade.getMyLikes(1L,
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt")));

            // then
            assertAll(
                () -> assertThat(result.getContent()).hasSize(1),
                () -> assertThat(result.getContent().get(0).productId()).isEqualTo(p1)
            );
        }

        @DisplayName("다른 사용자의 좋아요는 조회되지 않는다")
        @Test
        void doesNotReturnOtherUserLikes() {
            // given
            Long brandId = createBrand("나이키");
            Long productId = createProduct("에어맥스", 129000, brandId);
            likeFacade.like(1L, productId);
            likeFacade.like(2L, productId);

            // when
            Page<LikeModel> result = likeFacade.getMyLikes(1L,
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt")));

            // then
            assertThat(result.getContent()).hasSize(1);
        }
    }
}
