package com.loopers.domain.product;

import com.loopers.domain.PageResult;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    public Product register(Long brandId, String name, Money price, Stock stock) {
        return productRepository.save(new Product(brandId, name, price, stock));
    }

    public Product getById(Long id) {
        return productRepository.findById(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."));
    }

    public Map<Long, Product> getByIds(Set<Long> ids) {
        List<Product> products = productRepository.findAllByIds(ids);
        return products.stream().collect(Collectors.toMap(Product::getId, product -> product));
    }

    public PageResult<Product> getAll(Long brandId, ProductSortType sort, int page, int size) {
        return productRepository.findAll(brandId, sort, page, size);
    }

    public Product update(Long id, String name, Money price, Stock stock) {
        Product product = getById(id);
        product.update(name, price, stock);
        return productRepository.save(product);
    }

    public void delete(Long id) {
        Product product = getById(id);
        product.delete();
        productRepository.save(product);
    }

    public void deleteAllByBrandId(Long brandId) {
        productRepository.softDeleteAllByBrandId(brandId);
    }

    public void incrementLikeCount(Long productId) {
        productRepository.incrementLikeCount(productId);
    }

    public void decrementLikeCount(Long productId) {
        productRepository.decrementLikeCount(productId);
    }
}
