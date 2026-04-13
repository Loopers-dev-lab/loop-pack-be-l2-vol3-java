package com.loopers.application.ranking;

import java.util.List;

import com.loopers.application.shared.annotation.UseCase;
import com.loopers.domain.ranking.RankingItem;
import com.loopers.domain.ranking.RankingService;
import com.loopers.support.page.PageSize;

import lombok.RequiredArgsConstructor;

/**
 * 일간 인기 상품 랭킹을 페이지 단위로 조회한다.
 */
@UseCase
@RequiredArgsConstructor
public class ReadDailyRankingsUseCase {

    private final RankingService rankingService;
    private final RankingResultAssembler rankingResultAssembler;

    /**
     * @param userId   사용자 ID (비로그인 시 null)
     * @param date     조회 날짜 (yyyyMMdd), null이면 오늘
     * @param pageSize 페이지 정보
     * @return 랭킹 페이지 결과 (상품 정보, brandId, 좋아요 여부 포함)
     */
    public RankingPageResult execute(Long userId, String date, PageSize pageSize) {
        List<RankingItem> rankingItems = rankingService.readDailyTopRanked(date, pageSize.offset(), pageSize.size());
        return rankingResultAssembler.assemble(userId, rankingItems, pageSize);
    }
}
