package com.loopers.batch.job.eventhandledcleanup.step;

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
public class EventHandledCleanupTasklet implements Tasklet {

    private final EntityManager entityManager;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        log.info("[EventHandledCleanup] event_handled 7일 이전 데이터 삭제 시작");
        int deleted = entityManager.createNativeQuery(
            "DELETE FROM event_handled WHERE created_at < DATE_SUB(NOW(), INTERVAL 7 DAY)"
        ).executeUpdate();
        log.info("[EventHandledCleanup] 삭제 완료 — 삭제 행 수: {}", deleted);
        return RepeatStatus.FINISHED;
    }
}
