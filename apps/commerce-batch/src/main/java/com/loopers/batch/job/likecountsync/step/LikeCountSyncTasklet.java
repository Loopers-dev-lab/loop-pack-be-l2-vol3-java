package com.loopers.batch.job.likecountsync.step;

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
public class LikeCountSyncTasklet implements Tasklet {

    private final EntityManager entityManager;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        log.info("[LikeCountSync] 1단계: likes 테이블 → product_like_stats 동기화 시작");
        int synced = entityManager.createNativeQuery(
            "REPLACE INTO product_like_stats (product_id, like_count, synced_at) "
                + "SELECT l.product_id, COUNT(*), NOW() FROM likes l GROUP BY l.product_id"
        ).executeUpdate();
        log.info("[LikeCountSync] 1단계 완료 — 동기화 행 수: {}", synced);

        log.info("[LikeCountSync] 2단계: product.like_count 드리프트 보정 시작");
        int corrected = entityManager.createNativeQuery(
            "UPDATE product p JOIN product_like_stats pls ON p.id = pls.product_id "
                + "SET p.like_count = pls.like_count "
                + "WHERE p.like_count != pls.like_count AND p.deleted_at IS NULL"
        ).executeUpdate();
        log.info("[LikeCountSync] 2단계 완료 — 보정된 상품 수: {}", corrected);

        return RepeatStatus.FINISHED;
    }
}
