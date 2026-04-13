package com.loopers.application.ranking;

import com.loopers.domain.ranking.ProductRankingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class RankingScoreService {

    private static final String RANKING_KEY_PREFIX = "ranking:all:";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final long TTL_SECONDS = 172_800; // 2일

    private static final double WEIGHT_VIEW = 0.1;
    private static final double WEIGHT_LIKE = 0.2;
    private static final double WEIGHT_ORDER = 0.7;

    private final ProductRankingRepository productRankingRepository;

    public void updateScore(String eventType, Long productId, int quantity) {
        double score = calculateScore(eventType, quantity);
        if (score == 0.0) {
            return;
        }

        try {
            String key = todayKey();
            productRankingRepository.incrementScore(key, productId, score);
            productRankingRepository.setTtlIfAbsent(key, TTL_SECONDS);
        } catch (Exception e) {
            log.warn("랭킹 점수 갱신 실패 (best-effort) - productId: {}, eventType: {}", productId, eventType, e);
        }
    }

    private double calculateScore(String eventType, int quantity) {
        return switch (eventType) {
            case "PRODUCT_VIEWED" -> WEIGHT_VIEW;
            case "PRODUCT_LIKED" -> WEIGHT_LIKE;
            case "PRODUCT_UNLIKED" -> -WEIGHT_LIKE;
            case "ORDER_PLACED" -> WEIGHT_ORDER;
            case "ORDER_CANCELLED" -> -WEIGHT_ORDER;
            default -> 0.0;
        };
    }

    private String todayKey() {
        return RANKING_KEY_PREFIX + LocalDate.now(ZoneId.of("Asia/Seoul")).format(DATE_FORMAT);
    }
}
