package com.loopers.application.product;

import com.loopers.application.product.CreateProductUseCase;
import com.loopers.application.product.DeleteProductUseCase;
import com.loopers.application.product.UpdateProductUseCase;
import com.loopers.domain.model.product.Price;
import com.loopers.domain.model.product.Product;
import com.loopers.domain.model.product.ProductName;
import com.loopers.domain.model.product.Stock;
import com.loopers.domain.repository.BrandRepository;
import com.loopers.domain.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ProductService implements CreateProductUseCase, UpdateProductUseCase, DeleteProductUseCase {

    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;

    public ProductService(ProductRepository productRepository, BrandRepository brandRepository) {
        this.productRepository = productRepository;
        this.brandRepository = brandRepository;
    }

    @Override
    public void createProduct(ProductCreateCommand command) {
        brandRepository.findActiveById(command.brandId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 브랜드입니다."));

        Price salePriceVo = command.salePrice() != null ? Price.of(command.salePrice()) : null;
        Product product = Product.create(command.brandId(), ProductName.of(command.name()),
                Price.of(command.price()), salePriceVo, Stock.of(command.stock()), command.description());
        productRepository.save(product);
    }

    @Override
    public void updateProduct(ProductUpdateCommand command) {
        Product product = findProduct(command.productId());
        Price salePriceVo = command.salePrice() != null ? Price.of(command.salePrice()) : null;
        Product updated = product.update(ProductName.of(command.name()), Price.of(command.price()),
                salePriceVo, Stock.of(command.stock()), command.description());
        productRepository.save(updated);
    }

    @Override
    public void deleteProduct(Long productId) {
        Product product = findProduct(productId);
        Product deleted = product.delete();
        productRepository.save(deleted);
    }

    private Product findProduct(Long productId) {
        return productRepository.findActiveById(productId)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."));
    }
}
