package com.loopers.application.product;

import com.loopers.domain.PageResult;
import com.loopers.domain.brand.BrandDomainService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductDomainService;
import com.loopers.domain.product.ProductSortType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;

@RequiredArgsConstructor
@Component
public class ProductApplicationService {

    private final ProductDomainService productService;
    private final BrandDomainService brandService;

    @Transactional
    public Product register(Long brandId, String name, int price, int stock) {
        brandService.getById(brandId);
        return productService.register(brandId, name, price, stock);
    }

    @Transactional(readOnly = true)
    public Product getById(Long id) {
        return productService.getById(id);
    }

    @Transactional(readOnly = true)
    public Map<Long, Product> getByIds(Set<Long> ids) {
        return productService.getByIds(ids);
    }

    @Transactional(readOnly = true)
    public PageResult<Product> getAll(Long brandId, ProductSortType sort, int page, int size) {
        return productService.getAll(brandId, sort, page, size);
    }

    @Transactional
    public Product update(Long id, String name, int price, int stock) {
        return productService.update(id, name, price, stock);
    }

    @Transactional
    public void delete(Long id) {
        productService.delete(id);
    }
}
