package com.loopers.batch.job.likesync.step;

import com.loopers.batch.cache.LikeCountBatchCacheService;
import com.loopers.batch.job.likesync.LikeCountSyncJobConfig;
import com.loopers.batch.persistence.LikeBatchRepository;
import com.loopers.batch.persistence.ProductBatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 좋아요 카운트 동기화 Tasklet.
 * Redis dirty set에 등록된 상품들의 좋아요 수를 DB로 동기화.
 */
@Slf4j
@StepScope
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = LikeCountSyncJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Component
public class LikeCountSyncTasklet implements Tasklet {

    private final LikeCountBatchCacheService likeCountCacheService;
    private final LikeBatchRepository likeRepository;
    private final ProductBatchRepository productRepository;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        log.info("좋아요 카운트 동기화 시작");

        Set<Long> dirtyProductIds = likeCountCacheService.getDirtyProductIds();

        if (dirtyProductIds.isEmpty()) {
            log.info("동기화할 상품이 없습니다.");
            return RepeatStatus.FINISHED;
        }

        log.info("동기화 대상 상품 수: {}", dirtyProductIds.size());

        int successCount = 0;
        int failCount = 0;

        for (Long productId : dirtyProductIds) {
            try {
                syncProductLikeCount(productId);
                successCount++;
            } catch (Exception e) {
                log.error("상품 좋아요 수 동기화 실패: productId={}", productId, e);
                failCount++;
            }
        }

        log.info("좋아요 카운트 동기화 완료 - 성공: {}, 실패: {}", successCount, failCount);

        for (int i = 0; i < successCount; i++) {
            contribution.incrementWriteCount(1);
        }

        return RepeatStatus.FINISHED;
    }

    private void syncProductLikeCount(Long productId) {
        long actualCount = likeRepository.countByProductId(productId);

        int updated = productRepository.updateLikeCount(productId, actualCount);

        if (updated > 0) {
            likeCountCacheService.set(productId, actualCount);
            likeCountCacheService.removeDirty(productId);
            log.debug("상품 좋아요 수 동기화: productId={}, count={}", productId, actualCount);
        } else {
            log.warn("상품이 존재하지 않음: productId={}", productId);
        }
    }
}
