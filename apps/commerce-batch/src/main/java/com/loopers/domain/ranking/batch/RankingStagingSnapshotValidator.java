package com.loopers.domain.ranking.batch;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Option A publish 직전 스테이징 스냅샷 검증.
 */
public final class RankingStagingSnapshotValidator {

    private static final int TOP_LIMIT = 100;

    private RankingStagingSnapshotValidator() {
    }

    /**
     * 검증에 실패하면 {@link IllegalArgumentException}을 던진다.
     */
    public static void validateOrThrow(List<RankingStagingRankRow> rows) {
        if (rows.size() > TOP_LIMIT) {
            throw new IllegalArgumentException(
                    "스테이징 행 수는 " + TOP_LIMIT + "을 넘을 수 없습니다. 실제: " + rows.size()
            );
        }
        for (int i = 0; i < rows.size(); i++) {
            int expectedRank = i + 1;
            if (rows.get(i).rank() != expectedRank) {
                throw new IllegalArgumentException(
                        "rank는 1부터 연속이어야 합니다. 인덱스 " + i + "에서 기대 " + expectedRank
                                + ", 실제 " + rows.get(i).rank()
                );
            }
        }
        Set<Long> productIds = new HashSet<>();
        for (RankingStagingRankRow row : rows) {
            if (!productIds.add(row.productId())) {
                throw new IllegalArgumentException("product_id가 중복입니다: " + row.productId());
            }
        }
    }
}
