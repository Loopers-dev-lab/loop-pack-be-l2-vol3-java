package com.loopers.application.queue;

import com.loopers.domain.queue.WaitingQueueCapacityPolicy;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@code queue.join.*} 바인딩 및 대기열 정원 도메인 빈 등록.
 *
 * <p>설정 값과 {@link WaitingQueueCapacityPolicy} 빈을 한곳에 둬서 분리된 설정 클래스를 두지 않는다.
 */
@Configuration
@ConfigurationProperties(prefix = "queue.join")
public class QueueJoinProperties {

    /** 단일 eventId 기준 대기열 최대 인원. */
    private long maxWaiting = 100_000L;

    public long getMaxWaiting() {
        return maxWaiting;
    }

    public void setMaxWaiting(long maxWaiting) {
        if (maxWaiting < 1) {
            throw new IllegalArgumentException("queue.join.max-waiting must be >= 1");
        }
        this.maxWaiting = maxWaiting;
    }

    /** {@link WaitingQueueCapacityPolicy} 빈 등록 */
    @Bean
    WaitingQueueCapacityPolicy waitingQueueCapacityPolicy() {
        return new WaitingQueueCapacityPolicy(maxWaiting);
    }
}
