package com.loopers.config;

import com.loopers.config.redis.CustomCacheErrorHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;

class CustomCacheErrorHandlerTest {

    private CustomCacheErrorHandler sut;
    private Cache mockCache;

    @BeforeEach
    void setUp() {
        sut = new CustomCacheErrorHandler();
        mockCache = mock(Cache.class);
    }

    @Test
    @DisplayName("handleCacheGetError 호출 시 예외가 전파되지 않는다")
    void handleCacheGetError_should_not_propagate_exception() {
        // given
        RuntimeException exception = new RuntimeException("Redis connection failed");
        Object key = "testKey";

        // when & then
        assertDoesNotThrow(() -> sut.handleCacheGetError(exception, mockCache, key));
    }

    @Test
    @DisplayName("handleCachePutError 호출 시 예외가 전파되지 않는다")
    void handleCachePutError_should_not_propagate_exception() {
        // given
        RuntimeException exception = new RuntimeException("Redis connection failed");
        Object key = "testKey";
        Object value = "testValue";

        // when & then
        assertDoesNotThrow(() -> sut.handleCachePutError(exception, mockCache, key, value));
    }

    @Test
    @DisplayName("handleCacheEvictError 호출 시 예외가 전파되지 않는다")
    void handleCacheEvictError_should_not_propagate_exception() {
        // given
        RuntimeException exception = new RuntimeException("Redis connection failed");
        Object key = "testKey";

        // when & then
        assertDoesNotThrow(() -> sut.handleCacheEvictError(exception, mockCache, key));
    }

    @Test
    @DisplayName("handleCacheClearError 호출 시 예외가 전파되지 않는다")
    void handleCacheClearError_should_not_propagate_exception() {
        // given
        RuntimeException exception = new RuntimeException("Redis connection failed");

        // when & then
        assertDoesNotThrow(() -> sut.handleCacheClearError(exception, mockCache));
    }
}
