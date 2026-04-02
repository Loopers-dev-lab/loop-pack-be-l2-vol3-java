package com.loopers.infrastructure.queue;

import com.loopers.domain.queue.SchedulerLockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

// SchedulerLockRepository의 JPA 구현체.
// REQUIRES_NEW 트랜잭션으로 락 획득/해제를 수행하여
// 스케줄러의 비즈니스 트랜잭션과 분리한다.
@RequiredArgsConstructor
@Repository
public class SchedulerLockRepositoryImpl implements SchedulerLockRepository {

    private final SchedulerLockJpaRepository schedulerLockJpaRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Override
    public boolean tryAcquire(String lockKey, String instanceId, long expireSeconds) {
        LocalDateTime expireThreshold = LocalDateTime.now().minusSeconds(expireSeconds);
        int updated = schedulerLockJpaRepository.tryAcquire(lockKey, instanceId, expireThreshold);
        return updated > 0;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Override
    public void release(String lockKey, String instanceId) {
        schedulerLockJpaRepository.release(lockKey, instanceId);
    }
}
