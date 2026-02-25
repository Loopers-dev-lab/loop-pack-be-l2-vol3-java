package com.loopers.application.like;

import com.loopers.domain.like.Like;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class LikeServiceIntegrationTest {

    @Autowired
    private LikeService likeService;

    @Autowired
    private LikeRepository likeRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    class 좋아요_등록 {

        @Test
        void 신규_좋아요면_저장되고_생성됨을_반환한다() {
            boolean result = likeService.like(1L, 1L);

            assertThat(result).isTrue();
            Optional<Like> saved = likeRepository.findByUserIdAndProductId(1L, 1L);
            assertThat(saved).isPresent();
            assertThat(saved.get().getUserId()).isEqualTo(1L);
            assertThat(saved.get().getProductId()).isEqualTo(1L);
        }

        @Test
        void 이미_좋아요한_상태이면_생성되지_않는다() {
            likeService.like(1L, 1L);

            boolean result = likeService.like(1L, 1L);

            assertThat(result).isFalse();
        }
    }

    @Nested
    class 좋아요_취소 {

        @Test
        void 좋아요가_존재하면_물리적_삭제된다() {
            likeService.like(1L, 1L);

            boolean result = likeService.unlike(1L, 1L);

            assertThat(result).isTrue();
            Optional<Like> found = likeRepository.findByUserIdAndProductId(1L, 1L);
            assertThat(found).isEmpty();
        }

        @Test
        void 좋아요가_없으면_삭제되지_않는다() {
            boolean result = likeService.unlike(1L, 1L);

            assertThat(result).isFalse();
        }
    }

    @Nested
    class 좋아요_목록_조회 {

        @Test
        void 좋아요한_상품을_최신순으로_조회한다() {
            Product product1 = productRepository.save(Product.create(1L, "상품1", new BigDecimal("10000"), 10, "설명1"));
            Product product2 = productRepository.save(Product.create(1L, "상품2", new BigDecimal("20000"), 20, "설명2"));
            likeService.like(1L, product1.getId());
            likeService.like(1L, product2.getId());

            Page<Like> result = likeService.findLikedProducts(1L, PageRequest.of(0, 10));

            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getContent().get(0).getProductId()).isEqualTo(product2.getId());
            assertThat(result.getContent().get(1).getProductId()).isEqualTo(product1.getId());
        }

        @Test
        void 삭제된_상품은_조회되지_않는다() {
            Product product1 = productRepository.save(Product.create(1L, "상품1", new BigDecimal("10000"), 10, "설명1"));
            Product product2 = productRepository.save(Product.create(1L, "상품2", new BigDecimal("20000"), 20, "설명2"));
            likeService.like(1L, product1.getId());
            likeService.like(1L, product2.getId());
            product2.delete();
            productRepository.save(product2);

            Page<Like> result = likeService.findLikedProducts(1L, PageRequest.of(0, 10));

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getProductId()).isEqualTo(product1.getId());
            assertThat(result.getTotalElements()).isEqualTo(1);
        }

        @Test
        void 좋아요가_없으면_빈_목록을_반환한다() {
            Page<Like> result = likeService.findLikedProducts(1L, PageRequest.of(0, 10));

            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isZero();
        }
    }
}
