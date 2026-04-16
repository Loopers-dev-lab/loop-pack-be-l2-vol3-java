package com.loopers.batch.listener;

import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;

import java.time.LocalDate;
import java.util.function.Consumer;

/**
 * Step 시작 전 MV 테이블의 해당 baseDate 데이터를 삭제하는 리스너.
 *
 * Reader 가 0건을 반환하면 write() 가 호출되지 않아 Writer 내 DELETE 가 실행되지 않는다.
 * 이 경우 이전 실행의 stale 데이터가 MV 에 남는 문제를 방지하기 위해
 * beforeStep() 에서 항상 사전 삭제를 수행한다.
 */
public class RankingMvCleanupListener implements StepExecutionListener {

    private final Consumer<LocalDate> deleteAction;

    public RankingMvCleanupListener(Consumer<LocalDate> deleteAction) {
        this.deleteAction = deleteAction;
    }

    @Override
    public void beforeStep(StepExecution stepExecution) {
        LocalDate targetDate = stepExecution.getJobParameters().getLocalDate("targetDate");
        if (targetDate == null) {
            throw new IllegalStateException("JobParameter 'targetDate' is required");
        }
        LocalDate baseDate = targetDate.minusDays(1);
        deleteAction.accept(baseDate);
    }
}
