package com.loopers.application.ranking;

import com.loopers.domain.ranking.MonthlyRankingRepository;
import com.loopers.domain.ranking.RankingEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * 월간 랭킹 Use Case Facade.
 *
 * WeeklyRankingFacade 와 처리 흐름이 동일하며, 조회 대상 저장소만 다르다.
 * MV 테이블 조회 후 상품 가시성 필터링과 RankingItemInfo 조립을 RankingAssembler 에 위임한다.
 *
 * date 가 null 이면 KST 기준 어제 날짜를 기본값으로 사용한다.
 * batch 가 targetDate - 1일을 base_date 로 적재하므로 최신 월간 랭킹의 기본 기준일은 어제다.
 */
@Component
@RequiredArgsConstructor
public class MonthlyRankingFacade {

    private final MonthlyRankingRepository monthlyRankingRepository;
    private final RankingAssembler rankingAssembler;
    private final Clock clock;

    /**
     * 월간 랭킹 목록을 조회하고 상품 정보를 합산하여 반환한다.
     *
     * @param date         조회 기준일. null 이면 KST 어제 날짜를 사용한다.
     * @param pageOneBased 1-based 페이지 번호
     * @param size         페이지 크기
     * @return 가시성 필터링 후 RankingItemInfo 목록과 페이지 메타데이터.
     */
    public RankingPageResult getMonthlyRanking(LocalDate date, int pageOneBased, int size) {
        LocalDate baseDate = date != null ? date : LocalDate.now(clock.withZone(RankingAssembler.KST)).minusDays(1);

        long total = monthlyRankingRepository.getTotal(baseDate);
        List<RankingEntry> entries = monthlyRankingRepository.getTopN(baseDate, pageOneBased, size);
        return rankingAssembler.assemble(baseDate, total, entries);
    }
}
