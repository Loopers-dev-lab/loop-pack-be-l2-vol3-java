package com.loopers.infrastructure.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.brand.BrandCacheRepository;
import com.loopers.application.coupon.category.CategoryCacheRepository;
import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.vo.BrandName;
import com.loopers.domain.category.Category;
import com.loopers.infrastructure.brand.BrandEntity;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.brand.db.BrandDbPassThroughCacheRepositoryImpl;
import com.loopers.infrastructure.brand.redis.BrandCacheRepositoryImpl;
import com.loopers.infrastructure.brand.redis.BrandCacheSyncer;
import com.loopers.infrastructure.cachesync.CacheSyncFailurePersistence;
import com.loopers.infrastructure.category.CategoryEntity;
import com.loopers.infrastructure.category.CategoryJpaRepository;
import com.loopers.infrastructure.category.db.CategoryDbPassThroughCacheRepositoryImpl;
import com.loopers.infrastructure.category.redis.CategoryCacheRepositoryImpl;
import com.loopers.infrastructure.category.redis.CategoryCacheSyncer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BrandCategoryCacheToggleTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(
                    RepositorySelectionTestConfig.class,
                    CommonDependencyTestConfig.class,
                    RedisDependencyTestConfig.class
            );

    @Test
    @DisplayName("기본값에서는 브랜드/카테고리 캐시가 Redis 구현체를 사용한다")
    void usesRedisRepositoryByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(BrandCacheRepository.class);
            assertThat(context.getBean(BrandCacheRepository.class)).isInstanceOf(BrandCacheRepositoryImpl.class);
            assertThat(context).hasSingleBean(CategoryCacheRepository.class);
            assertThat(context.getBean(CategoryCacheRepository.class)).isInstanceOf(CategoryCacheRepositoryImpl.class);
            assertThat(context).doesNotHaveBean(BrandDbPassThroughCacheRepositoryImpl.class);
            assertThat(context).doesNotHaveBean(CategoryDbPassThroughCacheRepositoryImpl.class);
        });
    }

    @Test
    @DisplayName("캐시를 비활성화하면 Redis 의존성 없이 DB pass-through 구현체로 안전하게 우회한다")
    void bypassesRedisSafelyWhenDisabled() {
        new ApplicationContextRunner()
                .withPropertyValues("loopers.cache.brand-category.enabled=false")
                .withUserConfiguration(RepositorySelectionTestConfig.class, CommonDependencyTestConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(BrandCacheRepository.class);
                    assertThat(context.getBean(BrandCacheRepository.class)).isInstanceOf(BrandDbPassThroughCacheRepositoryImpl.class);
                    assertThat(context).hasSingleBean(CategoryCacheRepository.class);
                    assertThat(context.getBean(CategoryCacheRepository.class)).isInstanceOf(CategoryDbPassThroughCacheRepositoryImpl.class);
                    assertThat(context).doesNotHaveBean(BrandCacheRepositoryImpl.class);
                    assertThat(context).doesNotHaveBean(CategoryCacheRepositoryImpl.class);
                    assertThat(context.containsBean(RedisConfig.REDIS_TEMPLATE_MASTER)).isFalse();

                    BrandJpaRepository brandJpaRepository = context.getBean(BrandJpaRepository.class);
                    CategoryJpaRepository categoryJpaRepository = context.getBean(CategoryJpaRepository.class);
                    CacheSyncFailurePersistence cacheSyncFailurePersistence = context.getBean(CacheSyncFailurePersistence.class);
                    BrandCacheRepository brandCacheRepository = context.getBean(BrandCacheRepository.class);
                    CategoryCacheRepository categoryCacheRepository = context.getBean(CategoryCacheRepository.class);
                    BrandCacheSyncer brandCacheSyncer = context.getBean(BrandCacheSyncer.class);
                    CategoryCacheSyncer categoryCacheSyncer = context.getBean(CategoryCacheSyncer.class);

                    UUID brandId = UUID.randomUUID();
                    UUID categoryId = UUID.randomUUID();
                    Brand brand = new Brand(brandId, new BrandName("브랜드"), "desc", "img");
                    Category category = new Category(categoryId, "카테고리");

                    when(brandJpaRepository.findByReferenceIdAndDeletedAtIsNull(brandId))
                            .thenReturn(Optional.of(BrandEntity.from(brand)));
                    when(categoryJpaRepository.findByReferenceIdAndDeletedAtIsNull(categoryId))
                            .thenReturn(Optional.of(CategoryEntity.from(category)));

                    assertThat(brandCacheRepository.findById(brandId)).contains(brand);
                    assertThat(categoryCacheRepository.findById(categoryId)).contains(category);

                    brandCacheSyncer.registerUpsert(brand);
                    brandCacheSyncer.registerDelete(brandId);
                    categoryCacheSyncer.registerUpsert(category);
                    categoryCacheSyncer.registerDelete(categoryId);

                    verifyNoInteractions(cacheSyncFailurePersistence);
                });
    }

    @Configuration
    @Import({
            BrandCacheRepositoryImpl.class,
            CategoryCacheRepositoryImpl.class,
            BrandDbPassThroughCacheRepositoryImpl.class,
            CategoryDbPassThroughCacheRepositoryImpl.class,
            BrandCacheSyncer.class,
            CategoryCacheSyncer.class
    })
    static class RepositorySelectionTestConfig {
    }

    @Configuration
    static class CommonDependencyTestConfig {

        @Bean
        BrandJpaRepository brandJpaRepository() {
            return Mockito.mock(BrandJpaRepository.class);
        }

        @Bean
        CategoryJpaRepository categoryJpaRepository() {
            return Mockito.mock(CategoryJpaRepository.class);
        }

        @Bean
        CacheSyncFailurePersistence cacheSyncFailurePersistence() {
            return Mockito.mock(CacheSyncFailurePersistence.class);
        }
    }

    @Configuration
    static class RedisDependencyTestConfig {

        @Bean
        @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
        RedisTemplate<String, String> redisTemplateMaster() {
            RedisTemplate<String, String> redisTemplate = new RedisTemplate<>();
            redisTemplate.setConnectionFactory(Mockito.mock(RedisConnectionFactory.class));
            return redisTemplate;
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
