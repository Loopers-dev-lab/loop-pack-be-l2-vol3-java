package com.loopers.infrastructure.idempotency;

import com.loopers.domain.idempotency.EventHandledModel;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import jakarta.persistence.EntityManager;
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
@Import({EventHandledRepositoryImpl.class, MySqlTestContainersConfig.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@DisplayName("EventHandledRepository 통합 테스트")
class EventHandledRepositoryImplTest {

    @Autowired
    EventHandledRepositoryImpl eventHandledRepository;

    @Autowired
    EventHandledJpaRepository jpaRepository;

    @Autowired
    EntityManager entityManager;

    @Test
    @DisplayName("저장 후 existsById로 존재 확인이 가능하다")
    void save_ThenExistsById_ShouldReturnTrue() {
        eventHandledRepository.save(new EventHandledModel(100L));

        assertThat(eventHandledRepository.existsById(100L)).isTrue();
        assertThat(eventHandledRepository.existsById(999L)).isFalse();
    }

    @Test
    @DisplayName("동일 eventId로 중복 저장 시 예외가 발생한다")
    void save_DuplicateEventId_ShouldThrow() {
        entityManager.persist(new EventHandledModel(100L));
        entityManager.flush();
        entityManager.clear();

        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () -> {
            entityManager.persist(new EventHandledModel(100L));
            entityManager.flush();
        });
    }

    @Test
    @DisplayName("cutoff 이전 레코드를 삭제한다")
    void deleteOlderThan_ShouldDeleteOldRecords() {
        eventHandledRepository.save(new EventHandledModel(1L));
        eventHandledRepository.save(new EventHandledModel(2L));
        jpaRepository.flush();

        // 미래 시점을 cutoff으로 → 전부 삭제 대상
        int deleted = eventHandledRepository.deleteOlderThan(
                LocalDateTime.now().plusDays(1), 100);

        assertThat(deleted).isEqualTo(2);
        assertThat(jpaRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("batchSize로 삭제 건수를 제한한다")
    void deleteOlderThan_ShouldRespectBatchSize() {
        for (long i = 1; i <= 5; i++) {
            eventHandledRepository.save(new EventHandledModel(i));
        }
        jpaRepository.flush();

        int deleted = eventHandledRepository.deleteOlderThan(
                LocalDateTime.now().plusDays(1), 3);

        assertThat(deleted).isEqualTo(3);
        assertThat(jpaRepository.findAll()).hasSize(2);
    }
}
