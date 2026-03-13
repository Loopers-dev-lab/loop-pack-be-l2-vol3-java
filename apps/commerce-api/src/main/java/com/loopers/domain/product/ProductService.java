package com.loopers.domain.product;

import com.loopers.domain.brand.BrandRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;
    private final ProductStatsRepository productStatsRepository;

    public ProductService(ProductRepository productRepository, BrandRepository brandRepository,
            ProductStatsRepository productStatsRepository) {
        this.productRepository = productRepository;
        this.brandRepository = brandRepository;
        this.productStatsRepository = productStatsRepository;
    }

    @Transactional
    public ProductModel registerProduct(Long brandId, String name, BigDecimal price, int stockQuantity) {
        brandRepository.findByIdAndNotDeleted(brandId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다: " + brandId));
        try {
            ProductModel product = ProductModel.create(
                    brandId,
                    name,
                    Money.of(price),
                    StockQuantity.of(stockQuantity));
            ProductModel saved = productRepository.save(product);
            productStatsRepository.createIfAbsent(saved.getId());
            return saved;
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
    public ProductModel updateProduct(Long id, String name, BigDecimal price, int stockQuantity) {
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
    public void deleteProduct(Long id) {
        ProductModel product = productRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다: " + id));
        product.delete();
        productRepository.save(product);
    }

    /**
     * 상품 판매 가능 여부를 검증한다. (존재·미삭제·재고 충분)
     * optionId는 값 보존만 하며 옵션 테이블 검증은 하지 않는다.
     */
    @Transactional
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
     * 재고 차감은 하지 않으며, 검증·스냅샷 생성만 수행한다.
     * placeOrder 등 쓰기 트랜잭션에서 호출되면 readOnly는 미적용되나, 동일 트랜잭션 내 스냅샷·재고·주문의 일관성을 위해
     * 의도적으로 한 트랜잭션에서 실행한다.
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
     * 비관적 락으로 동시성 보장.
     * 데드락 방지를 위해 productId 오름차순으로 락을 획득한다.
     * 정합성 우선으로 단일 트랜잭션에서 주문 취소와 함께 수행한다.
     */
    @Transactional
    public void restoreStock(List<RestoreStockItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        List<RestoreStockItem> sorted = items.stream()
                .sorted(Comparator.comparing(RestoreStockItem::productId))
                .toList();
        for (RestoreStockItem item : sorted) {
            ProductModel product = productRepository.findByIdForUpdate(item.productId())
                    .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다: " + item.productId()));
            product.increaseStock(item.quantity());
            productRepository.save(product);
        }
    }

    /**
     * 주문 항목별로 비관적 락을 먼저 걸고, 검증·스냅샷·재고 차감을 한 번에 수행한다.
     * 트랜잭션 시작 직후 락을 선점하여 영속성 컨텍스트 캐시로 인한 락 미적용을 방지한다. (05-transaction-query §2.1,
     * §3.2)
     * 상품 ID 오름차순으로 락을 잡아 데드락을 방지한다.
     *
     * @param requests 주문 항목(상품 ID, 수량, 옵션 ID)
     * @return 요청 순서와 동일한 스냅샷 목록 (주문 생성용)
     */
    @Transactional
    public List<ProductSnapshot> validateDecreaseStockAndGetSnapshots(List<ProductValidationRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목이 없습니다.");
        }
        Map<Long, Integer> quantityByProductId = new HashMap<>();
        for (ProductValidationRequest req : requests) {
            quantityByProductId.merge(req.productId(), req.quantity().value(), Integer::sum);
        }
        List<Long> productIds = quantityByProductId.keySet().stream().sorted().toList();
        Map<Long, ProductSnapshot> productIdToSnapshot = new HashMap<>();

        for (Long productId : productIds) {
            int qty = quantityByProductId.get(productId);
            ProductModel product = productRepository.findByIdForUpdate(productId)
                    .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다: " + productId));
            if (!product.hasStock(Quantity.of(qty))) {
                throw new CoreException(ErrorType.BAD_REQUEST, "재고가 부족합니다. 상품 ID: " + productId);
            }
            productIdToSnapshot.put(productId, product.snapshotForOrder());
            try {
                product.decreaseStock(Quantity.of(qty));
            } catch (IllegalArgumentException e) {
                throw new CoreException(ErrorType.BAD_REQUEST, "재고가 부족합니다. 상품 ID: " + productId);
            }
            productRepository.saveAndFlush(product);
        }

        return requests.stream()
                .map(req -> productIdToSnapshot.get(req.productId()))
                .toList();
    }

    /**
     * 주문 항목별 재고를 비관적 락으로 차감한다.
     * 상품 ID 오름차순으로 락을 잡아 데드락을 방지한다. (05-transaction-query §2.1, §3.2)
     * 동일 상품이 여러 항목에 있으면 수량을 합산해 한 번에 차감한다.
     */
    @Transactional
    public void decreaseStockWithLock(List<ProductValidationRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return;
        }
        Map<Long, Integer> quantityByProductId = new HashMap<>();
        for (ProductValidationRequest req : requests) {
            quantityByProductId.merge(req.productId(), req.quantity().value(), Integer::sum);
        }
        List<Long> productIds = quantityByProductId.keySet().stream().sorted().toList();
        for (Long productId : productIds) {
            int qty = quantityByProductId.get(productId);
            ProductModel product = productRepository.findByIdForUpdate(productId)
                    .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다: " + productId));
            try {
                product.decreaseStock(Quantity.of(qty));
            } catch (IllegalArgumentException e) {
                throw new CoreException(ErrorType.BAD_REQUEST, "재고가 부족합니다. 상품 ID: " + productId);
            }
            productRepository.saveAndFlush(product);
        }
    }

    /**
     * 해당 브랜드에 속한 모든 미삭제 상품을 soft delete한다.
     * 브랜드 삭제 시 연쇄 삭제에 사용한다 (01 §3.5).
     * 단일 벌크 UPDATE로 트랜잭션 내 루프·save 수를 줄인다.
     */
    @Transactional
    public void softDeleteByBrandId(Long brandId) {
        productRepository.softDeleteByBrandIdBulk(brandId);
    }
}
