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
public class ProductDomainService {

    private final ProductRepository productRepository;

    public Product register(Long brandId, String name, int price) {
        return productRepository.save(new Product(brandId, name, new Money(price)));
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

    public Product update(Long id, String name, int price) {
        Product product = getById(id);
        product.changeDetails(name, new Money(price));
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
        int affected = productRepository.incrementLikeCount(productId);
        if (affected == 0) {
            throw new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다.");
        }
    }

    public void decrementLikeCount(Long productId) {
        int affected = productRepository.decrementLikeCount(productId);
        if (affected == 0) {
            productRepository.findById(productId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."));
            throw new CoreException(ErrorType.BAD_REQUEST, "좋아요 수는 0 미만이 될 수 없습니다.");
        }
    }
}
