package com.loopers.domain.product.service;

import com.loopers.domain.product.model.Product;
import com.loopers.domain.product.model.ProductCommand;
import com.loopers.domain.product.model.ProductItem;
import com.loopers.domain.product.repository.ProductCacheRepository;
import com.loopers.domain.product.repository.ProductCustomRepository;
import com.loopers.domain.product.repository.ProductRepository;
import com.loopers.support.enums.SortFilter;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductCustomRepository productCustomRepository;
    private final ProductCacheRepository productCacheRepository;

    public Product createProduct(Long brandId, ProductCommand.Create command) {
        Product product = Product.create(brandId, command);
        return productRepository.save(product);
    }

    public Product updateProduct(Long productId, ProductCommand.Update command) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 상품입니다."));
        product.update(command);
        productRepository.update(product);
        return product;
    }

    public void deleteProduct(Long productId) {
        productRepository.findById(productId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 상품입니다."));
        productRepository.deleteById(productId);
    }

    public void deleteProductsByBrandId(Long brandId) {
        productRepository.deleteByBrandId(brandId);
    }

    public Page<ProductItem> findProductList(Long brandId, SortFilter sortFilter, Pageable pageable) {
        if (isFirstPageLatest(brandId, sortFilter, pageable)) {
            Optional<ProductCacheRepository.CachedPage> cached = productCacheRepository.getFirstPage();
            if (cached.isPresent()) {
                List<ProductItem> items = cached.get().items().stream()
                        .map(item -> item.withFavoriteCnt(resolveLikeCount(item.id())))
                        .toList();
                return new PageImpl<>(items, pageable, cached.get().totalElements());
            }
        }

        Page<ProductItem> result = productCustomRepository.findProductList(brandId, sortFilter, pageable);

        if (isFirstPageLatest(brandId, sortFilter, pageable)) {
            productCacheRepository.putFirstPage(result.getContent(), result.getTotalElements());
            result.getContent().forEach(item ->
                    productCacheRepository.initLikeCount(item.id(), item.favoriteCnt()));
        }

        return result;
    }

    public ProductItem findProductDetail(Long productId) {
        Optional<ProductItem> cached = productCacheRepository.get(productId);
        if (cached.isPresent()) {
            long likeCount = resolveLikeCount(productId);
            return cached.get().withFavoriteCnt(likeCount);
        }

        ProductItem item = productCustomRepository.findProduct(productId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 상품입니다."));
        productCacheRepository.put(productId, item);
        productCacheRepository.initLikeCount(productId, item.favoriteCnt());
        return item;
    }

    public Product findProduct(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 상품입니다."));
    }

    public void increaseLikeCount(Long productId) {
        productRepository.increaseLikeCount(productId);
        productCacheRepository.incrementLikeCount(productId);
    }

    public void decreaseLikeCount(Long productId) {
        productRepository.decreaseLikeCount(productId);
        productCacheRepository.decrementLikeCount(productId);
    }

    public void decreaseStock(Product product, int quantity) {
        product.decreaseStock(quantity);
        productRepository.update(product);
    }

    public Product decreaseStockAtomic(Long productId, int quantity) {
        productRepository.findById(productId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 상품입니다."));

        int updatedRows = productRepository.decreaseStock(productId, quantity);
        if (updatedRows == 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재고가 부족합니다.");
        }
        productCacheRepository.evict(productId);
        productCacheRepository.evictFirstPage();
        return productRepository.findById(productId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 상품입니다."));
    }

    public List<Product> findProductsByBrandId(Long brandId) {
        return productRepository.findAll(Pageable.unpaged(), brandId).getContent();
    }

    public List<Product> getProductsByIds(List<Long> productIds) {
        List<Product> products = productRepository.findByIds(productIds);
        if (products.size() != productIds.size()) {
            throw new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 상품입니다.");
        }
        return products;
    }

    private boolean isFirstPageLatest(Long brandId, SortFilter sortFilter, Pageable pageable) {
        return brandId == null && sortFilter == SortFilter.LATEST && pageable.getPageNumber() == 0;
    }

    private long resolveLikeCount(Long productId) {
        return productCacheRepository.getLikeCount(productId)
                .orElseGet(() -> {
                    Product product = productRepository.findById(productId)
                            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 상품입니다."));
                    long count = product.getLikeCount();
                    productCacheRepository.initLikeCount(productId, count);
                    return count;
                });
    }
}
