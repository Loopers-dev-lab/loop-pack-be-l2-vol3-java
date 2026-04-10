package com.loopers.domain.ranking;

import java.util.List;

/**
 * 랭킹 페이지 도메인 목록 결과
 *
 * @param rows 랭킹 목록 아이템 목록
 * @param page 페이지 (1부터)
 * @param size 페이지 크기
 * @param totalElements 총 아이템 수
 * @param totalPages 총 페이지 수 (1부터)
 * @param listSource 목록 생성 경로(Redis / DB fallback / degraded)
 */
public record RankingPage(
        List<RankingRow> rows,
        int page,
        int size,
        long totalElements,
        int totalPages,
        RankingListSource listSource
) {
}
