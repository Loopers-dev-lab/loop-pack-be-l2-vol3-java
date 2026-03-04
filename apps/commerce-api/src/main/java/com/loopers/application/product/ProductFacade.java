package com.loopers.application.product;

import com.loopers.application.brand.BrandService;
import com.loopers.domain.brand.Brand;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.domain.product.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ProductFacade {

    private final ProductService productService;
    private final BrandService brandService;

    // Command

    @Transactional
    public ProductInfo register(ProductCommand.Register command) {
        Brand brand = brandService.getActiveBrand(command.brandId());
        Product product = productService.register(command);
        return ProductInfo.from(product, brand.getName());
    }

    @Transactional
    public ProductInfo updateInfo(Long productId, ProductCommand.UpdateInfo command) {
        Product product = productService.updateInfo(productId, command);
        Brand brand = brandService.getBrand(product.getBrandId());
        return ProductInfo.from(product, brand.getName());
    }

    @Transactional
    public void delete(Long productId) {
        productService.delete(productId);
    }

    // Query

    @Transactional(readOnly = true)
    public ProductInfo getDetail(Long productId) {
        Product product = productService.getProduct(productId);
        Brand brand = brandService.getBrand(product.getBrandId());
        return ProductInfo.from(product, brand.getName());
    }

    @Transactional(readOnly = true)
    public ProductInfo getActiveDetail(Long productId) {
        Product product = productService.getActiveProduct(productId);
        Brand brand = brandService.getBrand(product.getBrandId());
        return ProductInfo.from(product, brand.getName());
    }

    @Transactional(readOnly = true)
    public Page<ProductInfo> getActiveList(Long brandId, Pageable pageable) {
        Page<Product> products = productService.findActiveProducts(brandId, pageable);

        Set<Long> brandIds = products.getContent().stream()
                .map(Product::getBrandId)
                .collect(Collectors.toSet());

        Map<Long, Brand> brandMap = brandService.getBrandsMapByIds(brandIds);

        for (Product product : products.getContent()) {
            if (!brandMap.containsKey(product.getBrandId())) {
                throw new CoreException(ErrorType.NOT_FOUND,
                        "브랜드 매핑 누락. productId=" + product.getId() + ", brandId=" + product.getBrandId());
            }
        }

        return products.map(product -> ProductInfo.from(product, brandMap.get(product.getBrandId()).getName()));
    }

    @Transactional(readOnly = true)
    public Page<ProductInfo> getList(String name, Long brandId, Boolean deleted, Pageable pageable) {
        Page<Product> products = productService.findProducts(name, brandId, deleted, pageable);

        Set<Long> brandIds = products.getContent().stream()
                .map(Product::getBrandId)
                .collect(Collectors.toSet());

        Map<Long, Brand> brandMap = brandService.getBrandsMapByIds(brandIds);

        for (Product product : products.getContent()) {
            if (!brandMap.containsKey(product.getBrandId())) {
                throw new CoreException(ErrorType.NOT_FOUND,
                        "브랜드 매핑 누락. productId=" + product.getId() + ", brandId=" + product.getBrandId());
            }
        }

        return products.map(product -> ProductInfo.from(product, brandMap.get(product.getBrandId()).getName()));
    }
}
