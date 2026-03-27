package com.loopers.application.like;

import com.loopers.application.product.ProductFacade;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.product.Money;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.stock.StockModel;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.infrastructure.stock.StockJpaRepository;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(MySqlTestContainersConfig.class)
@ActiveProfiles("test")
class LikeTransactionCacheTest {

    static final GenericContainer<?> redisContainer =
            new GenericContainer<>(DockerImageName.parse("redis:latest")).withExposedPorts(6379);

    static {
        redisContainer.start();
    }

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        String host = redisContainer.getHost();
        int port = redisContainer.getFirstMappedPort();
        registry.add("datasource.redis.database", () -> 0);
        registry.add("datasource.redis.master.host", () -> host);
        registry.add("datasource.redis.master.port", () -> port);
        registry.add("datasource.redis.replicas[0].host", () -> host);
        registry.add("datasource.redis.replicas[0].port", () -> port);
    }

    @Autowired private LikeTransactionService likeTransactionService;
    @Autowired private ProductFacade productFacade;
    @Autowired private CacheManager cacheManager;
    @Autowired private BrandJpaRepository brandJpaRepository;
    @Autowired private ProductJpaRepository productJpaRepository;
    @Autowired private StockJpaRepository stockJpaRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        var cache = cacheManager.getCache("productDetail");
        if (cache != null) {
            cache.clear();
        }
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("좋아요 등록 시 상품 상세 캐시가 삭제된다")
    void 좋아요_등록_시_상품_상세_캐시가_삭제된다() {
        // given
        BrandModel brand = brandJpaRepository.save(new BrandModel("테스트브랜드", "설명"));
        ProductModel product = productJpaRepository.save(
                new ProductModel("테스트상품", "상품설명", new Money(10000), brand.getId())
        );
        stockJpaRepository.save(new StockModel(product.getId(), 100));

        productFacade.getProduct(product.getId());
        assertThat(cacheManager.getCache("productDetail").get(product.getId())).isNotNull();

        // when
        likeTransactionService.doLike(1L, product.getId());

        // then
        assertThat(cacheManager.getCache("productDetail").get(product.getId())).isNull();
    }

    @Test
    @DisplayName("좋아요 취소 시 상품 상세 캐시가 삭제된다")
    void 좋아요_취소_시_상품_상세_캐시가_삭제된다() {
        // given
        BrandModel brand = brandJpaRepository.save(new BrandModel("테스트브랜드", "설명"));
        ProductModel product = productJpaRepository.save(
                new ProductModel("테스트상품", "상품설명", new Money(10000), brand.getId())
        );
        stockJpaRepository.save(new StockModel(product.getId(), 100));

        likeTransactionService.doLike(1L, product.getId());

        productFacade.getProduct(product.getId());
        assertThat(cacheManager.getCache("productDetail").get(product.getId())).isNotNull();

        // when
        likeTransactionService.doUnlike(1L, product.getId());

        // then
        assertThat(cacheManager.getCache("productDetail").get(product.getId())).isNull();
    }
}
