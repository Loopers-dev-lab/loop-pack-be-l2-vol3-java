package com.loopers.domain.ranking.service;

import com.loopers.domain.ranking.model.ProductMetrics;
import com.loopers.domain.ranking.repository.RankingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@RequiredArgsConstructor
@Component
public class RankingService {

    private static final String KEY_PREFIX = "ranking:all:";
    private static final long TTL_SECONDS = 2 * 24 * 60 * 60; // 2일
    private static final double VIEW_WEIGHT = 0.1;
    private static final double LIKE_WEIGHT = 0.2;
    private static final double ORDER_WEIGHT = 0.7;
    private static final double CARRY_OVER_WEIGHT = 0.1;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final RankingRepository rankingRepository;

    public void updateScore(ProductMetrics metrics) {
        double score = VIEW_WEIGHT * metrics.viewCount()
                     + LIKE_WEIGHT * metrics.likeCount()
                     + ORDER_WEIGHT * metrics.orderCount();
        String key = todayKey();
        String member = String.valueOf(metrics.productId());
        rankingRepository.updateScore(key, member, score);
    }

    public void refreshTtl() {
        String key = todayKey();
        rankingRepository.setKeyExpire(key, TTL_SECONDS);
    }

    public void carryOverScores(String fromDate, String toDate) {
        String fromKey = KEY_PREFIX + fromDate;
        String toKey = KEY_PREFIX + toDate;
        if (rankingRepository.existsKey(toKey)) {
            return;
        }
        rankingRepository.carryOverScores(fromKey, toKey, CARRY_OVER_WEIGHT);
        rankingRepository.setKeyExpire(toKey, TTL_SECONDS);
    }

    private String todayKey() {
        return KEY_PREFIX + LocalDate.now().format(DATE_FORMAT);
    }
}
