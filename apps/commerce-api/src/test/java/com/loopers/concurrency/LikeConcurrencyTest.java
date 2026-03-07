package com.loopers.concurrency;

import com.loopers.application.brand.BrandCommand;
import com.loopers.application.brand.BrandService;
import com.loopers.application.like.LikeFacade;
import com.loopers.application.product.ProductCommand;
import com.loopers.application.product.ProductService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class LikeConcurrencyTest {

    @Autowired
    private BrandService brandService;

    @Autowired
    private LikeFacade likeFacade;

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    void 동시에_여러_유저가_좋아요해도_좋아요수가_정상_반영된다() throws InterruptedException {
        Long brandId = brandService.register(BrandCommand.Register.of("나이키", "스포츠 브랜드")).getId();
        Product product = productService.register(
                ProductCommand.Register.of(brandId, "운동화", new BigDecimal("50000"), 100, "편한 운동화"));
        Long productId = product.getId();

        int threadCount = 10;
        ConcurrencyTestHelper.executeConcurrently(threadCount, i -> likeFacade.like((long) (i + 1), productId));

        Product found = productRepository.findById(productId).orElseThrow();
        assertThat(found.getLikeCount()).isEqualTo(threadCount);
    }

    @Test
    void 동시에_여러_유저가_좋아요_취소해도_좋아요수가_정상_반영된다() throws InterruptedException {
        Long brandId = brandService.register(BrandCommand.Register.of("나이키", "스포츠 브랜드")).getId();
        Product product = productService.register(
                ProductCommand.Register.of(brandId, "운동화", new BigDecimal("50000"), 100, "편한 운동화"));
        Long productId = product.getId();

        int threadCount = 10;
        for (int i = 0; i < threadCount; i++) {
            likeFacade.like((long) (i + 1), productId);
        }

        ConcurrencyTestHelper.executeConcurrently(threadCount, i -> likeFacade.unlike((long) (i + 1), productId));

        Product found = productRepository.findById(productId).orElseThrow();
        assertThat(found.getLikeCount()).isEqualTo(0);
    }
}
