package com.loopers.domain.ranking;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;


@Component
@RequiredArgsConstructor
public class RankingService {

    // Key 형식: "ranking:all:20260405" (일별 분리)
    private static final String KEY_PREFIX = "ranking:all:";
    // TTL 2일: 오늘 + 어제 키를 유지 (일별 윈도우의 2배)
    private static final long TTL_SECONDS = 2 * 24 * 60 * 60;
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd");

    private final RankingRepository rankingRepository;

    /** 상품 조회 이벤트 → ZINCRBY +0.1 */
    public void addViewScore(Long productId) {
        String key = todayKey();
        rankingRepository.incrementScore(key, productId, RankingScoreWeight.VIEW);
        rankingRepository.setTtl(key, TTL_SECONDS);
    }

    /** 좋아요 이벤트 → ZINCRBY +0.2 */
    public void addLikeScore(Long productId) {
        String key = todayKey();
        rankingRepository.incrementScore(key, productId, RankingScoreWeight.LIKE);
        rankingRepository.setTtl(key, TTL_SECONDS);
    }

    /** 주문 이벤트 → ZINCRBY +(0.7 * 수량). 상품별로 호출 */
    public void addOrderScore(Long productId, int quantity) {
        String key = todayKey();
        double score = RankingScoreWeight.ORDER * quantity;
        rankingRepository.incrementScore(key, productId, score);
        rankingRepository.setTtl(key, TTL_SECONDS);
    }

    /** 오늘 날짜 기반 ZSET 키 생성.
     * ex) "ranking:all:20260405" */
    private String todayKey() {
        return KEY_PREFIX + LocalDate.now().format(DATE_FORMAT);
    }
}
