package com.loopers.application.product;

import com.loopers.application.brand.BrandService;
import com.loopers.application.like.LikeService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Service
public class ProductFacade {
    private final BrandService brandService;
    private final ProductService productService;
    private final LikeService likeService;

    public ProductInfo register(ProductCreateCommand command) {
        brandService.getBrand(command.brandId());
        return productService.register(command);
    }

    public ProductInfo getProduct(Long id) {
        return productService.getProduct(id);
    }

    public Page<ProductInfo> getAllProducts(Long brandId, Pageable pageable) {
        return productService.getAllProducts(brandId, pageable);
    }

    public ProductInfo update(Long id, ProductUpdateCommand command) {
        return productService.update(id, command);
    }

    @Transactional
    public void delete(Long id) {
        likeService.deleteAllByProductIds(List.of(id));
        productService.delete(id);
    }
}
