package com.loopers.domain.product;

import com.loopers.domain.BaseStringIdEntity;
import com.loopers.support.enums.DisplayStatus;
import com.loopers.support.enums.ProductSaleStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;

/**
 * 상품 JPA 엔티티.
 * <p>
 * 상품의 기본 정보(상품명, 가격, 카테고리 등)와 노출/판매 상태를 관리한다.
 * {@code revisionSeq}를 통해 변경 이력 순번을 추적하며,
 * {@link BaseStringIdEntity}를 상속하여 소프트 삭제({@code del_yn}, {@code deletedAt})를 지원한다.
 * 브랜드 삭제 시 소속 상품도 연쇄 소프트 삭제된다.
 * </p>
 *
 * @see BaseStringIdEntity
 * @see ProductRevisionModel
 */
@Entity
@Table(name = "products")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductModel extends BaseStringIdEntity {

    @Id
    @UuidGenerator
    @Column(name = "product_id", length = 36)
    private String productId;

    @Column(name = "brand_id", nullable = false, length = 36)
    private String brandId;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(length = 100)
    private String category;

    @Column(length = 50)
    private String color;

    @Column(name = "size", length = 50)
    private String size;

    @Column(name = "option", length = 255)
    private String option;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "attach_file")
    private String attachFile;

    @Enumerated(EnumType.STRING)
    @Column(name = "display_status", nullable = false, length = 20)
    private DisplayStatus displayStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "sale_status", nullable = false, length = 20)
    private ProductSaleStatus saleStatus;

    @Column(name = "revision_seq", nullable = false)
    private Long revisionSeq;

    private ProductModel(String productName, String brandId, BigDecimal price,
                         String description, String category, String color,
                         String size, String option, String imageUrl, String attachFile) {
        validateProductName(productName);
        validateBrandId(brandId);
        validatePrice(price);
        this.productName = productName;
        this.brandId = brandId;
        this.price = price;
        this.description = description;
        this.category = category;
        this.color = color;
        this.size = size;
        this.option = option;
        this.imageUrl = imageUrl;
        this.attachFile = attachFile;
        this.displayStatus = DisplayStatus.ACTIVE;
        this.saleStatus = ProductSaleStatus.ON_SALE;
        this.revisionSeq = 0L;
    }

    /**
     * 상품 엔티티를 생성한다. displayStatus=ACTIVE, saleStatus=ON_SALE, revisionSeq=0으로 초기화된다.
     * <p>정적 팩토리 메서드 패턴을 사용하여 생성자를 대신한다.</p>
     *
     * @param productName 상품명 (필수)
     * @param brandId     소속 브랜드 ID (필수, 생성 후 변경 불가)
     * @param price       가격 (0보다 커야 함)
     * @param description 상품 설명
     * @param category    카테고리
     * @param color       색상
     * @param size        사이즈
     * @param option      옵션
     * @param imageUrl    이미지 URL
     * @param attachFile  첨부 파일
     * @return 생성된 ProductModel 인스턴스
     * @throws CoreException productName/brandId null 또는 price <= 0인 경우 (BAD_REQUEST)
     */
    public static ProductModel create(String productName, String brandId, BigDecimal price,
                                       String description, String category, String color,
                                       String size, String option, String imageUrl, String attachFile) {
        return new ProductModel(productName, brandId, price, description, category, color,
                size, option, imageUrl, attachFile);
    }

    /**
     * 상품 정보를 수정하고 revisionSeq를 1 증가시킨다. brandId는 변경 불가.
     *
     * @param productName 새 상품명 (필수)
     * @param price       새 가격 (0보다 커야 함)
     * @param description 새 상품 설명
     * @param category    새 카테고리
     * @param color       새 색상
     * @param size        새 사이즈
     * @param option      새 옵션
     * @param imageUrl    새 이미지 URL
     * @param attachFile  새 첨부 파일
     * @throws CoreException productName blank 또는 price <= 0인 경우 (BAD_REQUEST)
     */
    public void updateInfo(String productName, BigDecimal price, String description,
                           String category, String color, String size,
                           String option, String imageUrl, String attachFile) {
        validateProductName(productName);
        validatePrice(price);
        this.productName = productName;
        this.price = price;
        this.description = description;
        this.category = category;
        this.color = color;
        this.size = size;
        this.option = option;
        this.imageUrl = imageUrl;
        this.attachFile = attachFile;
        this.revisionSeq++;
    }

    /**
     * 노출 상태를 변경한다 (ACTIVE ↔ HIDDEN).
     *
     * @param displayStatus 새 노출 상태
     */
    public void changeDisplayStatus(DisplayStatus displayStatus) {
        this.displayStatus = displayStatus;
    }

    /**
     * 판매 상태를 변경한다 (ON_SALE ↔ TEMP_SOLD_OUT ↔ STOPPED).
     *
     * @param saleStatus 새 판매 상태
     */
    public void changeSaleStatus(ProductSaleStatus saleStatus) {
        this.saleStatus = saleStatus;
    }

    /**
     * 주문 가능 여부를 판별한다.
     * displayStatus=ACTIVE, saleStatus.isOrderable()=true, 삭제되지 않은 경우에만 true.
     *
     * @return 주문 가능하면 true
     */
    public boolean isOrderable() {
        return displayStatus == DisplayStatus.ACTIVE
                && saleStatus.isOrderable()
                && !isDeleted();
    }

    /**
     * revisionSeq를 1 증가시키고 증가된 값을 반환한다. 상품 변경 이력(ProductRevision) 기록 시 사용.
     *
     * @return 증가된 revision 시퀀스 번호
     */
    public long incrementAndGetRevisionSeq() {
        return ++this.revisionSeq;
    }

    /**
     * JPA @PrePersist/@PreUpdate 시 호출되는 유효성 검증 훅.
     */
    @Override
    protected void guard() {
        validateProductName(this.productName);
        validateBrandId(this.brandId);
        validatePrice(this.price);
    }

    private static void validateProductName(String productName) {
        if (productName == null || productName.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 이름은 필수입니다.");
        }
    }

    private static void validateBrandId(String brandId) {
        if (brandId == null || brandId.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드 ID는 필수입니다.");
        }
    }

    private static void validatePrice(BigDecimal price) {
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "가격은 0보다 커야 합니다.");
        }
    }
}
