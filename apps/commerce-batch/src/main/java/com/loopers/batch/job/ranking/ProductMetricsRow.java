package com.loopers.batch.job.ranking;

/**
 * product_metrics 테이블의 1행을 담는 읽기 전용 DTO.
 * JdbcCursorItemReader의 RowMapper에서 생성된다.
 * commerce-batch에는 ProductMetricsModel 엔티티가 없으므로 JDBC + record로 처리.
 *
 * @param productId  상품 ID
 * @param viewCount  누적 조회수
 * @param likeCount  누적 좋아요수
 * @param salesCount 누적 판매건수
 * @param score      가중치 점수 (SQL에서 계산: view*0.1 + like*0.2 + sales*0.7)
 */
public record ProductMetricsRow(
        Long productId,
        long viewCount,
        long likeCount,
        long salesCount,
        double score
) {}
