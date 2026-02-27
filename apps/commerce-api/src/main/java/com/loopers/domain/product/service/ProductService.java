package com.loopers.domain.product.service;

import com.loopers.domain.product.model.Product;
import com.loopers.domain.product.model.ProductCommand;
import com.loopers.domain.product.model.ProductItem;
import com.loopers.domain.product.repository.ProductCustomRepository;
import com.loopers.domain.product.repository.ProductRepository;
import com.loopers.support.enums.SortFilter;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductCustomRepository productCustomRepository;

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

    public Page<ProductItem> findProductList(Long brandId, Long memberId, SortFilter sortFilter, Pageable pageable) {
        return productCustomRepository.findProductList(brandId, memberId, sortFilter, pageable);
    }

    public ProductItem findProduct(Long productId, Long memberId) {
        return productCustomRepository.findProduct(productId, memberId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 상품입니다."));
    }

    public Product findProduct(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 상품입니다."));
    }

    public void decreaseStock(Product product, int quantity) {
        product.decreaseStock(quantity);
        productRepository.update(product);
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
}
