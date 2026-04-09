package com.loopers.application.ranking;

import java.util.List;

/**
 * 랭킹 페이지 조회 결과.
 *
 * @param rankings 순위가 포함된 상품 목록
 * @param page     현재 페이지
 * @param size     페이지 크기
 */
public record RankingPageResult(
        List<RankedProduct> rankings,
        int page,
        int size
) {
}
