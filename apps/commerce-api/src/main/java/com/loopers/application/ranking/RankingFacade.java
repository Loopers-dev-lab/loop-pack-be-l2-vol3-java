package com.loopers.application.ranking;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.ranking.RankingRepository;
import com.loopers.domain.ranking.RankingService;
import com.loopers.support.enums.DisplayStatus;
import com.loopers.support.page.PagedResult;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RankingFacade {

    private static final int BUFFER = 5;

    private final RankingService rankingService;
    private final ProductService productService;
    private final BrandService brandService;

    /**
     * 랭킹 페이지를 조회한다.
     * ZSET에서 Top-N(+buffer)을 가져온 후, 삭제/숨김 상품을 필터링하고
     * 상품/브랜드 정보를 배치 조회하여 조합한다.
     */
    @Cacheable(value = "rankings", key = "#date + '_' + #page + '_' + #size",
               cacheManager = "rankingCacheManager")
    public PagedResult<RankingInfo> getRankings(LocalDate date, int page, int size) {
        // 1. ZSET에서 size + buffer개 요청 (삭제 상품 대비)
        List<RankingRepository.RankingEntry> entries =
                rankingService.getTopRankings(date, page, size + BUFFER);

        if (entries.isEmpty()) {
            return new PagedResult<>(List.of(), page, size, 0, 0);
        }

        // 2. 상품 정보 배치 조회 (N+1 방지)
        List<Long> productIds = entries.stream()
                .map(RankingRepository.RankingEntry::productId)
                .toList();
        Map<Long, ProductModel> productMap = productService.findAllByIds(productIds)
                .stream()
                .filter(p -> !p.isDeleted())
                .filter(p -> p.getDisplayStatus() == DisplayStatus.ACTIVE)
                .collect(Collectors.toMap(ProductModel::getProductId, Function.identity()));

        // 3. 브랜드 정보 배치 조회
        List<Long> brandIds = productMap.values().stream()
                .map(ProductModel::getBrandId)
                .distinct()
                .toList();
        Map<Long, BrandModel> brandMap = brandService.findAllByIds(brandIds)
                .stream()
                .collect(Collectors.toMap(BrandModel::getBrandId, Function.identity()));

        // 4. 유효한 상품만 조합, size만큼만 반환
        long baseRank = (long) page * size;
        List<RankingInfo> rankings = new ArrayList<>();
        for (RankingRepository.RankingEntry entry : entries) {
            if (rankings.size() >= size) break;

            ProductModel product = productMap.get(entry.productId());
            if (product == null) continue;

            BrandModel brand = brandMap.get(product.getBrandId());
            String brandName = brand != null ? brand.getBrandName() : null;

            rankings.add(RankingInfo.of(
                    baseRank + rankings.size() + 1,
                    entry.productId(),
                    product.getProductName(),
                    brandName,
                    product.getPrice(),
                    product.getImageUrl(),
                    entry.score()
            ));
        }

        // 5. 총 개수 및 페이지 수
        long totalElements = rankingService.getTotalCount(date);
        int totalPages = (int) Math.ceil((double) totalElements / size);

        return new PagedResult<>(rankings, page, size, totalElements, totalPages);
    }
}
