package com.loopers.application.ranking;

import java.util.List;

import com.loopers.application.shared.annotation.UseCase;
import com.loopers.domain.ranking.RankingItem;
import com.loopers.domain.ranking.RankingService;
import com.loopers.support.page.PageSize;

import lombok.RequiredArgsConstructor;

/**
 * 시간 단위 인기 상품 랭킹을 페이지 단위로 조회한다.
 */
@UseCase
@RequiredArgsConstructor
public class ReadHourlyRankingsUseCase {

    private final RankingService rankingService;
    private final RankingResultAssembler rankingResultAssembler;

    /**
     * @param userId   사용자 ID (비로그인 시 null)
     * @param datetime 조회 시간 (yyyyMMddHH), null이면 현재 시간
     * @param pageSize 페이지 정보
     * @return 랭킹 페이지 결과 (상품 정보, brandId, 좋아요 여부 포함)
     */
    public RankingPageResult execute(Long userId, String datetime, PageSize pageSize) {
        List<RankingItem> rankingItems = rankingService.readHourlyTopRanked(
                datetime,
                pageSize.offset(),
                pageSize.size()
        );
        return rankingResultAssembler.assemble(userId, rankingItems, pageSize);
    }
}
