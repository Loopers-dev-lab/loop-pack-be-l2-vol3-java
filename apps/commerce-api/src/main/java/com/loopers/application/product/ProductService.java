package com.loopers.application.product;

import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductDomainService;
import com.loopers.domain.product.ProductInfo;
import com.loopers.domain.product.ProductSort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductDomainService productDomainService;

    @Transactional(readOnly = true)
    public ProductResult findById(Long id) {
        Product product = productDomainService.findById(id);
        return ProductResult.from(product);
    }

    @Transactional(readOnly = true)
    public Page<ProductResult> findAll(Long brandId, Pageable pageable) {
        // 기본 정렬은 LATEST
        ProductSort sort = ProductSort.LATEST;

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

        List<ProductResult> results = products.stream()
            .map(ProductResult::from)
            .toList();

        return new PageImpl<>(results, pageable, total);
    }

    @Transactional
    public ProductResult create(ProductInfo info) {
        Product product = productDomainService.create(info);
        return ProductResult.from(product);
    }

    @Transactional
    public ProductResult update(Long id, ProductInfo info) {
        Product product = productDomainService.update(id, info);
        return ProductResult.from(product);
    }

    @Transactional
    public void delete(Long id) {
        productDomainService.delete(id);
    }
}
