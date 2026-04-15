package com.loopers.domain.ranking;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

/**
 * 랭킹 단일 항목.
 *
 * @param rank      1-based 순위
 * @param productId 상품 ID
 * @param score     가중치 기반 누적 점수
 */
public record RankingItem(int rank, Long productId, double score) {

    /**
     * 엔티티 목록을 {@link RankingItem} 목록으로 변환한다.
     *
     * @param items       원본 목록
     * @param offset      시작 오프셋 (rank 계산용)
     * @param toProductId 상품 ID 추출 함수
     * @param toScore     점수 추출 함수
     * @return 순위가 포함된 랭킹 항목 목록
     */
    public static <T> List<RankingItem> toRankingItems(
            List<T> items,
            int offset,
            Function<T, Long> toProductId,
            ToDoubleFunction<T> toScore
    ) {
        List<RankingItem> rankingItems = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            T item = items.get(i);
            rankingItems.add(new RankingItem(offset + i + 1, toProductId.apply(item), toScore.applyAsDouble(item)));
        }
        return rankingItems;
    }
}
