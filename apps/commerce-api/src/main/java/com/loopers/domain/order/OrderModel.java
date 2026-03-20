package com.loopers.domain.order;

import com.loopers.domain.BaseStringIdEntity;
import com.loopers.support.enums.OrderStatus;
import com.loopers.support.enums.OrderType;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 주문 JPA 엔티티.
 * <p>
 * 주문 생성 시 {@code PENDING_PAYMENT} 상태로 초기화되며, 15분 이내 결제하지 않으면 만료된다.
 * 상태 머신: {@code PENDING_PAYMENT} -> {@code CANCELLED} (사용자 취소) / {@code EXPIRED} (배치 만료).
 * 모든 상태 전이는 CAS(Compare-And-Set) 방식으로 경쟁 조건을 방지한다.
 * </p>
 *
 * @see BaseStringIdEntity
 */
@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderModel extends BaseStringIdEntity {

    private static final int EXPIRES_MINUTES = 15;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false, length = 20)
    private OrderType orderType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    private OrderModel(Long userId, OrderType orderType, BigDecimal totalAmount) {
        validateUserId(userId);
        this.userId = userId;
        this.orderType = orderType;
        this.status = OrderStatus.PENDING_PAYMENT;
        this.totalAmount = totalAmount;
        this.expiresAt = LocalDateTime.now().plusMinutes(EXPIRES_MINUTES);
    }

    /**
     * 주문 엔티티를 생성한다. status=PENDING_PAYMENT, expiresAt=현재+15분으로 초기화된다.
     * <p>정적 팩토리 메서드 패턴을 사용하여 생성자를 대신한다.</p>
     *
     * @param userId      주문자 ID (필수)
     * @param orderType   주문 유형 (DIRECT: 바로주문 / CART: 장바구니주문)
     * @param totalAmount 주문 총액
     * @return 생성된 OrderModel 인스턴스
     * @throws CoreException userId가 null/blank인 경우 (BAD_REQUEST)
     */
    public static OrderModel create(Long userId, OrderType orderType, BigDecimal totalAmount) {
        return new OrderModel(userId, orderType, totalAmount);
    }

    /**
     * 사용자가 주문을 취소한다 (PENDING_PAYMENT -> CANCELLED).
     * 이미 CANCELLED 상태면 멱등 처리(무시). EXPIRED 상태면 예외.
     *
     * @throws CoreException EXPIRED 상태에서 취소 시도 시 (ORDER_NOT_CANCELLABLE)
     */
    public void cancel() {
        if (this.status == OrderStatus.CANCELLED) {
            return; // 멱등
        }
        if (this.status == OrderStatus.EXPIRED) {
            throw new CoreException(ErrorType.ORDER_NOT_CANCELLABLE, "만료된 주문은 취소할 수 없습니다.");
        }
        this.status = OrderStatus.CANCELLED;
    }

    /**
     * 배치 스케줄러가 주문을 만료 처리한다 (PENDING_PAYMENT -> EXPIRED).
     * 이미 EXPIRED 상태면 멱등 처리(무시). CANCELLED 상태면 예외.
     *
     * @throws CoreException CANCELLED 상태에서 만료 시도 시 (ORDER_NOT_CANCELLABLE)
     */
    public void expire() {
        if (this.status == OrderStatus.EXPIRED) {
            return; // 멱등
        }
        if (this.status == OrderStatus.CANCELLED) {
            throw new CoreException(ErrorType.ORDER_NOT_CANCELLABLE, "취소된 주문은 만료 처리할 수 없습니다.");
        }
        this.status = OrderStatus.EXPIRED;
    }

    /**
     * 결제가 완료되어 주문 상태를 PAID로 전이한다 (PENDING_PAYMENT → PAID).
     *
     * @throws CoreException PENDING_PAYMENT가 아닌 상태에서 호출 시 (ORDER_NOT_FOUND)
     */
    public void markAsPaid() {
        if (this.status != OrderStatus.PENDING_PAYMENT) {
            throw new CoreException(ErrorType.ORDER_NOT_FOUND, "결제 가능한 상태가 아닙니다");
        }
        this.status = OrderStatus.PAID;
        this.paidAt = LocalDateTime.now();
    }

    /**
     * 사용자 취소가 가능한지 판별한다. PENDING_PAYMENT 상태이고 시간 만료되지 않은 경우에만 true.
     *
     * @return 취소 가능하면 true
     */
    public boolean canCancel() {
        return this.status == OrderStatus.PENDING_PAYMENT && !isTimeExpired();
    }

    /**
     * 시간 기준으로 만료 여부를 판별한다. expiresAt이 현재 시각 이전이면 만료.
     *
     * @return 시간 초과로 만료되었으면 true
     */
    public boolean isTimeExpired() {
        return LocalDateTime.now().isAfter(this.expiresAt);
    }

    /**
     * JPA @PrePersist/@PreUpdate 시 호출되는 유효성 검증 훅.
     */
    @Override
    protected void guard() {
        validateUserId(this.userId);
    }

    private static void validateUserId(Long userId) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용자 ID는 필수입니다.");
        }
    }
}
