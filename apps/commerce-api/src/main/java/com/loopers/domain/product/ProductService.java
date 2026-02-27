package com.loopers.domain.product;

import com.loopers.support.enums.ProductRevisionAction;
import com.loopers.support.enums.ProductSaleStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

/**
 * 상품 도메인 서비스.
 * <p>
 * 상품의 CRUD, 노출/판매 상태 변경, 소프트 삭제 및 변경 이력(ProductRevision) 관리를 담당한다.
 * 상품 생성/수정/삭제/판매 상태 변경 시 자동으로 변경 이력을 기록한다.
 * 외부 도메인(Brand, Stock)과의 조합은 {@link com.loopers.application.product.ProductFacade}에서 수행한다.
 * </p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductRevisionRepository revisionRepository;

    /**
     * 상품을 생성한다.
     * <p>
     * 상품 엔티티를 저장하고 CREATE 유형의 변경 이력을 기록한다.
     * 브랜드 검증 및 재고 생성은 Facade에서 수행한다.
     * </p>
     *
     * @param productName 상품명
     * @param brandId     소속 브랜드 ID
     * @param price       가격
     * @param description 상품 설명
     * @return 생성된 상품 엔티티
     */
    @Transactional
    public ProductModel createProduct(String productName, String brandId, BigDecimal price,
                                     String description) {
        ProductModel product = ProductModel.create(productName, brandId, price,
                description, null, null, null, null, null, null);
        product = productRepository.save(product);

        ProductRevisionModel revision = ProductRevisionModel.create(
                product.getProductId(), 0L, ProductRevisionAction.CREATE,
                null, null, null, toSnapshot(product));
        revisionRepository.save(revision);

        return product;
    }

    /**
     * 상품 ID로 상품을 조회한다.
     *
     * @param productId 상품 ID
     * @return 상품 엔티티
     * @throws CoreException 상품이 존재하지 않을 때 (PRODUCT_NOT_FOUND)
     */
    public ProductModel findById(String productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new CoreException(ErrorType.PRODUCT_NOT_FOUND));
    }

    /**
     * 주문 가능한 상품을 조회한다.
     * <p>
     * 상품 조회 후 주문 가능 여부(displayStatus=ACTIVE, saleStatus가 주문 가능, 미삭제)를 검증한다.
     * </p>
     *
     * @param productId 상품 ID
     * @return 주문 가능한 상품 엔티티
     * @throws CoreException 상품이 존재하지 않거나 주문 불가 시
     */
    /**
     * 상품 ID 목록으로 상품을 일괄 조회한다.
     *
     * @param productIds 상품 ID 목록
     * @return 상품 엔티티 목록
     */
    public List<ProductModel> findAllByIds(Collection<String> productIds) {
        return productRepository.findAllByProductIds(productIds);
    }

    public ProductModel findOrderableById(String productId) {
        ProductModel product = findById(productId);
        if (!product.isOrderable()) {
            throw new CoreException(ErrorType.PRODUCT_NOT_ORDERABLE);
        }
        return product;
    }

    /**
     * 관리자용 상품 목록을 조회한다.
     *
     * @param includeDeleted true이면 삭제된 상품 포함, false이면 미삭제 상품만
     * @return 상품 목록
     */
    public List<ProductModel> findAllForAdmin(boolean includeDeleted) {
        if (includeDeleted) {
            return productRepository.findAll();
        }
        return productRepository.findAllByDelYn("N");
    }

    /**
     * 고객용 상품 목록을 조회한다 (del_yn='N' AND display_status='ACTIVE').
     *
     * @param keyword 검색 키워드 (null이면 전체)
     * @param brandId 브랜드 ID 필터 (null이면 전체)
     * @return 고객 노출 조건을 만족하는 상품 목록
     */
    public List<ProductModel> findAllForCustomer(String keyword, String brandId) {
        return productRepository.findAllForCustomer(keyword, brandId);
    }

    /**
     * 고객용 상품 목록을 페이징하여 조회한다.
     *
     * @param keyword  검색 키워드 (null이면 전체)
     * @param brandId  브랜드 ID 필터 (null이면 전체)
     * @param pageable 페이징/정렬 정보
     * @return 페이징된 상품 목록
     */
    public Page<ProductModel> findAllForCustomer(String keyword, String brandId, Pageable pageable) {
        return productRepository.findAllForCustomer(keyword, brandId, pageable);
    }

    /**
     * 상품 정보를 수정한다.
     * <p>
     * 수정 전/후 상품 상태를 스냅샷으로 저장하고 UPDATE 유형의 변경 이력을 기록한다.
     * 재고 조회 및 Info 조합은 Facade에서 수행한다.
     * </p>
     *
     * @param productId   상품 ID
     * @param productName 새 상품명
     * @param price       새 가격
     * @param description 새 상품 설명
     * @param imageUrl    새 이미지 URL
     * @return 수정된 상품 엔티티
     * @throws CoreException 상품이 존재하지 않을 때 (PRODUCT_NOT_FOUND)
     */
    @Transactional
    public ProductModel updateProduct(String productId, String productName, BigDecimal price,
                                     String description, String imageUrl) {
        ProductModel product = findById(productId);

        String beforeSnapshot = toSnapshot(product);
        product.updateInfo(productName, price, description,
                null, null, null, null, imageUrl, null);
        String afterSnapshot = toSnapshot(product);

        ProductRevisionModel revision = ProductRevisionModel.create(
                product.getProductId(), product.getRevisionSeq(),
                ProductRevisionAction.UPDATE, null, null, beforeSnapshot, afterSnapshot);
        revisionRepository.save(revision);

        return product;
    }

    /**
     * 상품을 소프트 삭제한다. 이미 삭제된 상품이면 무시한다 (멱등).
     * <p>
     * 삭제 전 상품 상태를 스냅샷으로 저장하고 DELETE 유형의 변경 이력을 기록한다.
     * </p>
     *
     * @param productId 상품 ID
     * @throws CoreException 상품이 존재하지 않을 때 (PRODUCT_NOT_FOUND)
     */
    @Transactional
    public void deleteProduct(String productId) {
        ProductModel product = findById(productId);
        if (product.isDeleted()) {
            return;
        }
        String beforeSnapshot = toSnapshot(product);
        product.softDelete();

        long revSeq = product.incrementAndGetRevisionSeq();
        ProductRevisionModel revision = ProductRevisionModel.create(
                product.getProductId(), revSeq, ProductRevisionAction.DELETE,
                null, null, beforeSnapshot, null);
        revisionRepository.save(revision);
    }

    /**
     * 특정 브랜드에 소속된 상품을 연쇄 소프트 삭제한다.
     * <p>
     * 브랜드 삭제 시 호출되며, 미삭제 상품만 대상으로 소프트 삭제 후 변경 이력을 기록한다.
     * </p>
     *
     * @param brandId 브랜드 ID
     */
    @Transactional
    public void softDeleteByBrandId(String brandId) {
        List<ProductModel> products = productRepository.findAllByBrandId(brandId);
        for (ProductModel product : products) {
            if (!product.isDeleted()) {
                String beforeSnapshot = toSnapshot(product);
                product.softDelete();

                long revSeq = product.incrementAndGetRevisionSeq();
                ProductRevisionModel revision = ProductRevisionModel.create(
                        product.getProductId(), revSeq, ProductRevisionAction.DELETE,
                        null, null, beforeSnapshot, null);
                revisionRepository.save(revision);
            }
        }
    }

    /**
     * 상품의 판매 상태를 변경한다.
     * <p>
     * 변경 전/후 상품 상태를 스냅샷으로 저장하고 SALE_STATUS_CHANGE 유형의 변경 이력을 기록한다.
     * </p>
     *
     * @param productId 상품 ID
     * @param newStatus 새 판매 상태
     * @throws CoreException 상품이 존재하지 않을 때 (PRODUCT_NOT_FOUND)
     */
    @Transactional
    public void changeSaleStatus(String productId, ProductSaleStatus newStatus) {
        ProductModel product = findById(productId);
        String beforeSnapshot = toSnapshot(product);
        product.changeSaleStatus(newStatus);
        String afterSnapshot = toSnapshot(product);

        long revSeq = product.incrementAndGetRevisionSeq();
        ProductRevisionModel revision = ProductRevisionModel.create(
                product.getProductId(), revSeq, ProductRevisionAction.SALE_STATUS_CHANGE,
                null, null, beforeSnapshot, afterSnapshot);
        revisionRepository.save(revision);
    }

    /**
     * 특정 상품의 변경 이력 목록을 조회한다.
     *
     * @param productId 상품 ID
     * @return 변경 이력 목록
     */
    public List<ProductRevisionModel> findRevisionsByProductId(String productId) {
        return revisionRepository.findAllByProductId(productId);
    }

    /**
     * 특정 상품의 특정 순번 변경 이력을 조회한다.
     *
     * @param productId   상품 ID
     * @param revisionSeq 변경 순번
     * @return 변경 이력 엔티티
     * @throws CoreException 변경 이력이 존재하지 않을 때 (PRODUCT_NOT_FOUND)
     */
    public ProductRevisionModel findRevisionById(String productId, Long revisionSeq) {
        return revisionRepository.findById(new ProductRevisionId(productId, revisionSeq))
                .orElseThrow(() -> new CoreException(ErrorType.PRODUCT_NOT_FOUND));
    }

    private String toSnapshot(ProductModel product) {
        return "{\"productName\":\"" + product.getProductName()
                + "\",\"price\":" + product.getPrice()
                + ",\"saleStatus\":\"" + product.getSaleStatus()
                + "\",\"displayStatus\":\"" + product.getDisplayStatus() + "\"}";
    }
}
