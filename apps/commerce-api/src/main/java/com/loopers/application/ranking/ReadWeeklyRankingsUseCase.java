package com.loopers.application.ranking;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.loopers.application.shared.annotation.UseCase;
import com.loopers.domain.ranking.ProductRankingWeekly;
import com.loopers.domain.ranking.RankingItem;
import com.loopers.domain.ranking.RankingService;
import com.loopers.support.page.PageSize;

import lombok.RequiredArgsConstructor;

/**
 * 주간 인기 상품 랭킹을 페이지 단위로 조회한다.
 *
 * <p>배치가 집계한 지정 scoreDate의 주간 랭킹을 반환한다.</p>
 */
@UseCase
@RequiredArgsConstructor
public class ReadWeeklyRankingsUseCase {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final RankingService rankingService;
    private final RankingResultAssembler rankingResultAssembler;

    /**
     * @param userId   사용자 ID (비로그인 시 null)
     * @param date     조회 기준일 (yyyyMMdd 형식)
     * @param pageSize 페이지 정보
     * @return 랭킹 페이지 결과 (상품 정보, brandId, 좋아요 여부 포함)
     */
    public RankingPageResult execute(Long userId, String date, PageSize pageSize) {
        LocalDate scoreDate = LocalDate.parse(date, DATE_FORMAT);
        List<ProductRankingWeekly> rankings = rankingService.readWeeklyTopRanked(
                scoreDate, pageSize.page(), pageSize.size());

        List<RankingItem> rankingItems = RankingItem.toRankingItems(
                rankings,
                pageSize.offset(),
                ProductRankingWeekly::getProductId,
                ProductRankingWeekly::getScore
        );
        return rankingResultAssembler.assemble(userId, rankingItems, pageSize);
    }
}
