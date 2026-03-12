package com.loopers.domain.product;

import com.loopers.domain.common.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ProductDomainService {

    private final ProductRepository productRepository;
    private final ProductValidator productValidator;

    public Product create(ProductInfo info) {
        productValidator.validateBrandExists(info.brandId());
        Product product = Product.create(
            info.brandId(),
            info.name(),
            info.description(),
            new Money(info.price()),
            new Stock(info.stock()),
            info.imageUrl()
        );
        return productRepository.save(product);
    }

    public Product update(Long id, ProductInfo info) {
        Product product = findById(id);
        product.update(
            info.name(),
            info.description(),
            new Money(info.price()),
            new Stock(info.stock()),
            info.imageUrl()
        );
        return productRepository.save(product);
    }

    public Product findById(Long id) {
        return productRepository.findByIdActive(id)
            .orElseThrow(() -> new CoreException(ErrorType.PRODUCT_NOT_FOUND));
    }

    public Product findByIdWithLock(Long id) {
        return productRepository.findByIdWithLock(id)
            .orElseThrow(() -> new CoreException(ErrorType.PRODUCT_NOT_FOUND));
    }

    public List<Product> findAll(ProductSort sort, int offset, int limit) {
        return productRepository.findAllActive(sort, offset, limit);
    }

    public List<Product> findAllByBrandId(Long brandId, ProductSort sort, int offset, int limit) {
        return productRepository.findAllByBrandIdActive(brandId, sort, offset, limit);
    }

    public long countAll() {
        return productRepository.countActive();
    }

    public long countByBrandId(Long brandId) {
        return productRepository.countByBrandIdActive(brandId);
    }

    public void decreaseStock(Product product, int quantity) {
        product.decreaseStock(quantity);
        productRepository.save(product);
    }

    public void delete(Long id) {
        Product product = findById(id);
        product.delete();
        productRepository.save(product);
    }

    public void deleteAllByBrandId(Long brandId) {
        List<Product> products = productRepository.findAllByBrandIdActive(brandId);
        products.forEach(product -> {
            product.delete();
            productRepository.save(product);
        });
    }
}
