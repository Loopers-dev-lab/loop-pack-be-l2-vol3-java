package com.loopers.application.ranking;

import com.loopers.domain.ranking.RankingPage;

import java.util.List;

/**
 * 랭킹 목록 응답 DTO
 *
 * @param items 랭킹 아이템 목록
 * @param page 페이지
 * @param size 페이지 크기
 * @param totalElements 총 아이템 수
 * @param totalPages 총 페이지 수
 */
public record RankingListInfo(
        List<RankingItemInfo> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static RankingListInfo from(RankingPage page) {
        List<RankingItemInfo> items = page.rows().stream()
                .map(RankingItemInfo::from)
                .toList();
        return new RankingListInfo(
                items,
                page.page(),
                page.size(),
                page.totalElements(),
                page.totalPages()
        );
    }
}
