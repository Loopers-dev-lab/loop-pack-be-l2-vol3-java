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
        Product product = Product.create(command.brandId(), command.name(), command.price(),
                command.stockQuantity(), command.description());
        return productRepository.save(product);
    }

    @Transactional
    public Product updateInfo(Long productId, ProductCommand.UpdateInfo command) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 상품입니다"));

        product.updateInfo(command.name(), command.price(), command.stockQuantity(), command.description());
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
    public void decrementLikeCount(Long productId) {
        productRepository.decrementLikeCount(productId);
    }

    @Transactional
    public void decreaseStocks(Map<Long, Integer> productQuantities) {
        List<Long> sortedIds = productQuantities.keySet().stream()
                .sorted()
                .toList();

        for (Long productId : sortedIds) {
            int updated = productRepository.decreaseStock(
                    productId, productQuantities.get(productId)
            );
            if (updated == 0) {
                throw new CoreException(ErrorType.BAD_REQUEST,
                        "재고가 부족하거나 존재하지 않는 상품입니다. productId=" + productId);
            }
        }
    }

    @Transactional
    public int softDeleteByBrandIdInBatch(Long brandId, int batchSize) {
        return productRepository.softDeleteByBrandIdInBatch(brandId, batchSize);
    }

    @Deprecated(forRemoval = true)
    @Transactional
    public void deleteAllByBrandId(Long brandId) {
        List<Product> products = productRepository.findAllByBrandId(brandId);
        products.forEach(Product::delete);
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
    public Page<Product> findProducts(String name, Long brandId, Boolean deleted, Pageable pageable) {
        return productRepository.findAll(name, brandId, deleted, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Product> findActiveProducts(Long brandId, Pageable pageable) {
        return productRepository.findAllActiveWithActiveBrand(brandId, pageable);
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
