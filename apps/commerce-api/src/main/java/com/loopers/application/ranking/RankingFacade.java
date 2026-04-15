package com.loopers.application.ranking;

import com.loopers.domain.ranking.RankingPage;
import com.loopers.domain.ranking.RankingQueryService;
import com.loopers.domain.ranking.RankingRequestDate;
import com.loopers.domain.ranking.RankingSnapshotCreateResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 랭킹 유스케이스 조율: 요청 문자열을 도메인 날짜로 변환하고 {@link RankingQueryService}에 위임한다.
 * HTTP DTO와 도메인 결과를 매핑한다.
 * <p>
 * {@code date} 미지정 시 오늘(Asia/Seoul)을 쓰며, 이에 대응하는 Redis 키 {@code ranking:all:{yyyyMMdd}}를 조회한다(동적 날짜 키).
 */
@Service
public class RankingFacade {

    private final RankingQueryService rankingQueryService;

    public RankingFacade(RankingQueryService rankingQueryService) {
        this.rankingQueryService = rankingQueryService;
    }

    /**
     * 일간 인기 상품 랭킹 조회
     *
     * @param dateYyyyMmDdOptional 일자 yyyyMMdd (선택, 기본: 오늘(Asia/Seoul))
     * @param page 페이지 (1부터)
     * @param size 페이지 크기
     * @return 일간 인기 상품 랭킹 결과
     */
    @Transactional(readOnly = true)
    public RankingListInfo getRankings(String dateYyyyMmDdOptional, int page, int size) {
        return getRankings(dateYyyyMmDdOptional, page, size, Optional.empty());
    }

    /**
     * {@code rankingSnapshotId}가 있으면 해당 스냅샷 ZSET에서 오프셋 페이징한다.
     */
    @Transactional(readOnly = true)
    public RankingListInfo getRankings(
            String dateYyyyMmDdOptional, int page, int size, Optional<String> rankingSnapshotId) {
        LocalDate date = RankingRequestDate.resolveOptionalYyyyMmDd(dateYyyyMmDdOptional);
        RankingPage pageResult = rankingQueryService.loadPage(date, page, size, rankingSnapshotId);
        return RankingListInfo.from(pageResult);
    }

    /**
     * 일간 ZSET의 Redis 스냅샷을 만들고 식별자를 반환한다.
     */
    public RankingSnapshotCreateResult createRankingSnapshot(String dateYyyyMmDdOptional) {
        LocalDate date = RankingRequestDate.resolveOptionalYyyyMmDd(dateYyyyMmDdOptional);
        return rankingQueryService.createSnapshot(date);
    }
}
