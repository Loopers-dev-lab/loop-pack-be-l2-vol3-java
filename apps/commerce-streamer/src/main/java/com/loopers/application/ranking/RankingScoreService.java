package com.loopers.application.ranking;

import com.loopers.domain.event.OrderItemPayload;
import com.loopers.domain.ranking.RankingScoreLedger;
import com.loopers.domain.ranking.RankingScoreLedgerRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 이벤트 → ledger 누적. Redis ZSET 갱신은 RankingLedgerSyncScheduler가 별도 사이클로 수행.
 */
@Slf4j
@Service
public class RankingScoreService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter HOUR_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHH");

    private final RankingScoreLedgerRepository ledgerRepository;
    private final double viewWeight;
    private final double likeWeight;
    private final double orderWeight;

    public RankingScoreService(
        RankingScoreLedgerRepository ledgerRepository,
        @Value("${ranking.weights.view}") double viewWeight,
        @Value("${ranking.weights.like}") double likeWeight,
        @Value("${ranking.weights.order}") double orderWeight
    ) {
        double weightSum = viewWeight + likeWeight + orderWeight;
        Assert.state(Math.abs(weightSum - 1.0) < 0.001,
            "가중치 합이 1.0이어야 합니다. 현재: " + weightSum);

        this.ledgerRepository = ledgerRepository;
        this.viewWeight = viewWeight;
        this.likeWeight = likeWeight;
        this.orderWeight = orderWeight;
    }

    @Transactional
    public void addViewScore(Long productId) {
        upsertBothBuckets(productId, viewWeight);
    }

    @Transactional
    public void addViewScores(Map<Long, Integer> productCounts) {
        productCounts.forEach((productId, count) ->
            upsertBothBuckets(productId, viewWeight * count));
    }

    @Transactional
    public void addLikeScore(Long productId) {
        upsertBothBuckets(productId, likeWeight);
    }

    @Transactional
    public void addLikeScores(Map<Long, Integer> productCounts) {
        productCounts.forEach((productId, count) ->
            upsertBothBuckets(productId, likeWeight * count));
    }

    @Transactional
    public void subtractLikeScores(Map<Long, Integer> productCounts) {
        productCounts.forEach((productId, count) ->
            upsertBothBuckets(productId, -likeWeight * count));
    }

    @Transactional
    public void addOrderScores(List<OrderItemPayload> items) {
        Map<Long, Double> aggregated = new HashMap<>();
        for (OrderItemPayload item : items) {
            double rawValue = Math.max((long) item.price() * (long) item.quantity(), 1);
            double score = orderWeight * Math.log10(rawValue);
            aggregated.merge(item.productId(), score, Double::sum);
        }
        aggregated.forEach(this::upsertBothBuckets);
    }

    private void upsertBothBuckets(Long productId, double delta) {
        LocalDateTime now = LocalDateTime.now(KST);
        String dayBucket = now.toLocalDate().format(DAY_FORMAT);
        String hourBucket = now.format(HOUR_FORMAT);
        upsert(RankingScoreLedger.BucketType.DAY, dayBucket, productId, delta);
        upsert(RankingScoreLedger.BucketType.HOUR, hourBucket, productId, delta);
    }

    private void upsert(
        RankingScoreLedger.BucketType bucketType, String bucketKey, Long productId, double delta
    ) {
        RankingScoreLedger ledger = ledgerRepository.findByBucket(bucketType, bucketKey, productId)
            .orElseGet(() -> new RankingScoreLedger(bucketType, bucketKey, productId));
        ledger.addScore(delta);
        ledgerRepository.save(ledger);
    }

    // 테스트/연동에서 사용 — 오늘 날짜 day bucket key를 yyyymmdd 포맷으로 반환
    public static String todayDayBucket() {
        return LocalDate.now(KST).format(DAY_FORMAT);
    }
}
