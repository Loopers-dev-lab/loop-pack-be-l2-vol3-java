package com.loopers.domain.like;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "product_like", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"member_id", "product_id"})
})
public class Like extends BaseEntity {

    private Long memberId;
    private Long productId;

    protected Like() {}

    public Like(Long memberId, Long productId) {
        validateMemberId(memberId);
        validateProductId(productId);
        this.memberId = memberId;
        this.productId = productId;
    }

    private void validateMemberId(Long memberId) {
        if (memberId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "회원 ID는 필수입니다.");
        }
    }

    private void validateProductId(Long productId) {
        if (productId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 ID는 필수입니다.");
        }
    }

    public Long getMemberId() {
        return memberId;
    }

    public Long getProductId() {
        return productId;
    }
}
