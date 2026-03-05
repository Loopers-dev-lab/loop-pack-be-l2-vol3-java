package com.loopers.domain.order;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.product.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Order extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    // 적용된 발급 쿠폰 ID. 쿠폰 미적용 시 null (BR-O09)
    @Column(name = "user_coupon_id")
    private Long userCouponId;

    // 쿠폰 적용 전 총 금액 스냅샷 (BR-O13)
    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "original_amount", nullable = false))
    private Money originalAmount;

    // 할인 금액 스냅샷. 쿠폰 미적용 시 0 (BR-O13)
    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "discount_amount", nullable = false))
    private Money discountAmount;

    // 최종 결제 금액 스냅샷 (= originalAmount - discountAmount, BR-O13)
    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "final_amount", nullable = false))
    private Money finalAmount;

    // Order가 Aggregate Root, OrderItem은 Order를 통해서만 접근 (BR-O01: 최소 1개 항목)
    // EAGER: OrderInfo 응답에 항상 OrderItem을 포함하므로 항상 함께 로딩
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = jakarta.persistence.FetchType.EAGER)
    @JoinColumn(name = "order_id", nullable = false)
    private List<OrderItem> orderItems = new ArrayList<>();

    /**
     * @param discountAmount 쿠폰 할인 금액. 쿠폰 미적용 시 new Money(0)
     */
    public Order(Long userId, List<OrderItem> orderItems, Long userCouponId, Money originalAmount, Money discountAmount) {
        validateUserId(userId);
        validateOrderItems(orderItems);

        this.userId = userId;
        this.orderItems = new ArrayList<>(orderItems);
        this.userCouponId = userCouponId;
        this.originalAmount = originalAmount;
        this.discountAmount = discountAmount;
        // finalAmount는 도메인 규칙으로 계산 (BR-O13)
        this.finalAmount = new Money(originalAmount.getAmount() - discountAmount.getAmount());
    }

    // BR-O06: 주문 소유자 확인
    public boolean isOwnedBy(Long userId) {
        return this.userId.equals(userId);
    }

    @Override
    protected void guard() {
        // userId만 컬럼 레벨에서 검증. orderItems(@OneToMany)는 JPA merge 시점에
        // 컬렉션 초기화 전에 guard()가 호출될 수 있으므로 생성자에서만 검증한다.
        validateUserId(this.userId);
    }

    private void validateUserId(Long userId) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "유저 ID는 필수입니다.");
        }
    }

    private void validateOrderItems(List<OrderItem> orderItems) {
        if (orderItems == null || orderItems.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목은 최소 1개 이상이어야 합니다.");
        }
    }
}
