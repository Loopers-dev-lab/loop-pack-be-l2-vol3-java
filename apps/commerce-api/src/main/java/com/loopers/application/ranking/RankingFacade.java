package com.loopers.application.ranking;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.ranking.RankingRepository;
import com.loopers.domain.ranking.RankingRepository.RankedProduct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@RequiredArgsConstructor
@Component
public class RankingFacade {

    private final RankingRepository rankingRepository;
    private final ProductService productService;

    public List<RankingInfo> getRankings(LocalDate date, int page, int size) {
        List<RankedProduct> ranked = rankingRepository.getTopN(date, page, size);
        if (ranked.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> productIds = ranked.stream().map(RankedProduct::productId).toList();
        List<Product> products = productService.getProducts(productIds);
        List<Long> brandIds = products.stream().map(Product::getBrandId).distinct().toList();
        List<Brand> brands = productService.getBrands(brandIds);

        Map<Long, Product> productMap = products.stream()
            .collect(Collectors.toMap(Product::getId, p -> p));
        Map<Long, Brand> brandMap = brands.stream()
            .collect(Collectors.toMap(Brand::getId, b -> b));

        int offset = (page - 1) * size;

        return IntStream.range(0, ranked.size())
            .mapToObj(i -> {
                RankedProduct rp = ranked.get(i);
                Product product = productMap.get(rp.productId());
                Brand brand = brandMap.get(product.getBrandId());
                return new RankingInfo(
                    offset + i + 1,
                    product.getId(),
                    product.getName(),
                    brand.getName(),
                    product.getPrice(),
                    rp.score()
                );
            })
            .toList();
    }
}
