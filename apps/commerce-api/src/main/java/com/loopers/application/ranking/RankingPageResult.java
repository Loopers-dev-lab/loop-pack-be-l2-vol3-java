package com.loopers.application.ranking;

import java.time.LocalDate;
import java.util.List;

/**
 * 랭킹 페이지 조회 결과 VO.
 *
 * Facade 가 items, total, effectiveDate 를 하나로 묶어 반환함으로써
 * Controller 가 Facade 의 내부 날짜 계산 메서드를 직접 호출하지 않아도 된다.
 *
 * @param effectiveDate 실제 조회에 사용된 기준일 (요청 date 가 null 이면 Facade 기본값이 적용된다)
 * @param total         해당 기준일의 전체 랭킹 엔트리 수
 * @param items         가시성 필터링 후 상품 정보가 합산된 랭킹 목록
 */
public record RankingPageResult(
        LocalDate effectiveDate,
        long total,
        List<RankingItemInfo> items
) {
}
