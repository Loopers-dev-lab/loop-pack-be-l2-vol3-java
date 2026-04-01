package com.loopers.infrastructure.event;

import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class EventHandledEntityTest {

    @Autowired
    private EventHandledJpaRepository eventHandledJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("이벤트 처리 기록을 저장할 수 있다")
    void save_event_handled() {
        // given
        EventHandledEntity entity = EventHandledEntity.of("event-123", "order-events-v1");

        // when
        EventHandledEntity saved = eventHandledJpaRepository.save(entity);

        // then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getEventId()).isEqualTo("event-123");
        assertThat(saved.getTopic()).isEqualTo("order-events-v1");
        assertThat(saved.getHandledAt()).isNotNull();
    }

    @Test
    @DisplayName("이미 처리한 eventId를 조회할 수 있다")
    void exists_by_event_id() {
        // given
        eventHandledJpaRepository.save(EventHandledEntity.of("event-123", "order-events-v1"));

        // when & then
        assertThat(eventHandledJpaRepository.existsByEventId("event-123")).isTrue();
        assertThat(eventHandledJpaRepository.existsByEventId("event-999")).isFalse();
    }

    @Test
    @DisplayName("같은 eventId를 중복 저장하면 UNIQUE 제약 조건 위반 예외가 발생한다")
    void duplicate_event_id_throws_exception() {
        // given
        eventHandledJpaRepository.save(EventHandledEntity.of("event-123", "order-events-v1"));

        // when & then
        assertThatThrownBy(() ->
                eventHandledJpaRepository.saveAndFlush(EventHandledEntity.of("event-123", "order-events-v1"))
        ).isInstanceOf(DataIntegrityViolationException.class);
    }
}
