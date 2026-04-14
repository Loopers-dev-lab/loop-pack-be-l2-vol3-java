package com.loopers.application.ranking;

import com.loopers.application.product.ProductAssembler;
import com.loopers.application.product.ProductInfo;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.ranking.RankingRepository;
import com.loopers.support.page.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Component
public class RankingFacade {

    private final RankingRepository rankingRepository;
    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;
    private final ProductAssembler productAssembler;

    @Transactional(readOnly = true)
    public PageResponse<RankingInfo> getPage(LocalDate date, int page, int size) {
        long offset = (long) (page - 1) * size;
        List<Long> productIds = rankingRepository.findProductIdsByRank(date, offset, (long) size);

        if (productIds.isEmpty()) {
            return new PageResponse<>(List.of(), page, size, 0);
        }

        long count = rankingRepository.countByDate(date);
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
}
