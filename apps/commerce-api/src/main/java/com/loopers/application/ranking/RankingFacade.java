package com.loopers.application.ranking;

import com.loopers.application.product.ProductFacade;
import com.loopers.application.product.ProductInfo;
import com.loopers.domain.ranking.RankingEntry;
import com.loopers.domain.ranking.RankingKey;
import com.loopers.domain.ranking.RankingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 랭킹 Use Case Facade.
 *
 * - {@link #getDailyRanking(LocalDate, int, int)} : 랭킹 Page 조회 + 상품 정보 Aggregation
 * - {@link #getDailyRank(Long)}                   : 상품 상세의 `dailyRank` 합성용
 * - {@link #getDailyTotal(LocalDate)}             : 페이지네이션 totalElements 제공
 */
@Component
@RequiredArgsConstructor
public class RankingFacade {

    public static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final RankingRepository rankingRepository;
    private final ProductFacade productFacade;
    private final Clock clock;

    /**
     * KST 기준 일간 랭킹 상위 항목을 조회한다.
     *
     * 삭제/숨김 상태인 상품은 결과에서 제외되므로, 반환 size 가 요청 size 보다 작을 수 있다.
     * date 가 null 이면 오늘 날짜로 처리한다.
     *
     * @param pageOneBased 사용자 노출 기준 페이지 번호 (1-based)
     */
    public List<RankingItemInfo> getDailyRanking(LocalDate date, int pageOneBased, int size) {
        if (date == null) date = LocalDate.now(clock.withZone(KST));
        String key = RankingKey.daily(date);

        List<RankingEntry> entries = rankingRepository.getTopN(key, pageOneBased, size);
        if (entries.isEmpty()) return Collections.emptyList();

        List<Long> productIds = entries.stream().map(RankingEntry::productId).toList();
        Map<Long, ProductInfo> products = productFacade.findVisibleByIds(productIds);

        List<RankingItemInfo> result = new ArrayList<>(entries.size());
        for (RankingEntry entry : entries) {
            ProductInfo info = products.get(entry.productId());
            if (info == null) continue;  // 삭제/숨김 상품 — 응답에서 제외 (size 축소 허용)
            result.add(RankingItemInfo.of(entry, info));
        }
        return result;
    }

    public long getDailyTotal(LocalDate date) {
        if (date == null) date = LocalDate.now(clock.withZone(KST));
        return rankingRepository.getTotal(RankingKey.daily(date));
    }

    /**
     * KST 기준 "오늘" 을 반환한다 — controller / 응답 조립이 동일 clock 을 사용하도록.
     */
    public LocalDate today() {
        return LocalDate.now(clock.withZone(KST));
    }

    /**
     * 상품 상세의 `dailyRank` 필드에 사용되는 순위 조회.
     * 순위권 밖이면 null.
     */
    public Long getDailyRank(Long productId) {
        if (productId == null) return null;
        LocalDate today = LocalDate.now(clock.withZone(KST));
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
