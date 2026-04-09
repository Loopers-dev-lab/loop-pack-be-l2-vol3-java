package com.loopers.infrastructure.category.redis;

import com.loopers.application.coupon.category.CategoryCacheRepository;
import com.loopers.application.coupon.category.CategoryCacheSyncPort;
import com.loopers.domain.category.Category;
import com.loopers.infrastructure.cachesync.CacheSyncFailurePersistence;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collection;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class CategoryCacheSyncer implements CategoryCacheSyncPort {

    private final CategoryCacheRepository categoryCacheRepository;
    private final CacheSyncFailurePersistence cacheSyncFailurePersistence;

    public void registerUpsert(Category category) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            syncUpsert(category);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                syncUpsert(category);
            }
        });
    }

    public void registerUpsertAll(Collection<Category> categories) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            syncUpsertAll(categories);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                syncUpsertAll(categories);
            }
        });
    }

    public void registerDelete(UUID categoryId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            syncDelete(categoryId);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                syncDelete(categoryId);
            }
        });
    }

    public void syncUpsert(Category category) {
        try {
            categoryCacheRepository.save(category);
        } catch (RuntimeException e) {
            log.error("카테고리 Redis 읽기 모델 동기화에 실패했습니다. categoryId={}", category.id(), e);
            cacheSyncFailurePersistence.recordCategoryUpsertFailure(category.id(), CategoryCacheDocument.from(category), e);
        }
    }

    public void syncUpsertAll(Collection<Category> categories) {
        for (Category category : categories) {
            syncUpsert(category);
        }
    }

    public void syncDelete(UUID categoryId) {
        try {
            categoryCacheRepository.delete(categoryId);
        } catch (RuntimeException e) {
            log.error("카테고리 Redis 읽기 모델 삭제 동기화에 실패했습니다. categoryId={}", categoryId, e);
            cacheSyncFailurePersistence.recordCategoryDeleteFailure(categoryId, e);
        }
    }
}
