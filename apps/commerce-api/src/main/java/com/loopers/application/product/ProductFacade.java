package com.loopers.application.product;

import com.loopers.domain.event.ProductViewedEvent;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductWithBrand;
import com.loopers.domain.product.vo.Price;
import com.loopers.domain.product.vo.Stock;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.interfaces.api.product.ProductDto;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
    private final ProductCachePort productCachePort;
    private final ApplicationEventPublisher applicationEventPublisher;

    // ── 상품 상세 (캐시 적용) ──

    public ProductWithBrand getProductDetail(Long productId) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."));
        Brand brand = brandRepository.findById(product.getBrandId()).orElse(null);
        String brandName = (brand != null) ? brand.getName() : null;
        return new ProductWithBrand(product, brandName, product.getLikeCount());
    }

    public ProductDto.ProductResponse getProductDetailCached(Long productId) {
        ProductDto.ProductResponse cached = productCachePort.getProductDetail(productId);
        if (cached != null) {
            applicationEventPublisher.publishEvent(new ProductViewedEvent(productId, 0L));
            return cached;
        }

        ProductWithBrand info = getProductDetail(productId);
        ProductDto.ProductResponse response = ProductDto.ProductResponse.from(info);
        productCachePort.putProductDetail(productId, response);
        applicationEventPublisher.publishEvent(new ProductViewedEvent(productId, 0L));
        return response;
    }

    // ── 상품 목록 (페이지네이션 + 캐시 적용) ──

    public Page<ProductWithBrand> getAllProducts(String sort, Pageable pageable) {
        return productRepository.findAllWithBrand(sort, pageable);
    }

    public Page<ProductWithBrand> getProductsByBrandId(Long brandId, String sort, Pageable pageable) {
        brandRepository.findById(brandId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다."));
        return productRepository.findAllByBrandIdWithBrand(brandId, sort, pageable);
    }

    public ProductDto.PagedProductResponse getAllProductsCached(Long brandId, String sort, int page, int size) {
        ProductDto.PagedProductResponse cached = productCachePort.getProductList(brandId, sort, page, size);
        if (cached != null) {
            return cached;
        }

        Pageable pageable = PageRequest.of(page, size);
        Page<ProductWithBrand> result;
        if (brandId != null) {
            result = getProductsByBrandId(brandId, sort, pageable);
        } else {
            result = getAllProducts(sort, pageable);
        }

        ProductDto.PagedProductResponse response = ProductDto.PagedProductResponse.from(result);
        productCachePort.putProductList(brandId, sort, page, size, response);
        return response;
    }

    // ── 기존 List 반환 메서드 (하위 호환 + 벤치마크용) ──

    public List<ProductWithBrand> getAllProducts() {
        return productRepository.findAllWithBrand();
    }

    public List<ProductWithBrand> getAllProducts(String sort) {
        return productRepository.findAllWithBrand(sort);
    }

    public List<ProductWithBrand> getProductsByBrandId(Long brandId) {
        brandRepository.findById(brandId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다."));
        return productRepository.findAllByBrandIdWithBrand(brandId);
    }

    // ── 벤치마크 전용: AS-IS 재현 (enrichWithLikeCount + in-memory sort) ──

    public List<ProductWithBrand> getAllProductsNoOptimization(String sort) {
        List<ProductWithBrand> results = enrichWithLikeCount(
            productRepository.findAllWithBrand(sort));

        if ("likes_desc".equals(sort)) {
            return results.stream()
                .sorted(Comparator.comparingLong(ProductWithBrand::likeCount).reversed())
                .toList();
        }
        return results;
    }

    // ── 상품 CUD (캐시 무효화 포함) ──

    @Transactional
    public Product createProduct(Long brandId, String name, int price, int stockQuantity) {
        brandRepository.findById(brandId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다."));
        Product product = new Product(brandId, name, new Price(price), new Stock(stockQuantity));
        Product saved = productRepository.save(product);
        productCachePort.evictProductList();
        return saved;
    }

    @Transactional
    public Product updateProduct(Long productId, String name, int price, int stockQuantity) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."));
        product.changeName(name);
        product.changePrice(new Price(price));
        product.changeStock(new Stock(stockQuantity));
        productCachePort.evictProductDetail(productId);
        productCachePort.evictProductList();
        return product;
    }

    @Transactional
    public void deleteProduct(Long productId) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."));
        likeRepository.deleteAllByProductId(productId);
        product.delete();
        productCachePort.evictProductDetail(productId);
        productCachePort.evictProductList();
    }

    // ── private: 벤치마크 전용 AS-IS 로직 보존 ──

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
