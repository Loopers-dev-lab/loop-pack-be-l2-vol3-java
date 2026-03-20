package com.loopers.domain.product;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "product_images", indexes = {
        @Index(name = "idx_product_images_product_id", columnList = "product_id")})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductImageModel extends BaseEntity {

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "image_url", nullable = false)
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "image_type", nullable = false)
    private ImageType imageType;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    // === 생성 === //

    private ProductImageModel(Long productId, String imageUrl, ImageType imageType, int sortOrder) {
        this.productId = productId;
        this.imageUrl = imageUrl;
        this.imageType = imageType;
        this.sortOrder = sortOrder;
    }

    public static ProductImageModel create(Long productId, String imageUrl, ImageType imageType, int sortOrder) {
        validateProductId(productId);
        validateImageUrl(imageUrl);
        validateImageType(imageType);
        validateSortOrder(sortOrder);
        return new ProductImageModel(productId, imageUrl, imageType, sortOrder);
    }

    // === 검증 === //

    private static void validateProductId(Long productId) {
        if (productId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 ID는 필수값입니다.");
        }
    }

    private static void validateImageUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이미지 URL은 필수값입니다.");
        }
    }

    private static void validateImageType(ImageType imageType) {
        if (imageType == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이미지 타입은 필수값입니다.");
        }
    }

    private static void validateSortOrder(int sortOrder) {
        if (sortOrder < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "정렬 순서는 0 이상이어야 합니다.");
        }
    }
}
