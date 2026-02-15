package com.loopers.application.product;

import com.loopers.domain.PageResult;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.Money;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductSortType;
import com.loopers.domain.product.Stock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class ProductFacade {

    private final ProductService productService;
    private final BrandService brandService;

    @Transactional
    public ProductInfo register(Long brandId, String name, int price, int stock) {
        Brand brand = brandService.getById(brandId);
        Product product = productService.register(brandId, name, new Money(price), new Stock(stock));
        return ProductInfo.from(product, brand);
    }

    public ProductInfo getById(Long id) {
        Product product = productService.getById(id);
        Brand brand = brandService.getById(product.getBrandId());
        return ProductInfo.from(product, brand);
    }

    public PageResult<ProductInfo> getAll(Long brandId, ProductSortType sort, int page, int size) {
        PageResult<Product> result = productService.getAll(brandId, sort, page, size);

        Set<Long> brandIds = result.items().stream()
            .map(Product::getBrandId)
            .collect(Collectors.toSet());
        Map<Long, Brand> brandMap = brandService.getByIds(brandIds);

        return result.map(product -> {
            Brand brand = brandMap.get(product.getBrandId());
            return ProductInfo.from(product, brand);
        });
    }

    @Transactional
    public ProductInfo update(Long id, String name, int price, int stock) {
        Product product = productService.update(id, name, new Money(price), new Stock(stock));
        Brand brand = brandService.getById(product.getBrandId());
        return ProductInfo.from(product, brand);
    }

    @Transactional
    public void delete(Long id) {
        productService.delete(id);
    }
}
