package com.loopers.application.product.cache;

import com.loopers.application.product.PublicProductDetailCacheApplicationService;
import com.loopers.application.product.view.ProductView;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicProductDetailCacheAspectTest {

    @Mock
    private PublicProductDetailCacheApplicationService publicProductDetailCacheApplicationService;

    @Mock
    private ProceedingJoinPoint proceedingJoinPoint;

    @Test
    @DisplayName("캐시가 비활성화면 원본 로직을 그대로 실행한다")
    void cache_disabled() throws Throwable {
        PublicProductDetailCacheAspect aspect = new PublicProductDetailCacheAspect(publicProductDetailCacheApplicationService);
        UUID productId = UUID.randomUUID();
        ProductView resolved = view(productId, "resolved");

        when(publicProductDetailCacheApplicationService.enabled()).thenReturn(false);
        when(proceedingJoinPoint.proceed()).thenReturn(resolved);

        Object result = aspect.cache(proceedingJoinPoint, productId);

        assertThat(result).isEqualTo(resolved);
        verify(publicProductDetailCacheApplicationService, never()).find(productId);
        verify(publicProductDetailCacheApplicationService, never()).save(productId, resolved);
    }

    @Test
    @DisplayName("cache hit이면 원본 로직을 실행하지 않는다")
    void cache_hit() throws Throwable {
        PublicProductDetailCacheAspect aspect = new PublicProductDetailCacheAspect(publicProductDetailCacheApplicationService);
        UUID productId = UUID.randomUUID();
        ProductView cached = view(productId, "cached");

        when(publicProductDetailCacheApplicationService.enabled()).thenReturn(true);
        when(publicProductDetailCacheApplicationService.find(productId)).thenReturn(Optional.of(cached));

        Object result = aspect.cache(proceedingJoinPoint, productId);

        assertThat(result).isEqualTo(cached);
        verify(proceedingJoinPoint, never()).proceed();
    }

    @Test
    @DisplayName("cache miss이면 원본 로직 실행 후 결과를 저장한다")
    void cache_miss() throws Throwable {
        PublicProductDetailCacheAspect aspect = new PublicProductDetailCacheAspect(publicProductDetailCacheApplicationService);
        UUID productId = UUID.randomUUID();
        ProductView resolved = view(productId, "resolved");

        when(publicProductDetailCacheApplicationService.enabled()).thenReturn(true);
        when(publicProductDetailCacheApplicationService.find(productId)).thenReturn(Optional.empty());
        when(proceedingJoinPoint.proceed()).thenReturn(resolved);

        Object result = aspect.cache(proceedingJoinPoint, productId);

        assertThat(result).isEqualTo(resolved);
        verify(publicProductDetailCacheApplicationService).save(productId, resolved);
    }

    private ProductView view(UUID productId, String name) {
        return new ProductView(
                productId,
                name,
                1000,
                10,
                "description",
                UUID.randomUUID(),
                UUID.randomUUID(),
                "brand",
                1,
                ZonedDateTime.now()
        );
    }
}
