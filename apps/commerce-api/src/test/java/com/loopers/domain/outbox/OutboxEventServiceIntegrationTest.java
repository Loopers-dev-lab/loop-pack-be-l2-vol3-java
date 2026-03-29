package com.loopers.domain.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.List;
import java.util.UUID;

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
            // arrange
            UUID eventId = UUID.randomUUID();

            // act
            outboxEventService.save(eventId, 1L, "LIKE", "LIKED", "{\"productId\":1}", "like-liked-v1", "1");

            // assert
            assertThat(outboxEventJpaRepository.findById(eventId)).isPresent();
        }

        @DisplayName("같은 aggregate에 대해 재저장하면, 버전이 증가한다.")
        @Test
        void incrementsVersionForSameAggregate() {
            // arrange
            outboxEventService.save(
                    UUID.randomUUID(),
                    1L,
                    "LIKE",
                    "LIKED",
                    "{\"productId\":1}",
                    "like-liked-v1","1"
            );

            // act
            outboxEventService.save(
                    UUID.randomUUID(),
                    1L,
                    "LIKE",
                    "UNLIKED",
                    "{\"productId\":1}",
                    "like-liked-v1",
                    "1"
            );

            // assert
            List<OutboxEvent> events = outboxEventJpaRepository.findAll();
            assertThat(events).hasSize(2);
            assertThat(events).extracting(OutboxEvent::getVersion)
                    .containsExactlyInAnyOrder(1L, 2L);
        }
    }

    @DisplayName("발행 대기 이벤트를 조회할 때,")
    @Nested
    class FindPendingEvents {

        @DisplayName("INIT과 재시도 가능한 PUBLISH_FAILED만 조회된다.")
        @Test
        void returnsInitAndRetryableFail() {
            // arrange
            outboxEventService.save(
                    UUID.randomUUID(),
                    1L,
                    "LIKE",
                    "LIKED",
                    "{}",
                    "like-liked-v1",
                    "1"
            );
            outboxEventService.save(
                    UUID.randomUUID(),
                    2L,
                    "LIKE",
                    "LIKED",
                    "{}",
                    "like-liked-v1",
                    "2"
            );

            OutboxEvent target = outboxEventJpaRepository.findAll().stream()
                    .filter(e -> e.getAggregateId().equals(1L))
                    .findFirst()
                    .orElseThrow();
            outboxEventService.publish(target.getId());

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
            outboxEventService.save(
                    UUID.randomUUID(),
                    1L,
                    "LIKE",
                    "LIKED",
                    "{}",
                    "like-liked-v1",
                    "1"
            );
            UUID eventId = outboxEventJpaRepository.findAll().get(0).getId();

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
            outboxEventService.save(
                    UUID.randomUUID(),
                    1L,
                    "LIKE",
                    "LIKED",
                    "{}",
                    "like-liked-v1",
                    "1"
            );
            UUID eventId = outboxEventJpaRepository.findAll().get(0).getId();

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
            outboxEventService.save(
                    UUID.randomUUID(),
                    1L,
                    "LIKE",
                    "LIKED",
                    "{}",
                    "like-liked-v1",
                    "1"
            );
            outboxEventService.save(
                    UUID.randomUUID(),
                    2L,
                    "LIKE",
                    "LIKED",
                    "{}",
                    "like-liked-v1",
                    "2"
            );
            outboxEventService.save(
                    UUID.randomUUID(),
                    3L,
                    "LIKE",
                    "LIKED",
                    "{}",
                    "like-liked-v1",
                    "3"
            );

            // act
            List<OutboxEvent> pendingEvents = outboxEventService.findPendingEvents(2);

            // assert
            assertThat(pendingEvents).hasSize(2);
        }
    }

    @DisplayName("발행 성공 처리할 때,")
    @Nested
    class Publish {

        @DisplayName("INIT 상태이면 PUBLISHED로 갱신되고 true를 반환한다.")
        @Test
        void updatesInitToPublished() {
            // arrange
            UUID eventId = UUID.randomUUID();
            outboxEventService.save(eventId, 1L, "LIKE", "LIKED", "{}", "like-liked-v1", "1");

            // act
            boolean result = outboxEventService.publish(eventId);

            // assert
            assertThat(result).isTrue();
            OutboxEvent event = outboxEventJpaRepository.findById(eventId).orElseThrow();
            assertAll(
                    () -> assertThat(event.getStatus()).isEqualTo(OutboxEvent.Status.PUBLISHED),
                    () -> assertThat(event.getPublishedAt()).isNotNull()
            );
        }

        @DisplayName("이미 PUBLISHED 상태이면 false를 반환한다.")
        @Test
        void returnsFalseWhenAlreadyPublished() {
            // arrange
            UUID eventId = UUID.randomUUID();
            outboxEventService.save(eventId, 1L, "LIKE", "LIKED", "{}", "like-liked-v1", "1");
            outboxEventService.publish(eventId);

            // act
            boolean result = outboxEventService.publish(eventId);

            // assert
            assertThat(result).isFalse();
        }

        @DisplayName("PUBLISH_FAILED 상태이면 PUBLISHED로 갱신되고 true를 반환한다.")
        @Test
        void updatesPublishFailedToPublished() {
            // arrange
            UUID eventId = UUID.randomUUID();
            outboxEventService.save(eventId, 1L, "LIKE", "LIKED", "{}", "like-liked-v1", "1");
            outboxEventService.publishFail(eventId);

            // act
            boolean result = outboxEventService.publish(eventId);

            // assert
            assertThat(result).isTrue();
            OutboxEvent event = outboxEventJpaRepository.findById(eventId).orElseThrow();
            assertThat(event.getStatus()).isEqualTo(OutboxEvent.Status.PUBLISHED);
        }
    }

    @DisplayName("발행 실패 처리할 때,")
    @Nested
    class MarkFail {

        @DisplayName("상태가 PUBLISH_FAILED로 갱신되고, retryCount가 증가한다.")
        @Test
        void updatesStatusToFailAndIncrementsRetryCount() {
            // arrange
            outboxEventService.save(
                    UUID.randomUUID(),
                    1L,
                    "LIKE",
                    "LIKED",
                    "{}",
                    "like-liked-v1",
                    "1"
            );
            UUID eventId = outboxEventJpaRepository.findAll().get(0).getId();

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
