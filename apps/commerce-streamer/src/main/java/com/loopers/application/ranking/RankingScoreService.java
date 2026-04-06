package com.loopers.application.ranking;

import com.loopers.domain.event.OrderItemPayload;
import com.loopers.support.redis.RankingKeyConstants;
import com.loopers.domain.ranking.RankingRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import org.springframework.util.Assert;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class RankingScoreService {

    private final RankingRepository rankingRepository;
    private final double viewWeight;
    private final double likeWeight;
    private final double orderWeight;
    private final long dayTtlSeconds;
    private final long hourTtlSeconds;

    public RankingScoreService(
        RankingRepository rankingRepository,
        @Value("${ranking.weights.view}") double viewWeight,
        @Value("${ranking.weights.like}") double likeWeight,
        @Value("${ranking.weights.order}") double orderWeight,
        @Value("${ranking.ttl.day-seconds}") long dayTtlSeconds,
        @Value("${ranking.ttl.hour-seconds}") long hourTtlSeconds
    ) {
        double weightSum = viewWeight + likeWeight + orderWeight;
        Assert.state(Math.abs(weightSum - 1.0) < 0.001,
            "가중치 합이 1.0이어야 합니다. 현재: " + weightSum);

        this.rankingRepository = rankingRepository;
        this.viewWeight = viewWeight;
        this.likeWeight = likeWeight;
        this.orderWeight = orderWeight;
        this.dayTtlSeconds = dayTtlSeconds;
        this.hourTtlSeconds = hourTtlSeconds;
    }

    public void addViewScore(Long productId) {
        incrementBothKeys(productId, viewWeight);
    }

    public void addViewScores(Map<Long, Integer> productCounts) {
        productCounts.forEach((productId, count) ->
            incrementBothKeys(productId, viewWeight * count));
    }

    public void addLikeScore(Long productId) {
        incrementBothKeys(productId, likeWeight);
    }

    public void addLikeScores(Map<Long, Integer> productCounts) {
        productCounts.forEach((productId, count) ->
            incrementBothKeys(productId, likeWeight * count));
    }

    public void subtractLikeScores(Map<Long, Integer> productCounts) {
        productCounts.forEach((productId, count) ->
            incrementBothKeys(productId, -likeWeight * count));
    }

    public void addOrderScores(List<OrderItemPayload> items) {
        Map<Long, Double> aggregated = new HashMap<>();
        for (OrderItemPayload item : items) {
            double rawValue = Math.max((long) item.price() * (long) item.quantity(), 1);
            double score = orderWeight * Math.log10(rawValue);
            aggregated.merge(item.productId(), score, Double::sum);
        }
        aggregated.forEach(this::incrementBothKeys);
    }

    private void incrementBothKeys(Long productId, double score) {
        LocalDateTime now = LocalDateTime.now();
        String dayKey = RankingKeyConstants.dayKey(now.toLocalDate());
        String hourKey = RankingKeyConstants.hourKey(now);
        rankingRepository.incrementScore(dayKey, productId, score, dayTtlSeconds);
        rankingRepository.incrementScore(hourKey, productId, score, hourTtlSeconds);
    }
}
