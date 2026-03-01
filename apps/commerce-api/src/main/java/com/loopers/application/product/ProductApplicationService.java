package com.loopers.application.product;

import com.loopers.domain.PageResult;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandDomainService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductDomainService;
import com.loopers.domain.product.ProductSortType;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class ProductApplicationService {

    private final ProductDomainService productService;
    private final BrandDomainService brandService;

    @Transactional
    public ProductWithBrand register(RegisterProductCommand command) {
        Brand brand = brandService.getById(command.brandId());
        Product product = productService.register(command.brandId(), command.name(), command.price(), command.stock());
        return new ProductWithBrand(product, brand);
    }

    @Transactional(readOnly = true)
    public ProductWithBrand getProductWithBrand(Long id) {
        Product product = productService.getById(id);
        Brand brand = brandService.getById(product.getBrandId());
        return new ProductWithBrand(product, brand);
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
    public ProductPageWithBrands getAll(Long brandId, ProductSortType sort, int page, int size) {
        PageResult<Product> result = productService.getAll(brandId, sort, page, size);
        Set<Long> brandIds = result.items().stream()
            .map(Product::getBrandId)
            .collect(Collectors.toSet());
        Map<Long, Brand> brandMap = brandService.getByIds(brandIds);
        return new ProductPageWithBrands(result, brandMap);
    }

    @Transactional(readOnly = true)
    public ProductPageWithBrands getAllForAdmin(Long brandId, ProductSortType sort, int page, int size) {
        PageResult<Product> result = productService.getAll(brandId, sort, page, size);
        Set<Long> brandIds = result.items().stream()
            .map(Product::getBrandId)
            .collect(Collectors.toSet());
        Map<Long, Brand> brandMap = brandService.getByIds(brandIds);

        for (Product product : result.items()) {
            if (!brandMap.containsKey(product.getBrandId())) {
                throw new CoreException(ErrorType.INTERNAL_ERROR,
                    "브랜드를 찾을 수 없습니다. productId=" + product.getId() + ", brandId=" + product.getBrandId());
            }
        }

        return new ProductPageWithBrands(result, brandMap);
    }

    @Transactional
    public ProductWithBrand update(UpdateProductCommand command) {
        Product product = productService.update(command.productId(), command.name(), command.price(), command.stock());
        Brand brand = brandService.getById(product.getBrandId());
        return new ProductWithBrand(product, brand);
    }

    @Transactional
    public void delete(Long id) {
        productService.delete(id);
    }
}
