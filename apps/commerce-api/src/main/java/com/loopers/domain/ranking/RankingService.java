package com.loopers.domain.ranking;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
@RequiredArgsConstructor
public class RankingService {

    private static final String KEY_PREFIX = "ranking:all:";
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd");

    private final RankingRepository rankingRepository;

    /**
     * 날짜별 Top-N 랭킹 조회.
     * page/size로 offset을 계산하여 ZREVRANGE로 조회
     * ex) page=2, size=20이면 offset=20부터 20개 조회
     */
    public List<RankingEntry> getTopRankings(String date, int page, int size) {
        String key = KEY_PREFIX + date;       // "ranking:all:20260405"
        int offset = (page - 1) * size;       // 1-based page → 0-based offset
        return rankingRepository.getTopN(key, offset, size);
    }

    /**
     * DB ORDER BY 기반 Top-N 랭킹 조회 — ZSET 성능 비교용.
     * product_metrics 테이블에서 가중치 합산 점수로 정렬.
     */
    public List<RankingEntry> getTopRankingsFromDB(int page, int size) {
        int offset = (page - 1) * size;
        return rankingRepository.getTopNFromDB(offset, size);
    }

    /**
     * 특정 상품의 오늘 랭킹 순위 조회
     * ZREVRANK는 0-based이므로 +1 하여 1-based로 반환
     * ZSET에 없는 상품이면 null 반환
     */
    public Long getProductRank(Long productId) {
        String key = KEY_PREFIX + LocalDate.now().format(DATE_FORMAT);
        Long rank = rankingRepository.getRank(key, productId);
        return rank != null ? rank + 1 : null; // 0-based → 1-based
    }
}
