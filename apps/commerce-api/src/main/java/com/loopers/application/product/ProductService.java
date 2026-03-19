package com.loopers.application.product;

import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    // Command

    @Transactional
    public Product register(ProductCommand.Register command) {
        Product product = Product.create(command.brandId(), command.name(), command.price(), command.description());
        return productRepository.save(product);
    }

    @Transactional
    public Product updateInfo(Long productId, ProductCommand.UpdateInfo command) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 상품입니다"));

        product.updateInfo(command.name(), command.price(), command.description());
        return product;
    }

    @Transactional
    public void delete(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 상품입니다"));
        product.delete();
    }

    @Transactional
    public void incrementLikeCount(Long productId) {
        productRepository.incrementLikeCount(productId);
    }

    @Transactional
    public void decrementLikeCountIfPositive(Long productId) {
        productRepository.decrementLikeCountIfPositive(productId);
    }

    @Transactional(readOnly = true)
    public List<Long> findIdsForCleanup(Long brandId, int batchSize) {
        return productRepository.findIdsByBrandIdForCleanup(brandId, batchSize);
    }

    @Transactional
    public int softDeleteByIds(List<Long> ids) {
        return productRepository.softDeleteByIds(ids);
    }

    // Query

    @Transactional(readOnly = true)
    public Product getProduct(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 상품입니다"));
    }

    @Transactional(readOnly = true)
    public Product getActiveProduct(Long productId) {
        return productRepository.findActiveWithActiveBrand(productId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 상품입니다"));
    }

    @Transactional(readOnly = true)
    public void validateActiveProduct(Long productId) {
        if (!productRepository.existsActiveById(productId)) {
            throw new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 상품입니다");
        }
    }

    @Transactional(readOnly = true)
    public List<Product> getActiveProducts(Set<Long> productIds) {
        List<Product> products = productRepository.findAllActiveByIdIn(productIds);
        if (products.size() != productIds.size()) {
            throw new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 상품입니다");
        }
        return products;
    }

    @Transactional(readOnly = true)
    public Page<Product> findProducts(String name, Long brandId, Boolean deleted, Pageable pageable) {
        return productRepository.findAll(name, brandId, deleted, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Product> findActiveProducts(Long brandId, Pageable pageable) {
        return productRepository.findAllActiveWithActiveBrand(brandId, pageable);
    }

    @Transactional(readOnly = true)
    public List<Product> findActiveProductsCursor(Long brandId, Long cursor, int limit) {
        return productRepository.findAllActiveCursor(brandId, cursor, limit);
    }

    @Transactional(readOnly = true)
    public Map<Long, Product> getProductsMapByIds(Set<Long> productIds) {
        return productRepository.findAllByIdIn(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
    }

    @Transactional(readOnly = true)
    public List<Long> findBrandIdsWithUncleanedProducts() {
        return productRepository.findBrandIdsWithUncleanedProducts();
    }
}
