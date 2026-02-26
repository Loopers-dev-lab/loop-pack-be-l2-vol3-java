package com.loopers.application.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * 상품 유스케이스 조율.
 * 트랜잭션 경계, 도메인 결과 → ProductInfo 변환.
 * Controller는 Facade만 호출하며, request는 도메인 파라미터로 변환 후 Service에 전달한다.
 */
@Service
public class ProductFacade {

    private final ProductService productService;
    private final BrandService brandService;
    private final LikeRepository likeRepository;

    public ProductFacade(ProductService productService, BrandService brandService, LikeRepository likeRepository) {
        this.productService = productService;
        this.brandService = brandService;
        this.likeRepository = likeRepository;
    }

    @Transactional
    public ProductInfo register(Long brandId, String name, BigDecimal price, int stockQuantity) {
        ProductModel product = productService.register(brandId, name, price, stockQuantity);
        return ProductInfo.from(product);
    }

    @Transactional(readOnly = true)
    public Optional<ProductInfo> findById(Long id) {
        return productService.findById(id).map(ProductInfo::from);
    }

    @Transactional(readOnly = true)
    public Optional<ProductInfo> findByIdAndNotDeleted(Long id) {
        return productService.findByIdAndNotDeleted(id).map(ProductInfo::from);
    }

    @Transactional(readOnly = true)
    public Optional<ProductDetailInfo> getProductDetail(Long productId) {
        Optional<ProductModel> productOpt = productService.findByIdAndNotDeleted(productId);
        if (productOpt.isEmpty()) {
            return Optional.empty();
        }
        ProductModel product = productOpt.get();
        Optional<BrandModel> brandOpt = brandService.findByIdAndNotDeleted(product.getBrandId());
        if (brandOpt.isEmpty()) {
            return Optional.empty();
        }
        long likeCount = likeRepository.countByProductId(productId);
        return Optional.of(new ProductDetailInfo(
            product.getId(),
            product.getBrandId(),
            brandOpt.get().getName(),
            product.getName(),
            product.getPrice(),
            product.getStockQuantity(),
            likeCount
        ));
    }

    @Transactional
    public ProductInfo update(Long id, String name, BigDecimal price, int stockQuantity) {
        ProductModel product = productService.update(id, name, price, stockQuantity);
        return ProductInfo.from(product);
    }

    @Transactional
    public void delete(Long id) {
        productService.delete(id);
    }
}
