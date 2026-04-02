package com.loopers.application.order.queue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderAdmissionSchedulerTest {

    @Mock
    private OrderAdmissionApplicationService orderAdmissionApplicationService;

    @InjectMocks
    private OrderAdmissionScheduler orderAdmissionScheduler;

    @Test
    @DisplayName("스케줄러 실행 시 입장 토큰 발급을 위임한다")
    void scheduleDelegatesIssueAdmissions() {
        orderAdmissionScheduler.schedule();

        verify(orderAdmissionApplicationService).issueAdmissions();
    }
}
