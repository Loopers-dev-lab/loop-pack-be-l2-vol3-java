package com.loopers.infrastructure.idempotency;

import com.loopers.domain.idempotency.EventLogModel;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({EventLogRepositoryImpl.class, MySqlTestContainersConfig.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@DisplayName("EventLogRepository 통합 테스트")
class EventLogRepositoryImplTest {

    @Autowired
    EventLogRepositoryImpl eventLogRepository;

    @Autowired
    EventLogJpaRepository jpaRepository;

    @Test
    @DisplayName("success 로그를 저장하면 log_id가 자동 생성된다")
    void save_SuccessLog_ShouldPersistWithAutoId() {
        EventLogModel log = EventLogModel.success(1L, "ORDER_CREATED", "order-events");

        EventLogModel saved = eventLogRepository.save(log);

        assertThat(saved.getLogId()).isNotNull();
        assertThat(saved.getLogId()).isGreaterThan(0L);
    }

    @Test
    @DisplayName("failed 로그를 저장하면 에러 메시지가 포함된다")
    void save_FailedLog_ShouldContainErrorMessage() {
        EventLogModel log = EventLogModel.failed(1L, "ORDER_CREATED", "order-events", "parse error");

        EventLogModel saved = eventLogRepository.save(log);

        assertThat(saved.getErrorMessage()).isEqualTo("parse error");
    }

    @Test
    @DisplayName("cutoff 이전 레코드를 삭제한다")
    void deleteOlderThan_ShouldDeleteOldRecords() {
        eventLogRepository.save(EventLogModel.success(1L, "EVENT_A", "topic-a"));
        eventLogRepository.save(EventLogModel.success(2L, "EVENT_B", "topic-b"));
        jpaRepository.flush();

        int deleted = eventLogRepository.deleteOlderThan(
                LocalDateTime.now().plusDays(1), 100);

        assertThat(deleted).isEqualTo(2);
    }
}
