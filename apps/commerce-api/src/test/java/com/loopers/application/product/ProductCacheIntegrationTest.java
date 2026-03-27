package com.loopers.application.product;

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
class ProductCacheIntegrationTest {

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

    @Autowired
    private ProductFacade productFacade;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private StockJpaRepository stockJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        var cache = cacheManager.getCache("productDetail");
        if (cache != null) {
            cache.clear();
        }
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("상품 상세 조회 시 캐시에 저장된다")
    void 상품_상세_조회_시_캐시에_저장된다() {
        // given
        BrandModel brand = brandJpaRepository.save(new BrandModel("테스트브랜드", "설명"));
        ProductModel product = productJpaRepository.save(
                new ProductModel("테스트상품", "상품설명", new Money(10000), brand.getId())
        );
        stockJpaRepository.save(new StockModel(product.getId(), 100));

        // when
        productFacade.getProduct(product.getId());

        // then
        var cachedValue = cacheManager.getCache("productDetail").get(product.getId());
        assertThat(cachedValue).isNotNull();
    }

    @Test
    @DisplayName("상품 수정 시 상세 캐시가 삭제된다")
    void 상품_수정_시_상세_캐시가_삭제된다() {
        // given
        BrandModel brand = brandJpaRepository.save(new BrandModel("테스트브랜드", "설명"));
        ProductModel product = productJpaRepository.save(
                new ProductModel("테스트상품", "상품설명", new Money(10000), brand.getId())
        );
        stockJpaRepository.save(new StockModel(product.getId(), 100));

        productFacade.getProduct(product.getId());
        assertThat(cacheManager.getCache("productDetail").get(product.getId())).isNotNull();

        // when
        productFacade.update(product.getId(), "수정된상품", "수정된설명", new Money(20000));

        // then
        assertThat(cacheManager.getCache("productDetail").get(product.getId())).isNull();
    }

    @Test
    @DisplayName("상품 삭제 시 상세 캐시가 삭제된다")
    void 상품_삭제_시_상세_캐시가_삭제된다() {
        // given
        BrandModel brand = brandJpaRepository.save(new BrandModel("테스트브랜드", "설명"));
        ProductModel product = productJpaRepository.save(
                new ProductModel("테스트상품", "상품설명", new Money(10000), brand.getId())
        );
        stockJpaRepository.save(new StockModel(product.getId(), 100));

        productFacade.getProduct(product.getId());
        assertThat(cacheManager.getCache("productDetail").get(product.getId())).isNotNull();

        // when
        productFacade.delete(product.getId());

        // then
        assertThat(cacheManager.getCache("productDetail").get(product.getId())).isNull();
    }

    @Test
    @DisplayName("캐시 히트 시 동일한 결과를 반환한다")
    void 캐시_히트_시_동일한_결과를_반환한다() {
        // given
        BrandModel brand = brandJpaRepository.save(new BrandModel("테스트브랜드", "설명"));
        ProductModel product = productJpaRepository.save(
                new ProductModel("테스트상품", "상품설명", new Money(10000), brand.getId())
        );
        stockJpaRepository.save(new StockModel(product.getId(), 100));

        ProductDetail firstResult = productFacade.getProduct(product.getId());

        // when
        ProductDetail secondResult = productFacade.getProduct(product.getId());

        // then
        assertThat(secondResult).isEqualTo(firstResult);
    }
}
