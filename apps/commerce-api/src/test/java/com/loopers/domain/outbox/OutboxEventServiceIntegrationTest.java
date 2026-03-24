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

    @DisplayName("발행 대기 이벤트를 조회할 때,")
    @Nested
    class FindPendingEvents {

        @DisplayName("INIT과 재시도 가능한 PUBLISH_FAILED만 조회된다.")
        @Test
        void returnsInitAndRetryableFail() {
            // arrange
            outboxEventService.save(1L, "LIKE", "LIKED", "{}", "like-liked-v1", "1");
            outboxEventService.save(2L, "LIKE", "LIKED", "{}", "like-liked-v1", "2");

            // 첫 번째 이벤트를 PUBLISHED로 변경
            OutboxEvent first = outboxEventJpaRepository.findAll().get(0);
            outboxEventService.publish(first.getId());

            // act
            List<OutboxEvent> pendingEvents = outboxEventService.findPendingEvents(10);

            // assert
            assertThat(pendingEvents).hasSize(1);
            assertThat(pendingEvents.get(0).getAggregateId()).isEqualTo(2L);
        }

        @DisplayName("PUBLISH_FAILED이면서 재시도 횟수가 상한 이상이면 조회되지 않는다.")
        @Test
        void excludesExhaustedRetries() {
            // arrange
            outboxEventService.save(1L, "LIKE", "LIKED", "{}", "like-liked-v1", "1");
            Long eventId = outboxEventJpaRepository.findAll().get(0).getId();

            // 3회 실패
            outboxEventService.publishFail(eventId);
            outboxEventService.publishFail(eventId);
            outboxEventService.publishFail(eventId);

            // act
            List<OutboxEvent> pendingEvents = outboxEventService.findPendingEvents(10);

            // assert
            assertThat(pendingEvents).isEmpty();
        }

        @DisplayName("PUBLISH_FAILED이면서 재시도 횟수가 상한 미만이면 조회된다.")
        @Test
        void includesRetryableFail() {
            // arrange
            outboxEventService.save(1L, "LIKE", "LIKED", "{}", "like-liked-v1", "1");
            Long eventId = outboxEventJpaRepository.findAll().get(0).getId();

            // 1회 실패
            outboxEventService.publishFail(eventId);

            // act
            List<OutboxEvent> pendingEvents = outboxEventService.findPendingEvents(10);

            // assert
            assertThat(pendingEvents).hasSize(1);
        }

        @DisplayName("limit 이하로 조회된다.")
        @Test
        void respectsLimit() {
            // arrange
            outboxEventService.save(1L, "LIKE", "LIKED", "{}", "like-liked-v1", "1");
            outboxEventService.save(2L, "LIKE", "LIKED", "{}", "like-liked-v1", "2");
            outboxEventService.save(3L, "LIKE", "LIKED", "{}", "like-liked-v1", "3");

            // act
            List<OutboxEvent> pendingEvents = outboxEventService.findPendingEvents(2);

            // assert
            assertThat(pendingEvents).hasSize(2);
        }
    }

    @DisplayName("발행 성공 처리할 때,")
    @Nested
    class MarkSuccess {

        @DisplayName("상태가 PUBLISHED로 갱신되고 publishedAt이 설정된다.")
        @Test
        void updatesStatusAndPublishedAt() {
            // arrange
            outboxEventService.save(1L, "LIKE", "LIKED", "{}", "like-liked-v1", "1");
            Long eventId = outboxEventJpaRepository.findAll().get(0).getId();

            // act
            outboxEventService.publish(eventId);

            // assert
            OutboxEvent event = outboxEventJpaRepository.findById(eventId).orElseThrow();
            assertAll(
                    () -> assertThat(event.getStatus()).isEqualTo(OutboxEvent.Status.PUBLISHED),
                    () -> assertThat(event.getPublishedAt()).isNotNull()
            );
        }
    }

    @DisplayName("발행 실패 처리할 때,")
    @Nested
    class MarkFail {

        @DisplayName("상태가 PUBLISH_FAILED로 갱신되고, retryCount가 증가한다.")
        @Test
        void updatesStatusToFailAndIncrementsRetryCount() {
            // arrange
            outboxEventService.save(1L, "LIKE", "LIKED", "{}", "like-liked-v1", "1");
            Long eventId = outboxEventJpaRepository.findAll().get(0).getId();

            // act
            outboxEventService.publishFail(eventId);

            // assert
            OutboxEvent event = outboxEventJpaRepository.findById(eventId).orElseThrow();
            assertAll(
                    () -> assertThat(event.getStatus()).isEqualTo(OutboxEvent.Status.PUBLISH_FAILED),
                    () -> assertThat(event.getRetryCount()).isEqualTo(1)
            );
        }
    }
}
