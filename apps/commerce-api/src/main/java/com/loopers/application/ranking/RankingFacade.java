package com.loopers.application.ranking;

import com.loopers.application.ranking.dto.FindRankingItemResDto;
import com.loopers.application.ranking.dto.FindRankingListResDto;
import com.loopers.domain.product.model.Product;
import com.loopers.domain.product.service.ProductService;
import com.loopers.domain.ranking.model.RankingEntry;
import com.loopers.domain.ranking.model.RankingQuery;
import com.loopers.domain.ranking.model.RankingSlice;
import com.loopers.domain.ranking.model.RankingPeriod;
import com.loopers.domain.ranking.service.MonthlyRankingService;
import com.loopers.domain.ranking.service.RankingService;
import com.loopers.domain.ranking.service.WeeklyRankingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
@Transactional(readOnly = true)
public class RankingFacade {

    private final RankingService dailyRankingService;
    private final WeeklyRankingService weeklyRankingService;
    private final MonthlyRankingService monthlyRankingService;
    private final ProductService productService;

    public FindRankingListResDto getRankings(RankingQuery query) {
        RankingSlice slice = switch (query.period()) {
            case DAILY -> new RankingSlice(
                    dailyRankingService.getTopRankings(query.date(), query.page(), query.size()),
                    dailyRankingService.getTotalCount(query.date())
            );
            case WEEKLY -> weeklyRankingService.getRankings(query.date(), query.page(), query.size());
            case MONTHLY -> monthlyRankingService.getRankings(query.date(), query.page(), query.size());
        };

        if (slice.entries().isEmpty()) {
            return new FindRankingListResDto(List.of(), slice.totalCount(), query.page(), query.size());
        }

        List<Long> productIds = slice.entries().stream()
                .map(RankingEntry::productId)
                .toList();
        Map<Long, Product> productMap = productService.findAllByIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        List<FindRankingItemResDto> items = slice.entries().stream()
                .filter(entry -> productMap.containsKey(entry.productId()))
                .map(entry -> FindRankingItemResDto.from(entry, productMap.get(entry.productId())))
                .toList();

        return new FindRankingListResDto(items, slice.totalCount(), query.page(), query.size());
    }
}
