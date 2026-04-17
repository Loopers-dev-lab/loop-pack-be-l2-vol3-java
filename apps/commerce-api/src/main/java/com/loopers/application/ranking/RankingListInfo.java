package com.loopers.application.ranking;

import com.loopers.domain.ranking.RankingListSource;
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
 * @param dataSource 목록 생성 경로(API {@code dataSource} 문자열과 동일 의미)
 * @param rankingSnapshotId 스냅샷 조회 시 echo, 라이브면 null
 */
public record RankingListInfo(
        List<RankingItemInfo> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        String dataSource,
        String rankingSnapshotId
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
                page.totalPages(),
                toDataSource(page.listSource()),
                page.rankingSnapshotId()
        );
    }

    /**
     * 랭킹 목록 생성 경로를 문자열로 변환한다.
     * @param source 랭킹 목록 생성 경로
     * @return 문자열
     */
    private static String toDataSource(RankingListSource source) {
        return switch (source) {
            case REDIS_ZSET -> "REDIS";
            case REDIS_ZSET_SNAPSHOT -> "REDIS_SNAPSHOT";
            case FALLBACK_DB_LATEST -> "FALLBACK_LATEST";
            case DEGRADED_EMPTY -> "DEGRADED";
            case MV_WEEKLY -> "MV_WEEKLY";
            case MV_MONTHLY -> "MV_MONTHLY";
        };
    }
}
