package com.loopers.application.product;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.support.page.Page;
import com.loopers.support.page.PageSize;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;
    private final LikeRepository likeRepository;

    @Transactional
    public Long createProduct(ProductCommand.CreateProductCommand command) {
        if (!brandRepository.existsByIdAndDeletedAtIsNull(command.brandId())) {
            throw new CoreException(ErrorType.BRAND_NOT_FOUND);
        }
        Product product = Product.create(
                command.brandId(),
                command.name(),
                command.thumbnailUrl(),
                command.price(),
                command.stock(),
                command.description()
        );
        Product saved = productRepository.save(product);
        return saved.getId();
    }

    @Transactional(readOnly = true)
    public Page<ProductResult> getProducts(PageSize pageSize) {
        Slice<Product> products = productRepository.findAllBy(pageSize.toPageable(Sort.by(Sort.Direction.DESC, "createdAt")));
        return new Page<>(
                products.map(ProductResult::from)
                        .stream()
                        .toList(),
                products.hasNext()
        );
    }

    @Transactional(readOnly = true)
    public Page<ProductResult> getProductsByBrandId(Long brandId, PageSize pageSize) {
        if (!brandRepository.existsById(brandId)) {
            throw new CoreException(ErrorType.BRAND_NOT_FOUND);
        }
        Slice<Product> products = productRepository.findAllByBrandId(brandId,
                pageSize.toPageable(Sort.by(Sort.Direction.DESC, "createdAt")));
        return new Page<>(
                products.map(ProductResult::from)
                        .stream()
                        .toList(),
                products.hasNext()
        );
    }

    @Transactional(readOnly = true)
    public ProductResult getProduct(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new CoreException(ErrorType.PRODUCT_NOT_FOUND));
        return ProductResult.from(product);
    }

    @Transactional(readOnly = true)
    public ProductDetail getActiveProduct(Long userId, Long productId) {
        Product product = productRepository.findByIdAndDeletedAtIsNull(productId)
                .orElseThrow(() -> new CoreException(ErrorType.PRODUCT_NOT_FOUND));
        Brand brand = brandRepository.findByIdAndDeletedAtIsNull(product.getBrandId())
                .orElseThrow(() -> new CoreException(ErrorType.BRAND_NOT_FOUND));
        long likeCount = likeRepository.countByProductId(productId);
        boolean liked = isLiked(userId, productId);
        return ProductDetail.from(product, brand, likeCount, liked);
    }

    @Transactional
    public void updateProduct(ProductCommand.UpdateProductCommand command) {
        Product product = productRepository.findById(command.productId())
                .orElseThrow(() -> new CoreException(ErrorType.PRODUCT_NOT_FOUND));
        product.update(
                command.name(),
                command.thumbnailUrl(),
                command.price(),
                command.stock(),
                command.description()
        );
    }

    @Transactional
    public void deleteProduct(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new CoreException(ErrorType.PRODUCT_NOT_FOUND));
        if (product.isDeleted()) {
            return;
        }
        likeRepository.deleteAllByProductId(productId);
        product.delete();
    }

    private boolean isLiked(Long userId, Long productId) {
        if (userId == null) {
            return false;
        }
        return likeRepository.existsByUserIdAndProductId(userId, productId);
    }
}
