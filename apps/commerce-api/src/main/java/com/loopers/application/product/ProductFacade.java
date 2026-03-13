package com.loopers.application.product;

import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductWithBrand;
import com.loopers.domain.product.vo.Price;
import com.loopers.domain.product.vo.Stock;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductFacade {

    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;
    private final LikeRepository likeRepository;

    public ProductWithBrand getProductDetail(Long productId) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."));
        Brand brand = brandRepository.findById(product.getBrandId()).orElse(null);
        String brandName = (brand != null) ? brand.getName() : null;
        long likeCount = likeRepository.countByProductId(productId);
        return new ProductWithBrand(product, brandName, likeCount);
    }

    public List<ProductWithBrand> getAllProducts() {
        return enrichWithLikeCount(productRepository.findAllWithBrand());
    }

    public List<ProductWithBrand> getAllProducts(String sort) {
        List<ProductWithBrand> results = enrichWithLikeCount(
            productRepository.findAllWithBrand(sort));

        if ("likes_desc".equals(sort)) {
            return results.stream()
                .sorted(Comparator.comparingLong(ProductWithBrand::likeCount).reversed())
                .toList();
        }
        return results;
    }

    public List<ProductWithBrand> getProductsByBrandId(Long brandId) {
        brandRepository.findById(brandId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다."));
        return enrichWithLikeCount(productRepository.findAllByBrandIdWithBrand(brandId));
    }

    @Transactional
    public Product createProduct(Long brandId, String name, int price, int stockQuantity) {
        brandRepository.findById(brandId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다."));
        Product product = new Product(brandId, name, new Price(price), new Stock(stockQuantity));
        return productRepository.save(product);
    }

    @Transactional
    public Product updateProduct(Long productId, String name, int price, int stockQuantity) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."));
        product.changeName(name);
        product.changePrice(new Price(price));
        product.changeStock(new Stock(stockQuantity));
        return product;
    }

    @Transactional
    public void deleteProduct(Long productId) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."));
        likeRepository.deleteAllByProductId(productId);
        product.delete();
    }

    private List<ProductWithBrand> enrichWithLikeCount(List<ProductWithBrand> products) {
        List<Long> productIds = products.stream()
            .map(pwb -> pwb.product().getId())
            .toList();
        Map<Long, Long> likeCounts = likeRepository.countByProductIds(productIds);
        return products.stream()
            .map(pwb -> new ProductWithBrand(
                pwb.product(), pwb.brandName(),
                likeCounts.getOrDefault(pwb.product().getId(), 0L)))
            .toList();
    }
}
