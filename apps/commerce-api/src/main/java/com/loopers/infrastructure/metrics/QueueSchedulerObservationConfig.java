package com.loopers.infrastructure.metrics;

import com.loopers.domain.queue.EntrySchedulerObservation;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 입장 스케줄러 관측 훅을 {@link QueueInfrastructureMetrics}에 연결한다.
 * 별도 구현 클래스 없이 설정 빈 내부 익명 클래스로 등록한다.
 */
@Configuration
public class QueueSchedulerObservationConfig {

    @Bean
    EntrySchedulerObservation entrySchedulerObservation(QueueInfrastructureMetrics metrics) {
        return new EntrySchedulerObservation() {
            @Override
            public void onReleaseEntriesInvoked() {
                metrics.recordSchedulerInvocation();
            }

            @Override
            public void onLockNotAcquired() {
                metrics.recordSchedulerLockSkipped();
            }

            @Override
            public void onTickCompleted(int releasedCount) {
                metrics.recordSchedulerTickCompleted(releasedCount);
            }
        };
    }
}
