package com.loopers.domain.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.loopers.infrastructure.outbox.persistence.OutboxEventJpaRepository;
import com.loopers.support.BaseIntegrationTest;

class OutboxEventServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private OutboxEventService outboxEventService;

    @Autowired
    private OutboxEventJpaRepository outboxEventJpaRepository;

    @DisplayName("Outbox 이벤트를 저장할 때,")
    @Nested
    class Save {

        @DisplayName("이벤트가 INIT 상태로 저장된다.")
        @Test
        void savesWithInitStatus() {
            // act
            outboxEventService.save(
                    1L,
                    "LIKE",
                    "LIKED",
                    "{\"productId\":1}",
                    "like-liked-v1",
                    "1"
            );

            // assert
            List<OutboxEvent> events = outboxEventJpaRepository.findAll();
            assertThat(events).hasSize(1);

            OutboxEvent event = events.get(0);
            assertAll(
                    () -> assertThat(event.getAggregateId()).isEqualTo(1L),
                    () -> assertThat(event.getAggregateType()).isEqualTo("LIKE"),
                    () -> assertThat(event.getEventType()).isEqualTo("LIKED"),
                    () -> assertThat(event.getPayload()).isEqualTo("{\"productId\":1}"),
                    () -> assertThat(event.getTopic()).isEqualTo("like-liked-v1"),
                    () -> assertThat(event.getPartitionKey()).isEqualTo("1"),
                    () -> assertThat(event.getVersion()).isEqualTo(1L),
                    () -> assertThat(event.getStatus()).isEqualTo(OutboxEvent.Status.INIT),
                    () -> assertThat(event.getPublishedAt()).isNull()
            );
        }

        @DisplayName("같은 aggregate에 대해 재저장하면, 버전이 증가한다.")
        @Test
        void incrementsVersionForSameAggregate() {
            // arrange
            outboxEventService.save(1L, "LIKE", "LIKED", "{\"productId\":1}",
                    "like-liked-v1", "1");

            // act
            outboxEventService.save(1L, "LIKE", "UNLIKED", "{\"productId\":1}",
                    "like-liked-v1", "1");

            // assert
            List<OutboxEvent> events = outboxEventJpaRepository.findAll();
            assertThat(events).hasSize(2);
            assertThat(events.get(0).getVersion()).isEqualTo(1L);
            assertThat(events.get(1).getVersion()).isEqualTo(2L);
        }
    }
}
