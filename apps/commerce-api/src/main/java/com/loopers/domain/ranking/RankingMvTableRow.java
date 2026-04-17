package com.loopers.domain.ranking;

import java.math.BigDecimal;

/**
 * MV 테이블에서 읽은 한 행(순위·상품·점수). Hydration 전 단계.
 *
 * @param rankValue   전역 순위(1-based), 컬럼 {@code rank}
 * @param productId   상품 ID
 * @param score       배치가 저장한 점수
 */
public record RankingMvTableRow(int rankValue, long productId, BigDecimal score) {
}
