package com.loopers.application.product;

import com.loopers.application.ranking.RankingQueryUseCase;
import com.loopers.domain.model.brand.Brand;
import com.loopers.domain.model.common.PageResult;
import com.loopers.domain.model.product.Product;
import com.loopers.domain.repository.BrandRepository;
import com.loopers.domain.repository.ProductRepository;
import com.loopers.infrastructure.cache.CacheConfig;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ProductQueryService implements ProductQueryUseCase {

    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;
    private final RankingQueryUseCase rankingQueryUseCase;

    public ProductQueryService(ProductRepository productRepository,
                               BrandRepository brandRepository,
                               RankingQueryUseCase rankingQueryUseCase) {
        this.productRepository = productRepository;
        this.brandRepository = brandRepository;
        this.rankingQueryUseCase = rankingQueryUseCase;
    }

    @Transactional(readOnly = true)
    @Override
    public ProductDetailInfo getProduct(Long productId) {
        ProductCoreInfo core = getProductCore(productId);
        Long rank = rankingQueryUseCase.getProductRank(LocalDate.now(), productId);

        return new ProductDetailInfo(
                core.id(), core.brandId(), core.brandName(), core.name(),
                core.price(), core.salePrice(), core.onSale(),
                core.stock(), core.likeCount(), core.description(), rank
        );
    }

    @Cacheable(value = CacheConfig.PRODUCT_DETAIL, key = "#productId")
    public ProductCoreInfo getProductCore(Long productId) {
        Product product = productRepository.findActiveById(productId)
                .orElseThrow(() -> new CoreException(ErrorType.PRODUCT_NOT_FOUND));

        Brand brand = brandRepository.findById(product.getBrandId())
                .orElseThrow(() -> new CoreException(ErrorType.BRAND_NOT_FOUND));

        return new ProductCoreInfo(
                product.getId(),
                brand.getId(),
                brand.getName().getValue(),
                product.getName().getValue(),
                product.getPrice().getValue(),
                product.getSalePrice() != null ? product.getSalePrice().getValue() : null,
                product.isOnSale(),
                product.getStock().getValue(),
                product.getLikeCount(),
                product.getDescription()
        );
    }

    @Transactional(readOnly = true)
    @Override
    @Cacheable(value = CacheConfig.PRODUCT_LIST, key = "'brand:' + #brandId + ':sort:' + #sort + ':page:' + #page + ':size:' + #size")
    public PageResult<ProductSummaryInfo> getProducts(Long brandId, String sort, int page, int size) {
        PageResult<Product> products = productRepository.findAllActive(brandId, sort, page, size);

        List<Long> brandIds = products.content().stream()
                .map(Product::getBrandId)
                .distinct()
                .toList();

        Map<Long, String> brandNameMap = brandRepository.findAllByIds(brandIds).stream()
                .collect(Collectors.toMap(Brand::getId, b -> b.getName().getValue()));

        return products.map(product -> new ProductSummaryInfo(
                product.getId(),
                product.getBrandId(),
                brandNameMap.getOrDefault(product.getBrandId(), ""),
                product.getName().getValue(),
                product.getPrice().getValue(),
                product.getSalePrice() != null ? product.getSalePrice().getValue() : null,
                product.isOnSale(),
                product.getLikeCount()
        ));
    }

    public record ProductCoreInfo(
            Long id, Long brandId, String brandName, String name,
            int price, Integer salePrice, boolean onSale,
            int stock, int likeCount, String description
    ) {}
}
