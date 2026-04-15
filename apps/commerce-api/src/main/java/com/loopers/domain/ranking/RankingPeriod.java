package com.loopers.domain.ranking;

/**
 * 랭킹 조회 기간.
 *
 * <p>WEEKLY/MONTHLY 캘린더 경계는 "월요일 오전/매월 1일 오전에 표본이 1일치" 라는 빈약성
 * 문제가 있고, 실무에선 이커머스 랭킹을 롤링 N일 (오늘 제외) 로 구현하는 것이 일반적이다
 * (설계.md 프롤로그 + 데빈/케브/앨런 멘토링 결론). 본 API 는 배치가 만드는 롤링 MV 와
 * 일관되게 LAST_7D / LAST_30D 로 노출한다.</p>
 */
public enum RankingPeriod {
    REALTIME,
    DAILY,
    LAST_7D,
    LAST_30D
}
