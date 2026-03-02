package com.loopers.domain.product;

import com.loopers.domain.brand.BrandRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

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
            ProductModel product = ProductModel.create(
                    brandId,
                    name,
                    Money.of(price),
                    StockQuantity.of(stockQuantity));
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

    /**
     * 미삭제 상품 목록을 정렬·페이징하여 조회한다.
     * 좋아요 수는 채우지 않으며, Application 레이어에서 조합한다.
     */
    @Transactional(readOnly = true)
    public Page<ProductModel> findNotDeletedForList(ProductSortOrder sortOrder, Long brandId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return productRepository.findNotDeleted(sortOrder, brandId, pageable);
    }

    @Transactional
    public ProductModel update(Long id, String name, BigDecimal price, int stockQuantity) {
        ProductModel product = productRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다: " + id));
        try {
            product.updateName(name);
            product.updatePrice(Money.of(price));
            product.updateStockQuantity(StockQuantity.of(stockQuantity));
            return productRepository.save(product);
        } catch (IllegalArgumentException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, e.getMessage());
        }
    }

    /**
     * 상품을 soft delete한다. (어드민 삭제)
     */
    @Transactional
    public void delete(Long id) {
        ProductModel product = productRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다: " + id));
        product.delete();
        productRepository.save(product);
    }

    /**
     * 상품 판매 가능 여부를 검증한다. (존재·미삭제·재고 충분)
     * optionId는 값 보존만 하며 옵션 테이블 검증은 하지 않는다.
     */
    @Transactional(readOnly = true)
    public void validateProductAvailability(Long productId, Quantity quantity, Long optionId) {
        getValidatedProduct(productId, quantity);
    }

    private ProductModel getValidatedProduct(Long productId, Quantity quantity) {
        ProductModel product = productRepository.findByIdAndNotDeleted(productId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다: " + productId));
        if (!product.hasStock(quantity)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재고가 부족합니다. 상품 ID: " + productId);
        }
        return product;
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
     * 주문 항목 목록을 검증하고, 유효 시 각 상품의 스냅샷(이름·가격) 목록을 반환한다.
     * 하나라도 미존재/삭제/재고 부족이면 예외를 던진다.
     */
    @Transactional(readOnly = true)
    public List<ProductSnapshot> validateAndGetSnapshots(List<ProductValidationRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목이 없습니다.");
        }
        List<ProductSnapshot> snapshots = new ArrayList<>();
        for (ProductValidationRequest req : requests) {
            ProductModel product = getValidatedProduct(req.productId(), req.quantity());
            snapshots.add(product.snapshotForOrder());
        }
        return snapshots;
    }

    /**
     * 재고를 복구한다. (주문 취소 등)
     * 비관적 락으로 동시성 보장 (04-erd §0).
     */
    @Transactional
    public void restoreStock(List<RestoreStockItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        for (RestoreStockItem item : items) {
            ProductModel product = productRepository.findByIdForUpdate(item.productId())
                    .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다: " + item.productId()));
            product.increaseStock(item.quantity());
            productRepository.save(product);
        }
    }

    /**
     * 해당 브랜드에 속한 모든 미삭제 상품을 soft delete한다.
     * 브랜드 삭제 시 연쇄 삭제에 사용한다 (01 §3.5).
     */
    @Transactional
    public void softDeleteByBrandId(Long brandId) {
        List<ProductModel> products = productRepository.findByBrandIdAndNotDeleted(brandId);
        for (ProductModel product : products) {
            product.delete();
            productRepository.save(product);
        }
    }
}
