package com.loopers.application.like;

import com.loopers.application.product.ProductCacheService;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@SpringBootTest
@Import(MySqlTestContainersConfig.class)
class LikeFacadeAfterCommitEventIntegrationTest {

    @Autowired
    private LikeFacade likeFacade;

    @Autowired
    private RollbackTestService rollbackTestService;

    @Autowired
    private ProductService productService;

    @Autowired
    private BrandService brandService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @MockBean
    private ProductCacheService productCacheService;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private Long saveProduct() {
        BrandModel brand = brandService.registerBrand("테스트 브랜드");
        ProductModel product = productService.registerProduct(brand.getId(), "테스트 상품", new BigDecimal("10000"), 10);
        return product.getId();
    }

    @DisplayName("좋아요 추가/취소 시")
    @Nested
    class AfterCommit {

        @Test
        @DisplayName("커밋되면 커밋 이후 리스너가 PDP/PLP 캐시를 무효화한다.")
        void addLike_whenCommit_shouldEvictCachesAfterCommit() {
            Long productId = saveProduct();

            likeFacade.addLike(1L, productId);

            // AFTER_COMMIT는 트랜잭션 종료 직후 실행되므로 약간의 여유를 주고 검증한다.
            verify(productCacheService, timeout(1000)).evictDetail(productId);
            verify(productCacheService, timeout(1000)).evictList();
        }

        @Test
        @DisplayName("롤백되면 커밋 이후 리스너가 비즈니스 성공을 가정하지 않는다(캐시 무효화가 실행되지 않는다).")
        void addLike_whenRollback_shouldNotEvictCaches() {
            Long productId = saveProduct();

            assertThatThrownBy(() -> rollbackTestService.addLikeAndRollback(1L, productId))
                    .isInstanceOf(IllegalStateException.class);

            verify(productCacheService, never()).evictDetail(productId);
            verify(productCacheService, never()).evictList();
        }
    }

    @Service
    static class RollbackTestService {

        private final LikeFacade likeFacade;

        RollbackTestService(LikeFacade likeFacade) {
            this.likeFacade = likeFacade;
        }

        @Transactional
        public void addLikeAndRollback(Long userId, Long productId) {
            likeFacade.addLike(userId, productId);
            throw new IllegalStateException("rollback");
        }
    }
}

