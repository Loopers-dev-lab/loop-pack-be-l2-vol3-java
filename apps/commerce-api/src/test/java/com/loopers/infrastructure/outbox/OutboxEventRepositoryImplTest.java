package com.loopers.infrastructure.outbox;

import com.loopers.domain.outbox.OutboxEventModel;
import com.loopers.domain.outbox.OutboxEventStatus;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({OutboxEventRepositoryImpl.class, MySqlTestContainersConfig.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@DisplayName("OutboxEventRepository 통합 테스트")
class OutboxEventRepositoryImplTest {

    @Autowired
    OutboxEventRepositoryImpl outboxEventRepository;

    @Autowired
    OutboxEventJpaRepository jpaRepository;

    @Test
    @DisplayName("저장 시 event_id가 자동 생성된다")
    void save_ShouldPersistWithAutoId() {
        OutboxEventModel model = OutboxEventModel.create(
                "ORDER", "1001", "ORDER_CREATED",
                "order-events", "1001", "{\"orderId\":1001}"
        );

        OutboxEventModel saved = outboxEventRepository.save(model);

        assertThat(saved.getEventId()).isNotNull();
        assertThat(saved.getEventId()).isGreaterThan(0L);
    }

    @Test
    @DisplayName("PENDING 상태의 이벤트만 조회된다")
    void findPendingEvents_ShouldReturnOnlyPending() {
        // PENDING 이벤트 2건
        outboxEventRepository.save(OutboxEventModel.create(
                "ORDER", "1", "ORDER_CREATED", "order-events", "1", "{}"));
        outboxEventRepository.save(OutboxEventModel.create(
                "ORDER", "2", "ORDER_CREATED", "order-events", "2", "{}"));

        // PUBLISHED 이벤트 1건
        OutboxEventModel published = OutboxEventModel.create(
                "ORDER", "3", "ORDER_CREATED", "order-events", "3", "{}");
        published.markAsPublished();
        outboxEventRepository.save(published);

        List<OutboxEventModel> pending = outboxEventRepository.findPendingEvents(10);

        assertThat(pending).hasSize(2);
        assertThat(pending).allMatch(e -> e.getStatus() == OutboxEventStatus.PENDING);
    }

    @Test
    @DisplayName("findPendingEvents는 created_at 오름차순으로 조회된다")
    void findPendingEvents_ShouldOrderByCreatedAtAsc() {
        outboxEventRepository.save(OutboxEventModel.create(
                "ORDER", "1", "EVENT_A", "order-events", "1", "{}"));
        outboxEventRepository.save(OutboxEventModel.create(
                "ORDER", "2", "EVENT_B", "order-events", "2", "{}"));

        List<OutboxEventModel> pending = outboxEventRepository.findPendingEvents(10);

        assertThat(pending).hasSize(2);
        assertThat(pending.get(0).getAggregateId()).isEqualTo("1");
        assertThat(pending.get(1).getAggregateId()).isEqualTo("2");
    }

    @Test
    @DisplayName("limit 파라미터로 조회 건수를 제한한다")
    void findPendingEvents_ShouldRespectLimit() {
        for (int i = 0; i < 5; i++) {
            outboxEventRepository.save(OutboxEventModel.create(
                    "ORDER", String.valueOf(i), "ORDER_CREATED", "order-events", String.valueOf(i), "{}"));
        }

        List<OutboxEventModel> pending = outboxEventRepository.findPendingEvents(3);

        assertThat(pending).hasSize(3);
    }

    @Test
    @DisplayName("PUBLISHED 상태의 오래된 이벤트를 삭제한다")
    void deletePublishedOlderThan_ShouldDeleteOldPublished() {
        OutboxEventModel model = OutboxEventModel.create(
                "ORDER", "1", "ORDER_CREATED", "order-events", "1", "{}");
        model.markAsPublished();
        outboxEventRepository.save(model);
        jpaRepository.flush();

        int deleted = outboxEventRepository.deletePublishedOlderThan(
                LocalDateTime.now().plusDays(1), 100);

        assertThat(deleted).isEqualTo(1);
        assertThat(jpaRepository.findAll()).isEmpty();
    }
}
