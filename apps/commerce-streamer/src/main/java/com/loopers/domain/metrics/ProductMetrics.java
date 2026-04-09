package com.loopers.domain.metrics;

/*
 * [R7 레거시 — 주석 보존]
 *
 * 본 엔티티는 R7 단일 PK 누적 집계 테이블(`product_metrics`)의 JPA 매핑이었으며,
 * R9 랭킹 파이프라인(`ProductMetricsHourly` + `product_metrics_hourly`) 도입과 함께
 * 비활성화되었다. 히스토리/참고 목적으로 보존한다.
 *
 * 대체: com.loopers.domain.ranking.ProductMetricsHourly
 * 관련 문서: docs/week9/week9.md §9
 */
