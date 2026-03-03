package com.loopers.application.product;

import com.loopers.application.brand.BrandService;
import com.loopers.domain.brand.Brand;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.domain.product.Product;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Validated
@RequiredArgsConstructor
public class ProductFacade {

    private final ProductService productService;
    private final BrandService brandService;

    // Command

    @Transactional
    public ProductInfo register(@Valid ProductRequest.Register request) {
        Brand brand = brandService.getActiveBrand(request.brandId());
        ProductCommand.Create command = ProductCommand.Create.of(
                request.brandId(), request.name(), request.price(),
                request.stockQuantity(), request.description());
        Product product = productService.register(command);
        return ProductInfo.from(product, brand.getName());
    }

    @Transactional
    public ProductInfo updateInfo(Long productId, @Valid ProductRequest.UpdateInfo request) {
        ProductCommand.UpdateInfo command = ProductCommand.UpdateInfo.of(
                request.name(), request.price(),
                request.stockQuantity(), request.description());
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
    public Page<ProductInfo> getActiveList(@Valid ProductRequest.ListActive request) {
        Page<Product> products = productService.findActiveProducts(request.brandId(), request.toPageable());

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
    public Page<ProductInfo> getList(@Valid ProductRequest.ListAll request) {
        Page<Product> products = productService.findProducts(
                request.name(), request.brandId(), request.toDeleted(), request.toPageable());

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
