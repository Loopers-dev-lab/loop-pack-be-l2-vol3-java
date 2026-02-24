package com.loopers.domain.product;

import com.loopers.domain.brand.BrandRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;

    public ProductService(ProductRepository productRepository, BrandRepository brandRepository) {
        this.productRepository = productRepository;
        this.brandRepository = brandRepository;
    }

    @Transactional
    public ProductModel register(Long brandId, String name, BigDecimal price, int stockQuantity) {
        brandRepository.findByIdAndNotDeleted(brandId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다: " + brandId));
        try {
            ProductModel product = ProductModel.create(brandId, name, price, stockQuantity);
            return productRepository.save(product);
        } catch (IllegalArgumentException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public Optional<ProductModel> findById(Long id) {
        return productRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<ProductModel> findByIdAndNotDeleted(Long id) {
        return productRepository.findByIdAndNotDeleted(id);
    }

    @Transactional
    public ProductModel update(Long id, String name, BigDecimal price, int stockQuantity) {
        ProductModel product = productRepository.findByIdAndNotDeleted(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다: " + id));
        try {
            product.updateName(name);
            product.updatePrice(price);
            product.updateStockQuantity(stockQuantity);
            return productRepository.save(product);
        } catch (IllegalArgumentException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, e.getMessage());
        }
    }

    /**
     * 상품 판매 가능 여부를 검증한다. (존재·미삭제·재고 충분)
     * optionId는 값 보존만 하며 옵션 테이블 검증은 하지 않는다.
     */
    @Transactional(readOnly = true)
    public void validateProductAvailability(Long productId, int quantity, Long optionId) {
        ProductModel product = productRepository.findByIdAndNotDeleted(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다: " + productId));
        if (product.isDeleted()) {
            throw new CoreException(ErrorType.NOT_FOUND, "삭제된 상품입니다: " + productId);
        }
        if (!product.hasStock(quantity)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재고가 부족합니다. 상품 ID: " + productId);
        }
    }

    /**
     * 주문 항목 목록에 대해 상품 유효성(존재·미삭제·재고)을 일괄 검증한다.
     * 재고 차감은 결제 완료 시점에 수행하므로 여기서는 검증만 한다.
     */
    @Transactional(readOnly = true)
    public void validateProducts(List<ProductValidationRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return;
        }
        for (ProductValidationRequest req : requests) {
            validateProductAvailability(req.productId(), req.quantity(), req.optionId());
        }
    }

    /**
     * 재고를 복구한다. (주문 취소 등)
     */
    @Transactional
    public void restoreStock(List<RestoreStockItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        for (RestoreStockItem item : items) {
            ProductModel product = productRepository.findById(item.productId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다: " + item.productId()));
            product.increaseStock(item.quantity());
            productRepository.save(product);
        }
    }
}
