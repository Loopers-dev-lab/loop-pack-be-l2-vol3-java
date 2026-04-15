package com.loopers.application.ranking;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.ranking.ProductRankingInfo;
import com.loopers.domain.ranking.ProductRankingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class RankingFacade {

    private static final DateTimeFormatter DATE_KEY_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final ProductRankingRepository productRankingRepository;
    private final ProductService productService;
    private final BrandService brandService;

    @Transactional(readOnly = true)
    public Page<RankingInfo> getRankings(String date, int page, int size) {
        String dateKey = date != null ? date : LocalDate.now().format(DATE_KEY_FORMATTER);
        int offset = page * size;

        List<ProductRankingInfo> rankings = productRankingRepository.getRankings(dateKey, offset, size);
        if (rankings.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(), PageRequest.of(page, size), 0);
        }

        Long totalCount = productRankingRepository.getTotalCount(dateKey);

        List<Long> productIds = rankings.stream()
            .map(ProductRankingInfo::productId)
            .toList();

        Map<Long, Product> productMap = productService.getProductsByIds(productIds).stream()
            .collect(Collectors.toMap(Product::getId, Function.identity()));

        List<Long> brandIds = productMap.values().stream()
            .map(Product::getBrandId)
            .distinct()
            .toList();
        Map<Long, Brand> brandMap = brandService.getActiveBrandsByIds(brandIds);

        List<RankingInfo> rankingInfos = rankings.stream()
            .map(r -> {
                Product product = productMap.get(r.productId());
                if (product == null) {
                    return null;
                }
                Brand brand = brandMap.get(product.getBrandId());
                String brandName = brand != null ? brand.getName() : null;
                return RankingInfo.from(r, product, brandName);
            })
            .filter(Objects::nonNull)
            .toList();

        return new PageImpl<>(rankingInfos, PageRequest.of(page, size), totalCount);
    }

    public RankingDetailInfo getProductRanking(Long productId) {
        String dateKey = LocalDate.now().format(DATE_KEY_FORMATTER);
        Long rank = productRankingRepository.getRank(dateKey, productId);
        if (rank == null) {
            return null;
        }
        Double score = productRankingRepository.getScore(dateKey, productId);
        return new RankingDetailInfo(rank, score, dateKey);
    }
}
