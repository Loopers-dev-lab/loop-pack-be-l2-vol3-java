package com.loopers.application.ranking;

import com.loopers.domain.product.event.ProductLikedEvent;
import com.loopers.domain.product.event.ProductViewedEvent;
import com.loopers.domain.ranking.RankingEventType;
import com.loopers.domain.ranking.RankingScoreService;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class RankingScoreEventHandler {

    private final RankingScoreService rankingScoreService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleProductViewed(ProductViewedEvent event) {
        try {
            rankingScoreService.updateScore(
                    event.productId(), LocalDate.now(), RankingEventType.VIEW, 0);
        } catch (Exception e) {
            log.warn("[Ranking] 조회 점수 갱신 실패 — productId={}", event.productId(), e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleProductLiked(ProductLikedEvent event) {
        try {
            rankingScoreService.updateScore(
                    event.productId(), LocalDate.now(), RankingEventType.LIKE, 0);
        } catch (Exception e) {
            log.warn("[Ranking] 좋아요 점수 갱신 실패 — productId={}", event.productId(), e);
        }
    }
}
