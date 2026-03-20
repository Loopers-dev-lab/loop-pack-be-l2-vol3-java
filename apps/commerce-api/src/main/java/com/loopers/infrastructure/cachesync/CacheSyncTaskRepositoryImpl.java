package com.loopers.infrastructure.cachesync;

import com.loopers.domain.cachesync.CacheSyncTask;
import com.loopers.domain.cachesync.CacheSyncTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CacheSyncTaskRepositoryImpl implements CacheSyncTaskRepository {

    private final CacheSyncTaskJpaRepository cacheSyncTaskJpaRepository;

    @Override
    public CacheSyncTask save(CacheSyncTask task) {
        return cacheSyncTaskJpaRepository.save(CacheSyncTaskEntity.from(task)).toDomain();
    }
}
