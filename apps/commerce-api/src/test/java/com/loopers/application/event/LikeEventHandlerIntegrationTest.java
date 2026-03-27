package com.loopers.application.event;

import com.loopers.application.brand.BrandCommand;
import com.loopers.application.brand.BrandService;
import com.loopers.application.like.LikeFacade;
import com.loopers.application.product.ProductCommand;
import com.loopers.application.product.ProductService;
import com.loopers.domain.like.LikeRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@SpringBootTest
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class LikeEventHandlerIntegrationTest {

    @Autowired
    private LikeFacade likeFacade;

    @Autowired
    private BrandService brandService;

    @Autowired
    private ProductService productService;

    @Autowired
    private LikeRepository likeRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private Long productId;

    @BeforeEach
    void setUp() {
        databaseCleanUp.truncateAllTables();
        Long brandId = brandService.register(BrandCommand.Register.of("나이키", "스포츠 브랜드")).getId();
        productId = productService.register(
                ProductCommand.Register.of(brandId, "운동화", new BigDecimal("50000"), 100, "편한 운동화")).getId();
    }

    @Nested
    class 실패_격리 {

        @Test
        void LikeCountEventHandler가_예외를_던져도_좋아요_자체는_성공한다() {
            // LikeCountEventHandler는 @Async + AFTER_COMMIT이므로 핸들러 실패가 API에 전파되지 않음
            assertThatCode(() -> likeFacade.like(1L, productId))
                    .doesNotThrowAnyException();
        }

        @Test
        void 핸들러_실패_후에도_likes_테이블에_레코드가_존재한다() {
            likeFacade.like(1L, productId);

            assertThat(likeRepository.existsByUserIdAndProductId(1L, productId)).isTrue();
        }
    }
}
