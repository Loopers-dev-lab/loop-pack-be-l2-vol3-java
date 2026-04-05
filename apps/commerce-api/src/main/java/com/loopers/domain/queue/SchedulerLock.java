package com.loopers.domain.queue;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// 스케줄러 분산 락을 관리하는 엔티티.
// 멀티 인스턴스 배포 시 동일 스케줄러가 중복 실행되는 것을 방지한다.
// lock_key별로 하나의 인스턴스만 실행 권한을 획득할 수 있으며,
// 장애 대비 locked_at 기반 만료 처리를 지원한다.
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "scheduler_lock")
public class SchedulerLock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 스케줄러를 식별하는 고유 키 (예: "QUEUE_SCHEDULER")
    @Column(name = "lock_key", nullable = false, unique = true)
    private String lockKey;

    // 현재 락이 점유 중인지 여부
    @Column(name = "locked", nullable = false)
    private boolean locked;

    // 락을 획득한 시각. 만료 판단에 사용된다.
    @Column(name = "locked_at")
    private LocalDateTime lockedAt;

    // 락을 획득한 인스턴스 식별자 (디버깅/모니터링 용도)
    @Column(name = "instance_id")
    private String instanceId;

    public SchedulerLock(String lockKey) {
        this.lockKey = lockKey;
        this.locked = false;
    }
}
