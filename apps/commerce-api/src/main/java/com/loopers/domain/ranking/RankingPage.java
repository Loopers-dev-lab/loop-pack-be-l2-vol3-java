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
 * @param rankingSnapshotId 스냅샷 조회 시 발급·요청한 UUID, 라이브 조회면 null
 * @param mvPublishVersion 주간/월간 MV 조회 시 요청 시작 시점의 활성 버전(동일 요청 내 total·rows 일관성), 일간이면 null
 */
public record RankingPage(
        List<RankingRow> rows,
        int page,
        int size,
        long totalElements,
        int totalPages,
        RankingListSource listSource,
        String rankingSnapshotId,
        Integer mvPublishVersion
) {
}
