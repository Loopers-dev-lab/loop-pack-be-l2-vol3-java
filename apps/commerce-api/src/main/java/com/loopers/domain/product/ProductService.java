package com.loopers.domain.product;

import com.loopers.domain.product.exception.ProductNotDeletedException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Product 도메인 서비스
 * 상품 엔티티 생성/상태 변경 규칙을 조합한다.
 */
@Component
public class ProductService {

    public Product createProduct(Long brandId, String name, String description, BigDecimal price, Integer stock) {
        return Product.create(brandId, name, description, price, stock);
    }

    public void updateProduct(Product product, String name, String description, BigDecimal price, Integer stock) {
        product.updateInfo(name, description, price, stock);
    }

    public void increaseStock(Product product, Integer quantity) {
        product.increaseStock(quantity);
    }

    public void decreaseStock(Product product, Integer quantity) {
        product.decreaseStock(quantity);
    }

    public void deleteProduct(Product product) {
        product.delete();
    }

    public void restoreProduct(Product product, Long productId) {
        if (!product.isDeleted()) {
            throw new ProductNotDeletedException(productId);
        }
        product.restore();
    }
}
