package com.loopers.application.ranking;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.ranking.MvProductRankMonthly;
import com.loopers.domain.ranking.MvProductRankMonthlyRepository;
import com.loopers.domain.ranking.MvProductRankWeekly;
import com.loopers.domain.ranking.MvProductRankWeeklyRepository;
import com.loopers.domain.ranking.RankingService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class RankingFacade {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final RankingService rankingService;
    private final ProductService productService;
    private final BrandService brandService;
    private final MvProductRankWeeklyRepository mvProductRankWeeklyRepository;
    private final MvProductRankMonthlyRepository mvProductRankMonthlyRepository;

    public List<RankingInfo> getTopRankings(String date, int page, int size, String period) {
        return switch (period) {
            case "daily" -> getTopRankingsDaily(date, page, size);
            case "weekly" -> getTopRankingsWeekly(date, page, size);
            case "monthly" -> getTopRankingsMonthly(date, page, size);
            default -> throw new CoreException(ErrorType.BAD_REQUEST, "period는 daily, weekly, monthly 중 하나여야 합니다.");
        };
    }

    private List<RankingInfo> getTopRankingsDaily(String date, int page, int size) {
        List<Long> productIds = rankingService.getTopProductIds(date, page, size);
        if (productIds.isEmpty()) return List.of();

        List<Product> products = productService.getProductsByIds(productIds);
        Map<Long, Product> productMap = products.stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        List<Long> brandIds = products.stream().map(Product::getBrandId).distinct().toList();
        Map<Long, Brand> brandMap = brandService.getBrandsByIds(brandIds);

        List<RankingInfo> result = new ArrayList<>();
        for (int i = 0; i < productIds.size(); i++) {
            Long productId = productIds.get(i);
            Product product = productMap.get(productId);
            if (product == null) continue;
            Brand brand = brandMap.get(product.getBrandId());
            result.add(new RankingInfo(
                    (long) (page * size + i + 1),
                    product.getId(),
                    product.getName(),
                    product.getImageUrl(),
                    product.getPrice(),
                    brand != null ? brand.getId() : null,
                    brand != null ? brand.getName() : null
            ));
        }
        return result;
    }

    private List<RankingInfo> getTopRankingsWeekly(String date, int page, int size) {
        LocalDate localDate = LocalDate.parse(date, DATE_FORMATTER);
        List<MvProductRankWeekly> weeklyList = mvProductRankWeeklyRepository.findByDate(localDate, page, size);
        if (weeklyList.isEmpty()) return List.of();

        List<Long> productIds = weeklyList.stream().map(MvProductRankWeekly::getProductId).toList();
        List<Product> products = productService.getProductsByIds(productIds);
        Map<Long, Product> productMap = products.stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        List<Long> brandIds = products.stream().map(Product::getBrandId).distinct().toList();
        Map<Long, Brand> brandMap = brandService.getBrandsByIds(brandIds);

        return weeklyList.stream()
                .map(item -> {
                    Product product = productMap.get(item.getProductId());
                    if (product == null) return null;
                    Brand brand = brandMap.get(product.getBrandId());
                    return new RankingInfo(
                            item.getRankPosition(),
                            product.getId(),
                            product.getName(),
                            product.getImageUrl(),
                            product.getPrice(),
                            brand != null ? brand.getId() : null,
                            brand != null ? brand.getName() : null
                    );
                })
                .filter(info -> info != null)
                .toList();
    }

    private List<RankingInfo> getTopRankingsMonthly(String date, int page, int size) {
        LocalDate localDate = LocalDate.parse(date, DATE_FORMATTER);
        List<MvProductRankMonthly> monthlyList = mvProductRankMonthlyRepository.findByDate(localDate, page, size);
        if (monthlyList.isEmpty()) return List.of();

        List<Long> productIds = monthlyList.stream().map(MvProductRankMonthly::getProductId).toList();
        List<Product> products = productService.getProductsByIds(productIds);
        Map<Long, Product> productMap = products.stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        List<Long> brandIds = products.stream().map(Product::getBrandId).distinct().toList();
        Map<Long, Brand> brandMap = brandService.getBrandsByIds(brandIds);

        return monthlyList.stream()
                .map(item -> {
                    Product product = productMap.get(item.getProductId());
                    if (product == null) return null;
                    Brand brand = brandMap.get(product.getBrandId());
                    return new RankingInfo(
                            item.getRankPosition(),
                            product.getId(),
                            product.getName(),
                            product.getImageUrl(),
                            product.getPrice(),
                            brand != null ? brand.getId() : null,
                            brand != null ? brand.getName() : null
                    );
                })
                .filter(info -> info != null)
                .toList();
    }

    // 기존 메서드 (하위 호환 - 내부에서 호출하지 않지만 혹시 외부 참조를 위해 유지)
    public List<RankingInfo> getTopRankings(String date, int page, int size) {
        return getTopRankings(date, page, size, "daily");
    }
}
