package com.loopers.application.product;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.productlike.ProductLikeService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductFacade {

    private final ProductService productService;
    private final ProductLikeService productLikeService;
    private final BrandService brandService;

    @Transactional
    public ProductInfo registerProduct(Long brandId, String name, String description, BigDecimal price, Integer stock, String imageUrl) {
        Brand brand = brandService.getBrand(brandId);
        Product product = productService.register(brandId, name, description, price, stock, imageUrl);
        return ProductInfo.from(product, brand);
    }

    public ProductInfo getProduct(Long id) {
        Product product = productService.getById(id);
        Brand brand = brandService.getBrand(product.getBrandId());
        return ProductInfo.from(product, brand);
    }

    public Page<ProductInfo> getProducts(Pageable pageable) {
        Page<Product> products = productService.getAll(pageable);
        Map<Long, Brand> brandMap = getBrandMap(products.getContent());
        return products.map(p -> ProductInfo.from(p, brandMap.get(p.getBrandId())));
    }

    public Page<ProductInfo> getProductsByBrandId(Long brandId, Pageable pageable) {
        Brand brand = brandService.getBrand(brandId);
        return productService.getAllByBrandId(brandId, pageable)
                .map(p -> ProductInfo.from(p, brand));
    }

    public List<ProductInfo> getProductsByIds(List<Long> ids) {
        List<Product> products = productService.getByIds(ids);
        Map<Long, Brand> brandMap = getBrandMap(products);
        return products.stream()
                .map(p -> ProductInfo.from(p, brandMap.get(p.getBrandId())))
                .toList();
    }

    @Transactional
    public ProductInfo updateProduct(Long id, String name, String description, BigDecimal price, Integer stock, String imageUrl) {
        Product product = productService.update(id, name, description, price, stock, imageUrl);
        Brand brand = brandService.getBrand(product.getBrandId());
        return ProductInfo.from(product, brand);
    }

    private Map<Long, Brand> getBrandMap(List<Product> products) {
        List<Long> brandIds = products.stream()
                .map(Product::getBrandId)
                .distinct()
                .toList();
        return brandService.getByIds(brandIds).stream()
                .collect(Collectors.toMap(Brand::getId, b -> b));
    }

    @Transactional
    public void deleteProduct(Long id) {
        // 상품 삭제 전에 좋아요 먼저 삭제
        productLikeService.deleteByProductId(id);
        
        // 상품 삭제 (Soft Delete)
        productService.delete(id);
    }
}
