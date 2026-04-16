package com.loopers.application.ranking;

import com.loopers.application.product.ProductFacade;
import com.loopers.application.product.ProductInfo;
import com.loopers.domain.ranking.RankingEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 랭킹 항목 조립기.
 *
 * 저장소에서 가져온 RankingEntry 목록에 상품 가시성 필터링을 적용하고
 * RankingPageResult 로 조립한다. 일간/주간/월간 Facade 가 공통으로 사용한다.
 */
@Component
@RequiredArgsConstructor
public class RankingAssembler {

    public static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final ProductFacade productFacade;

    /**
     * RankingEntry 목록을 상품 가시성 필터링 후 RankingPageResult 로 조립한다.
     *
     * 삭제/숨김 상태인 상품은 결과에서 제외되므로 반환 items 수가 entries 수보다 작을 수 있다.
     */
    public RankingPageResult assemble(LocalDate effectiveDate, long total, List<RankingEntry> entries) {
        if (entries.isEmpty()) return new RankingPageResult(effectiveDate, total, List.of());

        List<Long> productIds = entries.stream().map(RankingEntry::productId).toList();
        Map<Long, ProductInfo> products = productFacade.findVisibleByIds(productIds);

        List<RankingItemInfo> items = entries.stream()
                .map(entry -> {
                    ProductInfo info = products.get(entry.productId());
                    return info == null ? null : RankingItemInfo.of(entry, info);
                })
                .filter(Objects::nonNull)
                .toList();

        return new RankingPageResult(effectiveDate, total, items);
    }
}
