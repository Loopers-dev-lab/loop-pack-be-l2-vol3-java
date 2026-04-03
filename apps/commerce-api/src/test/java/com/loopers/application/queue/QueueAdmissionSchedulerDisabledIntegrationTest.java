package com.loopers.application.queue;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;

@DisplayName("QueueAdmissionScheduler 비활성화 테스트")
@SpringBootTest
@TestPropertySource(properties = "queue.enabled=false")
class QueueAdmissionSchedulerDisabledIntegrationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @DisplayName("queue.enabled=false이면, 스케줄러 빈이 등록되지 않는다.")
    @Test
    void schedulerBeanNotRegistered_whenQueueDisabled() {
        assertThat(applicationContext.getBeansOfType(QueueAdmissionScheduler.class)).isEmpty();
    }
}
