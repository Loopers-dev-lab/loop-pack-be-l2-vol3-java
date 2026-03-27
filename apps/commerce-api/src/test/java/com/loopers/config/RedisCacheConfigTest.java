package com.loopers.config;

import com.loopers.testcontainers.RedisTestContainersConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(RedisTestContainersConfig.class)
@ActiveProfiles("test")
class RedisCacheConfigTest {

    @Autowired
    private CacheManager cacheManager;

    @Test
    @DisplayName("CacheManager Bean이 RedisCacheManager 인스턴스여야 한다")
    void cacheManager_should_be_redisCacheManager_instance() {
        // given & when & then
        assertThat(cacheManager).isInstanceOf(RedisCacheManager.class);
    }

    @Test
    @DisplayName("productDetail 캐시 설정이 존재해야 한다")
    void productDetail_cache_configuration_should_exist() {
        // given
        RedisCacheManager redisCacheManager = (RedisCacheManager) cacheManager;

        // when
        var cache = redisCacheManager.getCache("productDetail");

        // then
        assertThat(cache).isNotNull();
    }

}
