package com.loopers.application.ranking;

import com.loopers.application.ranking.dto.FindRankingItemResDto;
import com.loopers.application.ranking.dto.FindRankingListResDto;
import com.loopers.domain.product.model.Product;
import com.loopers.domain.product.service.ProductService;
import com.loopers.domain.ranking.repository.RankingRepository;
import com.loopers.domain.ranking.service.RankingService;
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

    private final RankingService rankingService;
    private final ProductService productService;

    public FindRankingListResDto getRankings(String date, int page, int size) {
        List<RankingRepository.RankingEntry> entries = rankingService.getTopRankings(date, page, size);
        long totalCount = rankingService.getTotalCount(date);

        if (entries.isEmpty()) {
            return new FindRankingListResDto(List.of(), totalCount, page, size);
        }

        List<Long> productIds = entries.stream()
                .map(RankingRepository.RankingEntry::productId)
                .toList();
        Map<Long, Product> productMap = productService.getProductsByIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        List<FindRankingItemResDto> items = entries.stream()
                .filter(entry -> productMap.containsKey(entry.productId()))
                .map(entry -> FindRankingItemResDto.from(entry, productMap.get(entry.productId())))
                .toList();

        return new FindRankingListResDto(items, totalCount, page, size);
    }
}
