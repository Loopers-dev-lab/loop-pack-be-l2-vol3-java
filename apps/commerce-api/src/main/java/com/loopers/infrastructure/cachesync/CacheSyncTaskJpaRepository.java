package com.loopers.infrastructure.cachesync;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CacheSyncTaskJpaRepository extends JpaRepository<CacheSyncTaskEntity, Long> {
}
