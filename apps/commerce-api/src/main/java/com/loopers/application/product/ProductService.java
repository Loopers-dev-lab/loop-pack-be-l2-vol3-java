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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;

    // Command

    @Transactional
    public Product register(ProductCommand.Create command) {
        Product product = Product.create(command.brandId(), command.name(), command.price(),
                command.stockQuantity(), command.description());
        return productRepository.save(product);
    }

    @Transactional
    public Product update(Long productId, ProductCommand.Update command) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 상품입니다"));

        product.update(command.name(), command.price(), command.stockQuantity(), command.description());
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
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 상품입니다"));
        product.incrementLikeCount();
    }

    @Transactional
    public void decrementLikeCount(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 상품입니다"));
        product.decrementLikeCount();
    }

    @Transactional
    public List<Product> deductStocks(Map<Long, Integer> productQuantities) {
        List<Long> productIds = new ArrayList<>(productQuantities.keySet());
        List<Product> products = productRepository.findAllByIdInForUpdate(productIds);

        if (products.size() != productIds.size()) {
            throw new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 상품이 포함되어 있습니다");
        }

        for (Product product : products) {
            product.deductStock(productQuantities.get(product.getId()));
        }

        return products;
    }

    @Transactional
    public void deleteAllByBrandId(Long brandId) {
        List<Product> products = productRepository.findAllByBrandId(brandId);
        products.forEach(Product::delete);
    }

    // Query

    public Product getProduct(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 상품입니다"));
    }

    public Product getActiveProduct(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 상품입니다"));
        product.validateNotDeleted();
        return product;
    }

    public Page<Product> findProducts(String name, Long brandId, Boolean deleted, Pageable pageable) {
        return productRepository.findAll(name, brandId, deleted, pageable);
    }

    public Page<Product> findActiveProducts(Long brandId, Pageable pageable) {
        return productRepository.findAllActive(brandId, pageable);
    }

    public Map<Long, Product> getProductsMapByIds(Set<Long> productIds) {
        return productRepository.findAllByIdIn(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
    }
}
