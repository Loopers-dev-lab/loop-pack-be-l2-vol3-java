package com.loopers.infrastructure.metrics;

import com.loopers.domain.queue.EntrySchedulerLockObservation;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 대기열 스케줄러 관측 훅을 Micrometer로 연결한다.
 * 별도 리스너 클래스 없이 메서드 레퍼런스 빈으로 등록한다.
 */
@Configuration
public class QueueSchedulerObservationConfig {

    @Bean
    EntrySchedulerLockObservation entrySchedulerLockObservation(QueueInfrastructureMetrics metrics) {
        return metrics::recordSchedulerLockSkipped;
    }
}
