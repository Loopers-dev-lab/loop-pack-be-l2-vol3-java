package com.loopers.application.ranking;

import com.loopers.domain.model.brand.Brand;
import com.loopers.domain.model.common.PageResult;
import com.loopers.domain.model.product.Product;
import com.loopers.domain.repository.BrandRepository;
import com.loopers.domain.repository.ProductRepository;
import com.loopers.domain.repository.RankingRepository;
import com.loopers.domain.repository.RankingRepository.RankedProduct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class RankingQueryService implements RankingQueryUseCase {

    private final RankingRepository rankingRepository;
    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;

    public RankingQueryService(RankingRepository rankingRepository,
                               ProductRepository productRepository,
                               BrandRepository brandRepository) {
        this.rankingRepository = rankingRepository;
        this.productRepository = productRepository;
        this.brandRepository = brandRepository;
    }

    @Override
    public PageResult<RankingItemInfo> getRankings(LocalDate date, int page, int size) {
        int offset = page * size;
        List<RankedProduct> rankedProducts = rankingRepository.getTopRankings(date, offset, size);

        if (rankedProducts.isEmpty()) {
            return new PageResult<>(Collections.emptyList(), page, size, 0, 0);
        }

        long totalCount = rankingRepository.getTotalCount(date);
        int totalPages = (int) Math.ceil((double) totalCount / size);

        List<Long> productIds = rankedProducts.stream()
                .map(RankedProduct::productId)
                .toList();

        Map<Long, Product> productMap = productRepository.findAllActiveByIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        List<Long> brandIds = productMap.values().stream()
                .map(Product::getBrandId)
                .distinct()
                .toList();

        Map<Long, String> brandNameMap = brandRepository.findAllByIds(brandIds).stream()
                .collect(Collectors.toMap(Brand::getId, b -> b.getName().getValue()));

        List<RankingItemInfo> items = rankedProducts.stream()
                .map(ranked -> toRankingItemInfo(ranked, productMap, brandNameMap))
                .filter(item -> item != null)
                .toList();

        return new PageResult<>(items, page, size, totalCount, totalPages);
    }

    @Override
    public Long getProductRank(LocalDate date, Long productId) {
        Long rank = rankingRepository.getRank(date, productId);
        return rank != null ? rank + 1 : null;
    }

    private RankingItemInfo toRankingItemInfo(RankedProduct ranked,
                                              Map<Long, Product> productMap,
                                              Map<Long, String> brandNameMap) {
        Product product = productMap.get(ranked.productId());
        if (product == null) {
            return null;
        }

        long rank = ranked.rank() + 1;
        String brandName = brandNameMap.getOrDefault(product.getBrandId(), "");

        return new RankingItemInfo(
                rank,
                ranked.score(),
                product.getId(),
                product.getBrandId(),
                brandName,
                product.getName().getValue(),
                product.getPrice().getValue(),
                product.getSalePrice() != null ? product.getSalePrice().getValue() : null,
                product.isOnSale(),
                product.getLikeCount()
        );
    }
}
