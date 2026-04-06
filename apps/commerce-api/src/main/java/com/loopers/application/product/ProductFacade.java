package com.loopers.application.product;

import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class ProductFacade {

    private final ProductService productService;

    @Cacheable(cacheNames = "product", key = "#productId")
    @Transactional(readOnly = true)
    public ProductInfo getProduct(Long productId) {
        Product product = productService.getProductById(productId);
        return ProductInfo.from(product);
    }

    @Transactional(readOnly = true)
    public Page<ProductInfo> getProducts(Long brandId, String sort, int page, int size) {
        Sort sorting = resolveSort(sort);
        Pageable pageable = PageRequest.of(page, size, sorting);
        Page<Product> products = productService.getProducts(brandId, pageable);
        return products.map(ProductInfo::from);
    }

    private Sort resolveSort(String sort) {
        if ("likes_desc".equals(sort)) {
            return Sort.by(Sort.Order.desc("likeCount"), Sort.Order.desc("id"));
        }
        if (sort == null || "latest".equals(sort)) {
            return Sort.by(Sort.Order.desc("id"));
        }
        throw new CoreException(ErrorType.BAD_REQUEST, "지원하지 않는 정렬 방식입니다: " + sort);
    }
}
