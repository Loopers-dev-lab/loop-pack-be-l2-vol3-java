package com.loopers.domain.order;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.math.BigDecimal;

import static lombok.AccessLevel.PROTECTED;

/**
 * 주문 도메인 엔티티.
 * 물리 삭제 금지, 취소는 status를 CANCELLED로 변경.
 */
@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = PROTECTED)
public class OrderModel extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    @Column(name = "ordered_at", nullable = false)
    private ZonedDateTime orderedAt;

    @Column(name = "issued_coupon_id")
    private Long issuedCouponId;

    @Column(name = "amount_before_discount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amountBeforeDiscount;

    @Column(name = "discount_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "final_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal finalAmount;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<OrderItemModel> orderItems = new ArrayList<>();

    private OrderModel(Long userId, OrderStatus status, ZonedDateTime orderedAt,
                       Long issuedCouponId, BigDecimal amountBeforeDiscount,
                       BigDecimal discountAmount, BigDecimal finalAmount) {
        this.userId = userId;
        this.status = status;
        this.orderedAt = orderedAt;
        this.issuedCouponId = issuedCouponId;
        this.amountBeforeDiscount = amountBeforeDiscount;
        this.discountAmount = discountAmount;
        this.finalAmount = finalAmount;
    }

    /**
     * 주문을 생성한다. 초기 상태는 ORDERED. 쿠폰 미적용 시 issuedCouponId=null, discountAmount=0, finalAmount=amountBeforeDiscount.
     */
    public static OrderModel create(Long userId, BigDecimal amountBeforeDiscount,
                                   BigDecimal discountAmount, BigDecimal finalAmount, Long issuedCouponId) {
        if (userId == null) {
            throw new IllegalArgumentException("사용자 ID는 null일 수 없습니다.");
        }
        if (amountBeforeDiscount == null || finalAmount == null || discountAmount == null) {
            throw new IllegalArgumentException("주문 금액은 null일 수 없습니다.");
        }
        return new OrderModel(userId, OrderStatus.ORDERED, ZonedDateTime.now(),
                issuedCouponId, amountBeforeDiscount, discountAmount, finalAmount);
    }

    /**
     * 쿠폰 미적용 주문 생성. (기존 호환용·테스트용)
     */
    public static OrderModel create(Long userId) {
        return create(userId, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null);
    }

    /**
     * 지정한 상태로 주문을 생성한다. 영속 계층에서 재구성하거나 테스트에서 사용.
     */
    public static OrderModel withStatus(Long userId, OrderStatus status, ZonedDateTime orderedAt) {
        return withStatus(userId, status, orderedAt, null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    /**
     * 지정한 상태·금액으로 주문을 생성한다. 테스트·영속 재구성용.
     */
    public static OrderModel withStatus(Long userId, OrderStatus status, ZonedDateTime orderedAt,
                                       Long issuedCouponId, BigDecimal amountBeforeDiscount,
                                       BigDecimal discountAmount, BigDecimal finalAmount) {
        if (userId == null) {
            throw new IllegalArgumentException("사용자 ID는 null일 수 없습니다.");
        }
        if (status == null) {
            throw new IllegalArgumentException("상태는 null일 수 없습니다.");
        }
        if (orderedAt == null) {
            throw new IllegalArgumentException("주문 시각은 null일 수 없습니다.");
        }
        if (amountBeforeDiscount == null) {
            amountBeforeDiscount = BigDecimal.ZERO;
        }
        if (discountAmount == null) {
            discountAmount = BigDecimal.ZERO;
        }
        if (finalAmount == null) {
            finalAmount = BigDecimal.ZERO;
        }
        return new OrderModel(userId, status, orderedAt, issuedCouponId, amountBeforeDiscount, discountAmount, finalAmount);
    }

    /**
     * 주문 항목을 추가한다.
     */
    public void addItem(OrderItemModel item) {
        if (item == null) {
            throw new IllegalArgumentException("주문 항목은 null일 수 없습니다.");
        }
        item.setOrder(this);
        orderItems.add(item);
    }

    /**
     * 저장 전 검증: 최소 1개 항목이 있어야 한다.
     */
    public void validateHasItems() {
        if (orderItems == null || orderItems.isEmpty()) {
            throw new IllegalArgumentException("주문에는 최소 1개 이상의 항목이 필요합니다.");
        }
    }

    /**
     * 취소 가능 여부. ORDERED, PAID만 취소 가능.
     */
    public boolean canCancel() {
        return status == OrderStatus.ORDERED || status == OrderStatus.PAID;
    }

    /**
     * 주문을 취소한다. 취소 가능 상태일 때만 호출.
     */
    public void cancel() {
        if (!canCancel()) {
            throw new IllegalStateException("취소할 수 없는 상태입니다: " + status);
        }
        this.status = OrderStatus.CANCELLED;
    }

    /**
     * 주문 항목 목록을 읽기 전용으로 반환한다.
     */
    public List<OrderItemModel> getOrderItems() {
        return Collections.unmodifiableList(orderItems);
    }
}
