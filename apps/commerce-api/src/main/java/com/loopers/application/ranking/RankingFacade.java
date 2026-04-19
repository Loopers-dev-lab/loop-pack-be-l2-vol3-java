package com.loopers.application.ranking;

import com.loopers.domain.ranking.RankingEntry;
import com.loopers.domain.ranking.RankingKey;
import com.loopers.domain.ranking.RankingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * 랭킹 Use Case Facade.
 *
 * - {@link #getDailyRanking(LocalDate, int, int)} : 랭킹 Page 조회 + 상품 정보 Aggregation
 * - {@link #getDailyRank(Long)}                   : 상품 상세의 `dailyRank` 합성용
 */
@Component
@RequiredArgsConstructor
public class RankingFacade {

    private final RankingRepository rankingRepository;
    private final RankingAssembler rankingAssembler;
    private final Clock clock;

    /**
     * 일간 랭킹을 조회하고 상품 정보를 합산하여 반환한다.
     *
     * 삭제/숨김 상태인 상품은 결과에서 제외되므로, 반환 size 가 요청 size 보다 작을 수 있다.
     * date 가 null 이면 KST 오늘 날짜로 처리한다.
     *
     * @param pageOneBased 사용자 노출 기준 페이지 번호 (1-based)
     */
    public RankingPageResult getDailyRanking(LocalDate date, int pageOneBased, int size) {
        LocalDate effectiveDate = date != null ? date : LocalDate.now(clock.withZone(RankingAssembler.KST));
        String key = RankingKey.daily(effectiveDate);

        long total = rankingRepository.getTotal(key);
        List<RankingEntry> entries = rankingRepository.getTopN(key, pageOneBased, size);
        return rankingAssembler.assemble(effectiveDate, total, entries);
    }

    /**
     * 상품 상세의 `dailyRank` 필드에 사용되는 순위 조회.
     * 순위권 밖이면 null.
     */
    public Long getDailyRank(Long productId) {
        if (productId == null) return null;
        LocalDate today = LocalDate.now(clock.withZone(RankingAssembler.KST));
        return rankingRepository.getRank(RankingKey.daily(today), productId);
    }

    /**
     * 특정 날짜의 특정 상품 순위 조회 (확장 용도).
     */
    public Long getDailyRank(Long productId, LocalDate date) {
        Objects.requireNonNull(date, "date must not be null");
        if (productId == null) return null;
        return rankingRepository.getRank(RankingKey.daily(date), productId);
    }
}
