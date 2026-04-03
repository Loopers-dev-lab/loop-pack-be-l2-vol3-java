package com.loopers.interfaces.scheduler;

import com.loopers.application.brand.BrandCommand;
import com.loopers.application.brand.BrandService;
import com.loopers.application.like.LikeService;
import com.loopers.application.product.ProductCommand;
import com.loopers.application.product.ProductService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class LikeCountReconciliationSchedulerIntegrationTest {

    @Autowired
    private LikeCountReconciliationScheduler scheduler;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BrandService brandService;

    @Autowired
    private ProductService productService;

    @Autowired
    private LikeService likeService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @MockBean
    private KafkaTemplate<Object, Object> kafkaTemplate;

    private Long productId;

    @BeforeEach
    void setUp() {
        databaseCleanUp.truncateAllTables();
        Long brandId = brandService.register(BrandCommand.Register.of("나이키", "스포츠")).getId();
        productId = productService.register(
                ProductCommand.Register.of(brandId, "운동화", new BigDecimal("50000"), 100, "설명")).getId();
    }

    @Nested
    class 동기화 {

        @Test
        void likes_테이블_COUNT와_Product_likeCount가_다르면_동기화된다() {
            // likes 3건 INSERT (likeCount는 @Async로 증가하므로 reconcile 전에는 0일 수 있음)
            likeService.like(1L, productId);
            likeService.like(2L, productId);
            likeService.like(3L, productId);

            // likeCount를 강제로 0으로 리셋 (불일치 상태 생성)
            productService.incrementLikeCount(productId); // 1
            // 실제로는 @Async 핸들러가 증가시키지만 테스트에서는 직접 제어

            scheduler.reconcile();

            Product product = productRepository.findById(productId).orElseThrow();
            assertThat(product.getLikeCount()).isEqualTo(3);
        }

        @Test
        void likes가_없으면_likeCount가_0으로_동기화된다() {
            // likeCount를 강제로 올린 상태
            productService.incrementLikeCount(productId);

            scheduler.reconcile();

            Product product = productRepository.findById(productId).orElseThrow();
            assertThat(product.getLikeCount()).isZero();
        }
    }
}
