package com.loopers.application.product;

import com.loopers.application.event.ProductViewedEvent;
import com.loopers.application.event.UserActionEvent;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductDomainService;
import com.loopers.domain.product.ProductInfo;
import com.loopers.domain.product.ProductSort;
import com.loopers.domain.useraction.ActionType;
import com.loopers.infrastructure.cache.ProductLikeCountQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductDomainService productDomainService;
    private final ProductLikeCountQueryService likeCountQueryService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 상품 조회 (사용자 ID 없이).
     * 조회 이벤트를 발행하지 않음.
     */
    @Transactional(readOnly = true)
    public ProductResult findById(Long id) {
        Product product = productDomainService.findById(id);
        long likeCount = likeCountQueryService.getLikeCount(id);
        return ProductResult.from(product, likeCount);
    }

    /**
     * 상품 조회 (사용자 ID 포함).
     * 사용자 ID가 있으면 조회 이벤트 발행.
     *
     * @param id 상품 ID
     * @param userId 사용자 ID (nullable)
     * @return 상품 결과
     */
    @Transactional(readOnly = true)
    public ProductResult findById(Long id, Long userId) {
        Product product = productDomainService.findById(id);
        long likeCount = likeCountQueryService.getLikeCount(id);

        if (userId != null) {
            eventPublisher.publishEvent(ProductViewedEvent.of(userId, id));
            eventPublisher.publishEvent(UserActionEvent.of(userId, ActionType.VIEW, id));
        }

        return ProductResult.from(product, likeCount);
    }

    @Transactional(readOnly = true)
    public Page<ProductResult> findAll(Long brandId, ProductSort sort, Pageable pageable) {
        if (sort == null) {
            sort = ProductSort.LATEST;
        }

        int offset = (int) pageable.getOffset();
        int limit = pageable.getPageSize();

        List<Product> products;
        long total;

        if (brandId != null) {
            products = productDomainService.findAllByBrandId(brandId, sort, offset, limit);
            total = productDomainService.countByBrandId(brandId);
        } else {
            products = productDomainService.findAll(sort, offset, limit);
            total = productDomainService.countAll();
        }

        List<Long> productIds = products.stream()
            .map(Product::getId)
            .toList();

        Map<Long, Long> likeCounts = likeCountQueryService.getLikeCounts(productIds);

        List<ProductResult> results = products.stream()
            .map(p -> ProductResult.from(p, likeCounts.getOrDefault(p.getId(), 0L)))
            .toList();

        return new PageImpl<>(results, pageable, total);
    }

    @Transactional(readOnly = true)
    public Page<ProductResult> findAll(Long brandId, Pageable pageable) {
        ProductSort sort = extractSort(pageable);
        return findAll(brandId, sort, pageable);
    }

    @Transactional
    public ProductResult create(ProductInfo info) {
        Product product = productDomainService.create(info);
        return ProductResult.from(product, 0L);
    }

    @Transactional
    public ProductResult update(Long id, ProductInfo info) {
        Product product = productDomainService.update(id, info);
        long likeCount = likeCountQueryService.getLikeCount(id);
        return ProductResult.from(product, likeCount);
    }

    @Transactional
    public void delete(Long id) {
        productDomainService.delete(id);
    }

    private ProductSort extractSort(Pageable pageable) {
        Sort sort = pageable.getSort();
        if (sort.isUnsorted()) {
            return ProductSort.LATEST;
        }

        for (Sort.Order order : sort) {
            String property = order.getProperty();
            if ("likeCount".equalsIgnoreCase(property) || "like_count".equalsIgnoreCase(property)) {
                return ProductSort.LIKES_DESC;
            }
            if ("price".equalsIgnoreCase(property) && order.isAscending()) {
                return ProductSort.PRICE_ASC;
            }
        }

        return ProductSort.LATEST;
    }
}
