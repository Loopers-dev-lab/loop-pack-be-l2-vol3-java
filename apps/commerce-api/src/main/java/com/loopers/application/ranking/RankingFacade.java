package com.loopers.application.ranking;

import com.loopers.application.product.ProductAssembler;
import com.loopers.application.product.ProductInfo;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.ranking.MonthlyRankingRepository;
import com.loopers.domain.ranking.RankingPeriod;
import com.loopers.domain.ranking.RankingRepository;
import com.loopers.domain.ranking.WeeklyRankingRepository;
import com.loopers.support.page.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Component
public class RankingFacade {

    private final RankingRepository rankingRepository;
    private final WeeklyRankingRepository weeklyRankingRepository;
    private final MonthlyRankingRepository monthlyRankingRepository;
    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;
    private final ProductAssembler productAssembler;

    @Transactional(readOnly = true)
    public PageResponse<RankingInfo> getPage(LocalDate date, RankingPeriod period, int page, int size) {
        long offset = (long) (page - 1) * size;
        List<Long> productIds = resolveProductIds(date, period, offset, (long) size);

        if (productIds.isEmpty()) {
            return new PageResponse<>(List.of(), page, size, 0);
        }

        long count = resolveCount(date, period);
        int totalPages = (int) Math.ceil((double) count / size);

        List<Product> products = productRepository.findAllByIdIn(productIds);
        List<Long> brandIds = products.stream().map(Product::brandId).toList();
        List<Brand> brands = brandRepository.findAllByIdIn(brandIds);

        Map<Long, ProductInfo> infoMap = productAssembler.toInfoMap(products, brands);

        List<RankingInfo> rankingInfos = new ArrayList<>();
        for (int i = 0; i < productIds.size(); i++) {
            Long productId = productIds.get(i);
            ProductInfo productInfo = infoMap.get(productId);
            if (productInfo != null) {
                rankingInfos.add(RankingInfo.of(offset + i + 1, productInfo));
            }
        }

        return new PageResponse<>(rankingInfos, page, size, totalPages);
    }

    private List<Long> resolveProductIds(LocalDate date, RankingPeriod period, long offset, long limit) {
        return switch (period) {
            case DAILY -> rankingRepository.findProductIdsByRank(date, offset, limit);
            case WEEKLY -> weeklyRankingRepository.findProductIdsByBaseDate(
                    date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)), offset, limit);
            case MONTHLY -> monthlyRankingRepository.findProductIdsByBaseDate(
                    date.withDayOfMonth(1), offset, limit);
        };
    }

    private long resolveCount(LocalDate date, RankingPeriod period) {
        return switch (period) {
            case DAILY -> rankingRepository.countByDate(date);
            case WEEKLY -> weeklyRankingRepository.countByBaseDate(
                    date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)));
            case MONTHLY -> monthlyRankingRepository.countByBaseDate(date.withDayOfMonth(1));
        };
    }
}
