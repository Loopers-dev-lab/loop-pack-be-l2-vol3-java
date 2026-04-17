package com.loopers.application.like;

import com.loopers.application.product.ProductService;
import com.loopers.domain.like.event.LikeToggledEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class LikeMetricsEventListenerTest {

    @InjectMocks
    private LikeMetricsEventListener listener;

    @Mock
    private ProductService productService;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache cache;

    @DisplayName("liked=true 이벤트 수신 시 like_count 증가 + 캐시 evict")
    @Test
    void incrementsLikeCountAndEvictsCache() {
        // given
        LikeToggledEvent event = new LikeToggledEvent(100L, true);
        given(cacheManager.getCache("productDetail")).willReturn(cache);

        // when
        listener.handleLikeMetrics(event);

        // then
        then(productService).should().incrementLikeCount(100L);
        then(cache).should().evict(100L);
    }

    @DisplayName("liked=false 이벤트 수신 시 like_count 감소 + 캐시 evict")
    @Test
    void decrementsLikeCountAndEvictsCache() {
        // given
        LikeToggledEvent event = new LikeToggledEvent(100L, false);
        given(cacheManager.getCache("productDetail")).willReturn(cache);

        // when
        listener.handleLikeMetrics(event);

        // then
        then(productService).should().decrementLikeCount(100L);
        then(cache).should().evict(100L);
    }
}
