package com.loopers.application.ranking;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.ranking.RankedProduct;
import com.loopers.domain.ranking.RankingRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RankingFacade {

    private final RankingRepository rankingRepository;
    private final ProductService productService;
    private final BrandService brandService;

    public List<RankingInfo> getRankings(LocalDate date, String period, int size, int page) {
        List<RankedProduct> rankedProducts = switch (period) {
            case "weekly" -> rankingRepository.getWeeklyTopN(date, size, page);
            case "monthly" -> rankingRepository.getMonthlyTopN(date, size, page);
            default -> rankingRepository.getTopN(date, size, page);
        };
        if (rankedProducts.isEmpty()) {
            return List.of();
        }

        List<Long> productIds = rankedProducts.stream().map(RankedProduct::productId).toList();
        Map<Long, Product> productMap = productService.getByIds(productIds).stream()
            .collect(Collectors.toMap(Product::getId, p -> p));

        List<Long> brandIds = productMap.values().stream().map(Product::getRefBrandId).distinct().toList();
        Map<Long, Brand> brandMap = brandIds.stream()
            .collect(Collectors.toMap(id -> id, brandService::getById));

        long baseRank = (long) page * size + 1;
        List<RankingInfo> result = new java.util.ArrayList<>();
        for (int i = 0; i < rankedProducts.size(); i++) {
            RankedProduct rp = rankedProducts.get(i);
            Product product = productMap.get(rp.productId());
            Brand brand = brandMap.get(product.getRefBrandId());
            result.add(new RankingInfo(
                baseRank + i,
                rp.productId(),
                product.getName().value(),
                product.getPrice().value(),
                brand.getName().value(),
                rp.score()
            ));
        }
        return result;
    }
}
