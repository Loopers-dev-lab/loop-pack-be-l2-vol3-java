package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.ZonedDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "product_views")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductViewLogModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "viewed_at", nullable = false, updatable = false)
    private ZonedDateTime viewedAt;

    // === 생성 === //

    private ProductViewLogModel(Long productId, Long userId, ZonedDateTime viewedAt) {
        this.productId = productId;
        this.userId = userId;
        this.viewedAt = viewedAt;
    }

    public static ProductViewLogModel create(Long productId, Long userId, ZonedDateTime viewedAt) {
        validateProductId(productId);
        return new ProductViewLogModel(productId, userId, viewedAt);
    }

    // === 검증 === //

    private static void validateProductId(Long productId) {
        if (productId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 ID는 필수값입니다.");
        }
    }
}
