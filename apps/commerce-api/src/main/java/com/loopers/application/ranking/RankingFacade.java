package com.loopers.application.ranking;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.ranking.MvRankingRepository;
import com.loopers.domain.ranking.RankedProduct;
import com.loopers.domain.ranking.RankingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RankingFacade {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final RankingRepository rankingRepository;
    private final MvRankingRepository mvRankingRepository;
    private final ProductService productService;
    private final BrandService brandService;

    public RankingPageInfo getRankings(String period, String date, int page, int size) {
        int offset = (page - 1) * size;

        List<RankedProduct> ranked;
        long totalSize;

        switch (period) {
            case "weekly" -> {
                LocalDate targetDate = LocalDate.parse(date, DATE_FORMAT);
                LocalDate periodStart = targetDate.with(DayOfWeek.MONDAY);
                LocalDate periodEnd = targetDate.with(DayOfWeek.SUNDAY);
                ranked = mvRankingRepository.findWeeklyRanking(periodStart, periodEnd, offset, size);
                totalSize = mvRankingRepository.countWeeklyRanking(periodStart, periodEnd);
            }
            case "monthly" -> {
                LocalDate targetDate = LocalDate.parse(date, DATE_FORMAT);
                YearMonth yearMonth = YearMonth.from(targetDate);
                LocalDate periodStart = yearMonth.atDay(1);
                LocalDate periodEnd = yearMonth.atEndOfMonth();
                ranked = mvRankingRepository.findMonthlyRanking(periodStart, periodEnd, offset, size);
                totalSize = mvRankingRepository.countMonthlyRanking(periodStart, periodEnd);
            }
            default -> {
                // daily — Redis ZSET
                ranked = rankingRepository.findTopN(date, offset, size);
                totalSize = rankingRepository.getTotalSize(date);
            }
        }

        if (ranked.isEmpty()) {
            return new RankingPageInfo(date, Collections.emptyList(), 0, page, size);
        }

        return buildPageInfo(date, ranked, totalSize, offset, page, size);
    }

    public Long getProductRank(String date, Long productId) {
        Long rank = rankingRepository.findRank(date, productId);
        return rank != null ? rank + 1 : null;
    }

    private RankingPageInfo buildPageInfo(String date, List<RankedProduct> ranked, long totalSize, int offset, int page, int size) {
        List<Long> productIds = ranked.stream().map(RankedProduct::productId).toList();
        List<Product> products = productService.getByIds(productIds);
        Map<Long, Product> productMap = products.stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        List<Long> brandIds = products.stream().map(Product::getBrandId).distinct().toList();
        Map<Long, Brand> brandMap = brandService.getByIds(brandIds).stream()
                .collect(Collectors.toMap(Brand::getId, b -> b));

        long rank = offset + 1;
        List<RankingInfo> items = new java.util.ArrayList<>();
        for (RankedProduct rp : ranked) {
            Product product = productMap.get(rp.productId());
            if (product == null) {
                rank++;
                continue;
            }
            Brand brand = brandMap.get(product.getBrandId());
            String brandName = brand != null ? brand.getName() : null;
            items.add(RankingInfo.of(rank++, rp.score(), product, brandName));
        }

        return new RankingPageInfo(date, items, totalSize, page, size);
    }
}
