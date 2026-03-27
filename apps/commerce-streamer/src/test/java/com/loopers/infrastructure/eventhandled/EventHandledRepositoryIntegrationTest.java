package com.loopers.infrastructure.eventhandled;

import com.loopers.domain.eventhandled.EventHandledModel;
import com.loopers.domain.eventhandled.EventHandledRepository;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(MySqlTestContainersConfig.class)
@DisplayName("EventHandledRepository 통합 테스트")
class EventHandledRepositoryIntegrationTest {

    @Autowired
    private EventHandledRepository eventHandledRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("저장된 eventId는 existsByEventId()가 true를 반환한다")
    void existsByEventId_returnsTrue_afterSave() {
        String eventId = UUID.randomUUID().toString();
        eventHandledRepository.save(EventHandledModel.create(eventId, "catalog-events"));

        assertThat(eventHandledRepository.existsByEventId(eventId)).isTrue();
    }

    @Test
    @DisplayName("저장되지 않은 eventId는 existsByEventId()가 false를 반환한다")
    void existsByEventId_returnsFalse_forUnknown() {
        assertThat(eventHandledRepository.existsByEventId(UUID.randomUUID().toString())).isFalse();
    }

    @Test
    @DisplayName("동일한 eventId로 중복 저장 시 예외가 발생한다 — PK 중복 방어 검증")
    void save_duplicateEventId_throwsException() {
        String eventId = UUID.randomUUID().toString();
        eventHandledRepository.save(EventHandledModel.create(eventId, "catalog-events"));

        assertThatThrownBy(() ->
                eventHandledRepository.save(EventHandledModel.create(eventId, "catalog-events")))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("서로 다른 eventId는 각각 독립적으로 저장된다")
    void save_differentEventIds_savedIndependently() {
        String eventId1 = UUID.randomUUID().toString();
        String eventId2 = UUID.randomUUID().toString();

        eventHandledRepository.save(EventHandledModel.create(eventId1, "catalog-events"));
        eventHandledRepository.save(EventHandledModel.create(eventId2, "order-events"));

        assertThat(eventHandledRepository.existsByEventId(eventId1)).isTrue();
        assertThat(eventHandledRepository.existsByEventId(eventId2)).isTrue();
    }
}
