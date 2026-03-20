package com.loopers.application.product.cache;

import com.loopers.application.product.PublicProductDetailCacheApplicationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PublicProductDetailCacheEvictionAspectTest {

    @Mock
    private PublicProductDetailCacheApplicationService publicProductDetailCacheApplicationService;

    @Test
    @DisplayName("상품 변경이 완료되면 상세 캐시를 제거한다")
    void evictAfterMutation() {
        PublicProductDetailCacheEvictionAspect aspect =
                new PublicProductDetailCacheEvictionAspect(publicProductDetailCacheApplicationService);
        UUID productId = UUID.randomUUID();

        aspect.evict(productId);

        verify(publicProductDetailCacheApplicationService).evict(productId);
    }
}
