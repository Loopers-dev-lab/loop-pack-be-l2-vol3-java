package com.loopers.application.queue;

import com.loopers.domain.queue.EntrySchedulerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EntrySchedulerTest {

    @Mock
    private EntrySchedulerService entrySchedulerService;

    private final EntrySchedulerProperties schedulerProperties = new EntrySchedulerProperties(
            100L,
            "default",
            18,
            300L,
            5L,
            "queue:scheduler:lock",
            "queue:scheduler:heartbeat",
            35L
    );

    private EntryScheduler entryScheduler() {
        return new EntryScheduler(entrySchedulerService, schedulerProperties);
    }

    @DisplayName("releaseEntries는 설정값으로 도메인 스케줄러 서비스를 호출한다.")
    @Test
    void releaseEntries_shouldCallDomainSchedulerServiceWithProperties() {
        entryScheduler().releaseEntries();

        verify(entrySchedulerService).releaseEntries(
                "default",
                18,
                300,
                5,
                "queue:scheduler:lock",
                "queue:scheduler:heartbeat",
                35
        );
    }
}
