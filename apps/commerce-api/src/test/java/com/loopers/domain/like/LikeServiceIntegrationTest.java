package com.loopers.domain.like;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Import(MySqlTestContainersConfig.class)
class LikeServiceIntegrationTest {

    @Autowired
    private LikeService likeService;

    @Autowired
    private ProductService productService;

    @Autowired
    private BrandService brandService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private Long saveProduct() {
        BrandModel brand = brandService.register("테스트 브랜드");
        ProductModel product = productService.register(brand.getId(), "테스트 상품", new BigDecimal("10000"), 10);
        return product.getId();
    }

    @DisplayName("addLike 시")
    @Nested
    class AddLike {

        @DisplayName("유효한 상품이면 좋아요가 저장된다.")
        @Test
        void addLike_whenValid_shouldSave() {
            // given
            Long productId = saveProduct();
            Long userId = 1L;

            // when
            LikeModel saved = likeService.addLike(userId, productId);

            // then
            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getUserId()).isEqualTo(userId);
            assertThat(saved.getProductId()).isEqualTo(productId);
        }

        @DisplayName("존재하지 않는 상품이면 NOT_FOUND 예외가 발생한다.")
        @Test
        void addLike_whenProductNotFound_shouldThrowNotFound() {
            CoreException ex = assertThrows(CoreException.class, () ->
                likeService.addLike(1L, 999_999L));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("같은 사용자가 같은 상품에 중복 좋아요 시 CONFLICT 예외가 발생한다.")
        @Test
        void addLike_whenDuplicate_shouldThrowConflict() {
            // given
            Long productId = saveProduct();
            Long userId = 1L;
            likeService.addLike(userId, productId);

            // when & then
            CoreException ex = assertThrows(CoreException.class, () ->
                likeService.addLike(userId, productId));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }

    @DisplayName("removeLike 시")
    @Nested
    class RemoveLike {

        @DisplayName("저장된 좋아요면 삭제된다.")
        @Test
        void removeLike_whenExists_shouldDelete() {
            // given
            Long productId = saveProduct();
            Long userId = 1L;
            likeService.addLike(userId, productId);

            // when
            likeService.removeLike(userId, productId);

            // then (재추가 시 성공해야 삭제됐음을 의미)
            LikeModel again = likeService.addLike(userId, productId);
            assertThat(again.getId()).isNotNull();
        }

        @DisplayName("존재하지 않는 좋아요 취소 시 NOT_FOUND 예외가 발생한다.")
        @Test
        void removeLike_whenNotFound_shouldThrowNotFound() {
            CoreException ex = assertThrows(CoreException.class, () ->
                likeService.removeLike(1L, 999_999L));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("findLikesByUserId 시")
    @Nested
    class FindLikesByUserId {

        @DisplayName("해당 사용자의 좋아요 목록을 페이지로 반환한다.")
        @Test
        void findLikesByUserId_shouldReturnPage() {
            // given
            Long productId = saveProduct();
            Long userId = 1L;
            likeService.addLike(userId, productId);
            Pageable pageable = PageRequest.of(0, 10);

            // when
            var page = likeService.findLikesByUserId(userId, pageable);

            // then
            assertThat(page.getContent()).hasSize(1);
            assertThat(page.getContent().get(0).getProductId()).isEqualTo(productId);
        }
    }
}
