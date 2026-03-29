package com.loopers.application.product;

import com.loopers.application.observability.ProductViewOutboxRecorder;
import com.loopers.application.product.event.ProductDeletedEvent;
import com.loopers.application.product.event.ProductUpdatedEvent;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.like.LikeService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductSortOrder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 상품 유스케이스 조율.
 * 트랜잭션 경계, 도메인 결과 → ProductInfo 변환.
 * Controller는 Facade만 호출하며, request는 도메인 파라미터로 변환 후 Service에 전달한다.
 *
 * <p>
 * 좋아요 수 집계: Like 도메인 경계를 지키기 위해 {@link LikeService}만 사용한다.
 * (Repository 직접 주입·호출 금지 → Service를 통한 캡슐화)
 */
@Service
public class ProductFacade {

    private final ProductService productService;
    private final BrandService brandService;
    private final LikeService likeService;
    private final ProductCacheService productCacheService;
    private final ApplicationEventPublisher eventPublisher;
    private final ProductViewOutboxRecorder productViewOutboxRecorder;

    public ProductFacade(ProductService productService, BrandService brandService, LikeService likeService,
            ProductCacheService productCacheService,
            ApplicationEventPublisher eventPublisher,
            ProductViewOutboxRecorder productViewOutboxRecorder) {
        this.productService = productService;
        this.brandService = brandService;
        this.likeService = likeService;
        this.productCacheService = productCacheService;
        this.eventPublisher = eventPublisher;
        this.productViewOutboxRecorder = productViewOutboxRecorder;
    }

    @Transactional
    public ProductInfo registerProduct(Long brandId, String name, BigDecimal price, int stockQuantity) {
        ProductModel product = productService.registerProduct(brandId, name, price, stockQuantity);
        return ProductInfo.from(product);
    }

    @Transactional(readOnly = true)
    public Optional<ProductInfo> findById(Long id) {
        return productService.findById(id).map(ProductInfo::from);
    }

    @Transactional(readOnly = true)
    public Optional<ProductInfo> findByIdAndNotDeleted(Long id) {
        return productService.findByIdAndNotDeleted(id).map(ProductInfo::from);
    }

    @Transactional(readOnly = true)
    public Optional<ProductDetailInfo> getProductDetail(Long productId) {
        Optional<ProductDetailInfo> cached = productCacheService.getDetail(productId);
        if (cached.isPresent()) {
            productViewOutboxRecorder.recordProductViewed(productId);
            return cached;
        }
        Optional<ProductModel> productOpt = productService.findByIdAndNotDeleted(productId);
        if (productOpt.isEmpty()) {
            return Optional.empty();
        }
        ProductModel product = productOpt.get();
        Optional<BrandModel> brandOpt = brandService.findByIdAndNotDeleted(product.getBrandId());
        if (brandOpt.isEmpty()) {
            return Optional.empty();
        }
        long likeCount = likeService.getLikeCountFromStats(productId);
        ProductDetailInfo info = new ProductDetailInfo(
                product.getId(),
                product.getBrandId(),
                brandOpt.get().getName(),
                product.getName(),
                product.getPrice(),
                product.getStockQuantity(),
                likeCount);
        productCacheService.putDetail(productId, info);
        productViewOutboxRecorder.recordProductViewed(productId);
        return Optional.of(info);
    }

    @Transactional(readOnly = true)
    public Page<ProductListItemInfo> getProductList(Long brandId, String sortParam, int page, int size) {
        if (page == 0) {
            Optional<Page<ProductListItemInfo>> cached = productCacheService.getList(brandId, sortParam, size);
            if (cached.isPresent()) {
                return cached.get();
            }
        }
        ProductSortOrder sortOrder = ProductSortOrder.fromParam(sortParam);
        Page<ProductModel> productPage = productService.findNotDeletedForList(sortOrder, brandId, page, size);
        List<ProductModel> products = productPage.getContent();
        if (products.isEmpty()) {
            return new PageImpl<>(List.of(), productPage.getPageable(), productPage.getTotalElements());
        }
        List<Long> productIds = products.stream().map(ProductModel::getId).toList();
        var likeCountMap = likeService.getLikeCountByProductIdsFromStats(productIds);
        List<Long> brandIds = products.stream().map(ProductModel::getBrandId).distinct().toList();
        Map<Long, BrandModel> brandMap = brandService.findByIdAndNotDeletedIn(brandIds);
        List<ProductListItemInfo> items = products.stream()
                .map(p -> {
                    String brandName = Optional.ofNullable(brandMap.get(p.getBrandId()))
                            .map(BrandModel::getName)
                            .orElse("");
                    long likeCount = likeCountMap.getOrDefault(p.getId(), 0L);
                    return new ProductListItemInfo(
                            p.getId(),
                            p.getName(),
                            p.getPrice(),
                            p.getBrandId(),
                            brandName,
                            likeCount);
                })
                .collect(Collectors.toList());
        Page<ProductListItemInfo> result = new PageImpl<>(items, productPage.getPageable(),
                productPage.getTotalElements());
        if (page == 0) {
            productCacheService.putList(brandId, sortParam, size, result);
        }
        return result;
    }

    @Transactional
    public ProductInfo updateProduct(Long id, String name, BigDecimal price, int stockQuantity) {
        ProductModel product = productService.updateProduct(id, name, price, stockQuantity);
        eventPublisher.publishEvent(new ProductUpdatedEvent(id));
        return ProductInfo.from(product);
    }

    @Transactional
    public void deleteProduct(Long id) {
        productService.deleteProduct(id);
        eventPublisher.publishEvent(new ProductDeletedEvent(id));
    }
}
