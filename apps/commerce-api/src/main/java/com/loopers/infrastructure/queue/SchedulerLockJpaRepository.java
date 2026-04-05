package com.loopers.infrastructure.queue;

import com.loopers.domain.queue.SchedulerLock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SchedulerLockJpaRepository extends JpaRepository<SchedulerLock, Long> {

    // 락 획득: locked=false이거나 locked_at이 만료 시간을 초과한 경우에만 UPDATE 성공.
    // 반환값은 영향받은 행 수 (1이면 획득 성공, 0이면 이미 다른 인스턴스가 점유 중).
    @Modifying
    @Query("UPDATE SchedulerLock s SET s.locked = true, s.lockedAt = CURRENT_TIMESTAMP, s.instanceId = :instanceId " +
            "WHERE s.lockKey = :lockKey AND (s.locked = false OR s.lockedAt < :expireThreshold)")
    int tryAcquire(@Param("lockKey") String lockKey,
                   @Param("instanceId") String instanceId,
                   @Param("expireThreshold") java.time.LocalDateTime expireThreshold);

    // 락 해제: 자신이 소유한 락만 해제한다. instanceId가 일치하지 않으면 UPDATE 0건.
    @Modifying
    @Query("UPDATE SchedulerLock s SET s.locked = false, s.instanceId = null WHERE s.lockKey = :lockKey AND s.instanceId = :instanceId")
    int release(@Param("lockKey") String lockKey, @Param("instanceId") String instanceId);
}
