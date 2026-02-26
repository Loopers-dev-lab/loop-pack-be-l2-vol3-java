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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@Validated
@RequiredArgsConstructor
@Transactional(readOnly = true)
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
    public ProductInfo update(Long productId, @Valid ProductRequest.Update request) {
        ProductCommand.Update command = ProductCommand.Update.of(
                request.name(), request.price(),
                request.stockQuantity(), request.description());
        Product product = productService.update(productId, command);
        Brand brand = brandService.getBrand(product.getBrandId());
        return ProductInfo.from(product, brand.getName());
    }

    @Transactional
    public void delete(Long productId) {
        productService.delete(productId);
    }

    // Query

    public ProductInfo getDetail(Long productId) {
        Product product = productService.getProduct(productId);
        Brand brand = brandService.getBrand(product.getBrandId());
        return ProductInfo.from(product, brand.getName());
    }

    public ProductInfo getActiveDetail(Long productId) {
        Product product = productService.getActiveProduct(productId);
        Brand brand = brandService.getBrand(product.getBrandId());
        return ProductInfo.from(product, brand.getName());
    }

    public Page<ProductInfo> getActiveList(@Valid ProductRequest.ListActive request) {
        Page<Product> products = productService.findActiveProducts(request.brandId(), request.toPageable());

        Set<Long> brandIds = products.getContent().stream()
                .map(Product::getBrandId)
                .collect(Collectors.toSet());

        Map<Long, Brand> brandMap = brandService.getBrandsMapByIds(brandIds);

        return products.map(product -> {
            Brand brand = brandMap.get(product.getBrandId());
            if (brand == null) {
                throw new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 브랜드입니다");
            }
            return ProductInfo.from(product, brand.getName());
        });
    }

    public Page<ProductInfo> getList(@Valid ProductRequest.ListAll request) {
        Page<Product> products = productService.findProducts(
                request.name(), request.brandId(), request.toDeleted(), request.toPageable());

        Set<Long> brandIds = products.getContent().stream()
                .map(Product::getBrandId)
                .collect(Collectors.toSet());

        Map<Long, Brand> brandMap = brandService.getBrandsMapByIds(brandIds);

        return products.map(product -> {
            Brand brand = brandMap.get(product.getBrandId());
            if (brand == null) {
                throw new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 브랜드입니다");
            }
            return ProductInfo.from(product, brand.getName());
        });
    }
}
