package com.loopers.infrastructure.brand.redis;

import com.loopers.domain.brand.Brand;
import com.loopers.application.brand.BrandCacheRepository;
import com.loopers.domain.brand.vo.BrandName;
import com.loopers.infrastructure.cachesync.CacheSyncFailurePersistence;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BrandCacheSyncerTest {

    @Mock
    private BrandCacheRepository brandCacheRepository;

    @Mock
    private CacheSyncFailurePersistence cacheSyncFailurePersistence;

    @InjectMocks
    private BrandCacheSyncer brandCacheSyncer;

    @Test
    @DisplayName("브랜드 Redis 동기화가 실패하면 재처리 task를 적재한다")
    void syncUpsert_persistsRetryTask_whenRedisWriteFails() {
        Brand brand = new Brand(UUID.randomUUID(), new BrandName("브랜드"), "desc", "img");
        RuntimeException failure = new RuntimeException("redis down");
        doThrow(failure).when(brandCacheRepository).save(brand);

        brandCacheSyncer.syncUpsert(brand);

        verify(cacheSyncFailurePersistence).recordBrandUpsertFailure(brand.id(), brand, failure);
    }
}
