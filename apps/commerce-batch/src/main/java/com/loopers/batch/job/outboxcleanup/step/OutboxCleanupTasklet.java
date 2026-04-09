package com.loopers.batch.job.outboxcleanup.step;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class OutboxCleanupTasklet implements Tasklet {

    private final EntityManager entityManager;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        log.info("[OutboxCleanup] event_outbox 1시간 이전 데이터 삭제 시작");
        int deleted = entityManager.createNativeQuery(
            "DELETE FROM event_outbox WHERE created_at < DATE_SUB(NOW(), INTERVAL 1 HOUR)"
        ).executeUpdate();
        log.info("[OutboxCleanup] 삭제 완료 — 삭제 행 수: {}", deleted);
        return RepeatStatus.FINISHED;
    }
}
