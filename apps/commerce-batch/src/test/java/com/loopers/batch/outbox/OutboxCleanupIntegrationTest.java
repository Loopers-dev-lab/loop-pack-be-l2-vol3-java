package com.loopers.batch.outbox;

import com.loopers.infrastructure.outbox.OutboxEventModel;
import com.loopers.infrastructure.outbox.OutboxJpaRepository;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.batch.job.enabled=false",
        "outbox.relay.enabled=false",
        "outbox.cleanup.enabled=false"
})
@Import(MySqlTestContainersConfig.class)
class OutboxCleanupIntegrationTest {

    @Autowired
    private OutboxJpaRepository outboxJpaRepository;

    @Autowired
    private OutboxCleanupService outboxCleanupService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("발행 완료·보관 기간이 지난 outbox 행만 배치 삭제한다.")
    void deletePublishedOlderThan_shouldRemoveOnlyStalePublishedRows() {
        OutboxEventModel stale = outboxJpaRepository.save(OutboxEventModel.pending(
                "evt-stale",
                "product-events",
                "1",
                "TEST",
                Instant.now(),
                "{}"));
        stale.markPublished(Instant.now().minus(30, ChronoUnit.DAYS));
        outboxJpaRepository.save(stale);

        OutboxEventModel recent = outboxJpaRepository.save(OutboxEventModel.pending(
                "evt-recent",
                "product-events",
                "2",
                "TEST",
                Instant.now(),
                "{}"));
        recent.markPublished(Instant.now().minus(1, ChronoUnit.HOURS));
        outboxJpaRepository.save(recent);

        Instant cutoff = Instant.now().minus(1, ChronoUnit.DAYS);
        int deleted = outboxCleanupService.deletePublishedOlderThan(cutoff, 100);

        assertThat(deleted).isEqualTo(1);
        assertThat(outboxJpaRepository.count()).isEqualTo(1);
        assertThat(outboxJpaRepository.findAll().get(0).getEventId()).isEqualTo("evt-recent");
    }
}
