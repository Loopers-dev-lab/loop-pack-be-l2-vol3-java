package com.loopers.infrastructure.brand.redis;

import com.loopers.application.brand.BrandCacheRepository;
import com.loopers.domain.brand.Brand;
import com.loopers.infrastructure.cachesync.CacheSyncFailurePersistence;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class BrandCacheSyncer {

    private final BrandCacheRepository brandCacheRepository;
    private final CacheSyncFailurePersistence cacheSyncFailurePersistence;

    public void registerUpsert(Brand brand) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            syncUpsert(brand);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                syncUpsert(brand);
            }
        });
    }

    public void registerDelete(UUID brandId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            syncDelete(brandId);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                syncDelete(brandId);
            }
        });
    }

    public void syncUpsert(Brand brand) {
        try {
            brandCacheRepository.save(brand);
        } catch (RuntimeException e) {
            log.error("브랜드 Redis 읽기 모델 동기화에 실패했습니다. brandId={}", brand.id(), e);
            cacheSyncFailurePersistence.recordBrandUpsertFailure(brand, e);
        }
    }

    public void syncDelete(UUID brandId) {
        try {
            brandCacheRepository.delete(brandId);
        } catch (RuntimeException e) {
            log.error("브랜드 Redis 읽기 모델 삭제 동기화에 실패했습니다. brandId={}", brandId, e);
            cacheSyncFailurePersistence.recordBrandDeleteFailure(brandId, e);
        }
    }
}
