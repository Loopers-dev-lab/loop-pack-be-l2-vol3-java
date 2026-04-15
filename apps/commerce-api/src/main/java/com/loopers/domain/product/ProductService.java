package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public Product getProduct(Long productId) {
        return productRepository.findById(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public Product getActiveProduct(Long productId) {
        Product product = getProduct(productId);
        if (product.isDeleted()) {
            throw new CoreException(ErrorType.NOT_FOUND, "삭제된 상품입니다.");
        }
        return product;
    }

    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public List<Product> getProductsByIds(List<Long> ids) {
        return productRepository.findByIds(ids);
    }

    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public List<Product> getAllActiveProducts() {
        return productRepository.findAllActive();
    }

    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public List<Product> getActiveProductsByCategoryId(Long categoryId) {
        return productRepository.findAllActiveByCategoryId(categoryId);
    }

    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public Page<Product> getProducts(Long categoryId, Long brandId, String keyword, ProductSortType sort, Pageable pageable) {
        return productRepository.findProducts(categoryId, brandId, keyword, sort, pageable);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public Product createProduct(String name, Long brandId, Long categoryId, Long basePrice,
                                  List<ProductOption> options, List<ProductImage> images) {
        Product product = new Product(name, brandId, categoryId, basePrice, options, images);
        return productRepository.save(product);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public Product createProduct(String name, Long brandId, Long categoryId, Long basePrice) {
        return createProduct(name, brandId, categoryId, basePrice, null, null);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public Product updateProduct(Long productId, String name, Long categoryId, Long basePrice,
                                  Long discount, DiscountType discountType, ProductStatus status) {
        Product product = getProduct(productId);
        product.update(name, categoryId, basePrice, discount, discountType, status);
        return productRepository.save(product);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void deleteProduct(Long productId) {
        Product product = getProduct(productId);
        productRepository.delete(product.getId());
    }

    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public Product validateProduct(Long productId) {
        Product product = getActiveProduct(productId);
        if (!product.isAvailable()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "판매 중인 상품만 주문 가능합니다.");
        }
        return product;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void decreaseStock(Long productId, Long optionId, int quantity) {
        Product product = getProductForUpdate(productId);
        product.decreaseStock(optionId, quantity);
        productRepository.save(product);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void increaseStock(Long productId, Long optionId, int quantity) {
        Product product = getProductForUpdate(productId);
        product.increaseStock(optionId, quantity);
        productRepository.save(product);
    }

    private Product getProductForUpdate(Long productId) {
        return productRepository.findByIdForUpdate(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public ProductOption getProductOption(Long productId, Long optionId) {
        Product product = getProduct(productId);
        return product.getOption(optionId);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void deleteProductsByBrandId(Long brandId) {
        List<Product> products = productRepository.findAllActiveByBrandId(brandId);
        if (!products.isEmpty()) {
            List<Long> productIds = products.stream()
                .map(Product::getId)
                .toList();
            productRepository.softDeleteAllByIds(productIds);
        }
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void deleteProductsByCategoryIds(List<Long> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return;
        }
        List<Product> products = productRepository.findAllActiveByCategoryIds(categoryIds);
        if (!products.isEmpty()) {
            List<Long> productIds = products.stream()
                .map(Product::getId)
                .toList();
            productRepository.softDeleteAllByIds(productIds);
        }
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public Long increaseLikeCount(Long productId) {
        return productRepository.increaseLikeCount(productId);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public Long decreaseLikeCount(Long productId) {
        return productRepository.decreaseLikeCount(productId);
    }

    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public Page<Product> getProductsForAdmin(Pageable pageable) {
        return productRepository.findAllIncludingDeleted(pageable);
    }
}