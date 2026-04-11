package com.loopers.application.ranking;

import com.loopers.domain.event.OrderItemPayload;
import com.loopers.domain.ranking.RankingScoreLedger;
import com.loopers.support.redis.RankingKeyConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 이벤트 → ledger 누적. Redis ZSET 갱신은 RankingLedgerSyncScheduler가 별도 사이클로 수행.
 *
 * <p>productId 단위 REQUIRES_NEW 트랜잭션으로 격리되어, 한 건 실패가 배치 전체 손실로 번지지 않는다.
 */
@Slf4j
@Service
public class RankingScoreService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final RankingLedgerWriter ledgerWriter;
    private final double viewWeight;
    private final double likeWeight;
    private final double orderWeight;

    public RankingScoreService(
        RankingLedgerWriter ledgerWriter,
        @Value("${ranking.weights.view}") double viewWeight,
        @Value("${ranking.weights.like}") double likeWeight,
        @Value("${ranking.weights.order}") double orderWeight
    ) {
        double weightSum = viewWeight + likeWeight + orderWeight;
        Assert.state(Math.abs(weightSum - 1.0) < 0.001,
            "가중치 합이 1.0이어야 합니다. 현재: " + weightSum);

        this.ledgerWriter = ledgerWriter;
        this.viewWeight = viewWeight;
        this.likeWeight = likeWeight;
        this.orderWeight = orderWeight;
    }

    public void addViewScore(Long productId) {
        applyToBothBuckets(productId, viewWeight);
    }

    public void addViewScores(Map<Long, Integer> productCounts) {
        productCounts.forEach((productId, count) ->
            applyToBothBuckets(productId, viewWeight * count));
    }

    public void addLikeScore(Long productId) {
        applyToBothBuckets(productId, likeWeight);
    }

    public void addLikeScores(Map<Long, Integer> productCounts) {
        productCounts.forEach((productId, count) ->
            applyToBothBuckets(productId, likeWeight * count));
    }

    public void subtractLikeScores(Map<Long, Integer> productCounts) {
        productCounts.forEach((productId, count) ->
            applyToBothBuckets(productId, -likeWeight * count));
    }

    /**
     * 동일 productId의 raw value를 먼저 합산한 뒤 log10을 한 번만 적용한다.
     * (이벤트 단위 log10 후 합산은 큰 주문이 지수적으로 유리해지므로 의도와 다르다)
     */
    public void addOrderScores(List<OrderItemPayload> items) {
        Map<Long, Long> rawByProduct = new HashMap<>();
        for (OrderItemPayload item : items) {
            long raw = (long) item.price() * (long) item.quantity();
            rawByProduct.merge(item.productId(), raw, Long::sum);
        }
        rawByProduct.forEach((productId, totalRaw) -> {
            double safeRaw = Math.max(totalRaw, 1L);
            double score = orderWeight * Math.log10(safeRaw);
            applyToBothBuckets(productId, score);
        });
    }

    private void applyToBothBuckets(Long productId, double delta) {
        // 0점 delta는 basePoints 변화 없이 lastScoredAt만 갱신하여 Tie-Break 순서를 오염시킨다.
        // (price=0 주문 → log10(1)=0 경로 등) 불필요한 DB write 방지 + Tie-Break 무결성 보호.
        if (delta == 0.0d) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(KST);
        String dayBucket = RankingKeyConstants.dayBucket(now.toLocalDate());
        String hourBucket = RankingKeyConstants.hourBucket(now);
        applyWithRetry(productId, RankingScoreLedger.BucketType.DAY, dayBucket, delta);
        applyWithRetry(productId, RankingScoreLedger.BucketType.HOUR, hourBucket, delta);
    }

    private void applyWithRetry(
        Long productId, RankingScoreLedger.BucketType bucketType, String bucketKey, double delta
    ) {
        try {
            ledgerWriter.upsertSingle(bucketType, bucketKey, productId, delta);
        } catch (DataIntegrityViolationException | ObjectOptimisticLockingFailureException race) {
            // INSERT race 또는 낙관적 락 충돌 — 원자 addDelta 경로로 한 번 더 시도하면 대부분 해소
            try {
                ledgerWriter.upsertSingle(bucketType, bucketKey, productId, delta);
            } catch (Exception e) {
                // 재시도 후에도 실패 — ledger 누락. 근사치 시스템 전제 하에 삼키되 운영 알람용으로 error 승격.
                // qna.md #16 의식적 수용 trade-off 참조.
                log.error("[RankingScore] ledger 적재 영구 실패(재시도 후). productId={} bucketType={} bucketKey={} delta={}",
                    productId, bucketType, bucketKey, delta, e);
            }
        } catch (Exception e) {
            log.error("[RankingScore] ledger 적재 영구 실패. productId={} bucketType={} bucketKey={} delta={}",
                productId, bucketType, bucketKey, delta, e);
        }
    }

    // 테스트/연동에서 사용 — 오늘 날짜 day bucket key를 반환
    public static String todayDayBucket() {
        return RankingKeyConstants.dayBucket(LocalDate.now(KST));
    }
}
