package com.loopers.application.product;

import com.loopers.application.product.cache.PublicProductDetailCacheProperties;
import com.loopers.application.product.cache.PublicProductDetailCacheRepository;
import com.loopers.application.product.cache.PublicProductDetailCacheTtlPolicy;
import com.loopers.application.product.view.ProductView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicProductDetailCacheApplicationServiceTest {

    @Mock
    private PublicProductDetailCacheRepository publicProductDetailCacheRepository;

    private final PublicProductDetailCacheTtlPolicy publicProductDetailCacheTtlPolicy =
            new PublicProductDetailCacheTtlPolicy(new PublicProductDetailCacheProperties(true, Duration.ofSeconds(30), false, 0.0));

    @Test
    @DisplayName("캐시가 비활성화면 enabled가 false를 반환한다")
    void enabled_disabled_false() {
        PublicProductDetailCacheApplicationService service = new PublicProductDetailCacheApplicationService(
                publicProductDetailCacheRepository,
                new PublicProductDetailCacheProperties(false, Duration.ofSeconds(30), false, 0.0),
                publicProductDetailCacheTtlPolicy
        );

        assertThat(service.enabled()).isFalse();
    }

    @Test
    @DisplayName("productId 기반으로 상세 캐시 키를 생성한다")
    void buildCacheKey_usesProductId() {
        PublicProductDetailCacheApplicationService service = new PublicProductDetailCacheApplicationService(
                publicProductDetailCacheRepository,
                new PublicProductDetailCacheProperties(true, Duration.ofSeconds(30), false, 0.0),
                publicProductDetailCacheTtlPolicy
        );
        UUID productId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        assertThat(service.buildCacheKey(productId))
                .isEqualTo("product:public-detail:v1:id=11111111-1111-1111-1111-111111111111");
    }

    @Test
    @DisplayName("find는 repository 조회를 위임한다")
    void find_delegatesToRepository() {
        PublicProductDetailCacheApplicationService service = new PublicProductDetailCacheApplicationService(
                publicProductDetailCacheRepository,
                new PublicProductDetailCacheProperties(true, Duration.ofSeconds(30), false, 0.0),
                publicProductDetailCacheTtlPolicy
        );
        UUID productId = UUID.randomUUID();
        ProductView view = view(productId);
        String cacheKey = service.buildCacheKey(productId);
        when(publicProductDetailCacheRepository.findByKey(cacheKey)).thenReturn(Optional.of(view));

        Optional<ProductView> cached = service.find(productId);

        assertThat(cached).contains(view);
    }

    @Test
    @DisplayName("save는 설정된 TTL로 repository 저장을 위임한다")
    void save_delegatesWithConfiguredTtl() {
        Duration ttl = Duration.ofSeconds(45);
        PublicProductDetailCacheTtlPolicy ttlPolicy =
                new PublicProductDetailCacheTtlPolicy(new PublicProductDetailCacheProperties(true, ttl, false, 0.0));
        PublicProductDetailCacheApplicationService service = new PublicProductDetailCacheApplicationService(
                publicProductDetailCacheRepository,
                new PublicProductDetailCacheProperties(true, ttl, false, 0.0),
                ttlPolicy
        );
        UUID productId = UUID.randomUUID();
        ProductView view = view(productId);

        service.save(productId, view);

        verify(publicProductDetailCacheRepository).save(service.buildCacheKey(productId), view, ttl);
    }

    @Test
    @DisplayName("evict는 productId 키를 제거한다")
    void evict_delegatesToRepository() {
        PublicProductDetailCacheApplicationService service = new PublicProductDetailCacheApplicationService(
                publicProductDetailCacheRepository,
                new PublicProductDetailCacheProperties(true, Duration.ofSeconds(30), false, 0.0),
                publicProductDetailCacheTtlPolicy
        );
        UUID productId = UUID.randomUUID();

        service.evict(productId);

        verify(publicProductDetailCacheRepository).evict(service.buildCacheKey(productId));
    }

    private ProductView view(UUID productId) {
        return new ProductView(
                productId,
                "name",
                1000,
                5,
                "desc",
                UUID.randomUUID(),
                UUID.randomUUID(),
                "brand",
                1,
                ZonedDateTime.now()
        );
    }
}
