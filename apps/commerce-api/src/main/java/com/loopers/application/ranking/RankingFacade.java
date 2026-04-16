package com.loopers.application.ranking;

import com.loopers.application.product.ProductInfo;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.ranking.ProductRankMonthly;
import com.loopers.domain.ranking.ProductRankWeekly;
import com.loopers.infrastructure.ranking.ProductRankMonthlyJpaRepository;
import com.loopers.infrastructure.ranking.ProductRankWeeklyJpaRepository;
import com.loopers.infrastructure.ranking.RankingCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class RankingFacade {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final RankingCacheService rankingCacheService;
    private final ProductService productService;
    private final BrandService brandService;
    private final ProductRankWeeklyJpaRepository productRankWeeklyJpaRepository;
    private final ProductRankMonthlyJpaRepository productRankMonthlyJpaRepository;

    @Transactional(readOnly = true)
    public List<RankingInfo> getRankings(String date, int page, int size, RankingType type) {
        return switch (type) {
            case DAILY -> getDailyRankings(date, page, size);
            case WEEKLY -> getWeeklyRankings(date, page, size);
            case MONTHLY -> getMonthlyRankings(date, page, size);
        };
    }

    public long getTotalCount(String date, RankingType type) {
        return switch (type) {
            case DAILY -> rankingCacheService.getTotalCount(date);
            case WEEKLY -> productRankWeeklyJpaRepository.countByYearWeek(toYearWeek(date));
            case MONTHLY -> productRankMonthlyJpaRepository.countByYearMonth(toYearMonth(date));
        };
    }

    private List<RankingInfo> getDailyRankings(String date, int page, int size) {
        int offset = (page - 1) * size;
        List<String> productIdStrings = rankingCacheService.getProductIds(date, offset, size);

        if (productIdStrings.isEmpty()) {
            return List.of();
        }

        List<Long> ids = productIdStrings.stream().map(Long::valueOf).toList();
        List<Product> products = productService.getProductsByIds(ids);
        Map<Long, Brand> brandMap = toBrandMap(products);
        Map<Long, Product> productMap = products.stream()
            .collect(Collectors.toMap(Product::getId, p -> p));

        List<RankingInfo> result = new ArrayList<>();
        for (int i = 0; i < productIdStrings.size(); i++) {
            Long productId = Long.valueOf(productIdStrings.get(i));
            Product product = productMap.get(productId);
            if (product == null) continue;
            Brand brand = brandMap.get(product.getBrandId());
            if (brand == null) continue;
            result.add(RankingInfo.of(offset + i + 1, ProductInfo.from(product, brand)));
        }
        return result;
    }

    private List<RankingInfo> getWeeklyRankings(String date, int page, int size) {
        String yearWeek = toYearWeek(date);
        List<ProductRankWeekly> ranked = productRankWeeklyJpaRepository
            .findByYearWeekOrderByRankPosition(yearWeek);

        int offset = (page - 1) * size;
        List<ProductRankWeekly> paged = ranked.stream()
            .skip(offset).limit(size).toList();

        if (paged.isEmpty()) {
            return List.of();
        }

        List<Long> ids = paged.stream().map(ProductRankWeekly::getProductId).toList();
        List<Product> products = productService.getProductsByIds(ids);
        Map<Long, Brand> brandMap = toBrandMap(products);
        Map<Long, Product> productMap = products.stream()
            .collect(Collectors.toMap(Product::getId, p -> p));

        return paged.stream()
            .map(r -> {
                Product product = productMap.get(r.getProductId());
                if (product == null) return null;
                Brand brand = brandMap.get(product.getBrandId());
                if (brand == null) return null;
                return RankingInfo.of(r.getRankPosition(), ProductInfo.from(product, brand));
            })
            .filter(r -> r != null)
            .toList();
    }

    private List<RankingInfo> getMonthlyRankings(String date, int page, int size) {
        String yearMonth = toYearMonth(date);
        List<ProductRankMonthly> ranked = productRankMonthlyJpaRepository
            .findByYearMonthOrderByRankPosition(yearMonth);

        int offset = (page - 1) * size;
        List<ProductRankMonthly> paged = ranked.stream()
            .skip(offset).limit(size).toList();

        if (paged.isEmpty()) {
            return List.of();
        }

        List<Long> ids = paged.stream().map(ProductRankMonthly::getProductId).toList();
        List<Product> products = productService.getProductsByIds(ids);
        Map<Long, Brand> brandMap = toBrandMap(products);
        Map<Long, Product> productMap = products.stream()
            .collect(Collectors.toMap(Product::getId, p -> p));

        return paged.stream()
            .map(r -> {
                Product product = productMap.get(r.getProductId());
                if (product == null) return null;
                Brand brand = brandMap.get(product.getBrandId());
                if (brand == null) return null;
                return RankingInfo.of(r.getRankPosition(), ProductInfo.from(product, brand));
            })
            .filter(r -> r != null)
            .toList();
    }

    private Map<Long, Brand> toBrandMap(List<Product> products) {
        List<Long> brandIds = products.stream().map(Product::getBrandId).distinct().toList();
        return brandService.getBrandsByIds(brandIds).stream()
            .collect(Collectors.toMap(Brand::getId, b -> b));
    }

    private String toYearWeek(String date) {
        LocalDate localDate = LocalDate.parse(date, DATE_FORMATTER);
        WeekFields weekFields = WeekFields.ISO;
        int week = localDate.get(weekFields.weekOfWeekBasedYear());
        int year = localDate.get(weekFields.weekBasedYear());
        return year + "-W" + String.format("%02d", week);
    }

    private String toYearMonth(String date) {
        LocalDate localDate = LocalDate.parse(date, DATE_FORMATTER);
        return localDate.format(DateTimeFormatter.ofPattern("yyyy-MM"));
    }
}
