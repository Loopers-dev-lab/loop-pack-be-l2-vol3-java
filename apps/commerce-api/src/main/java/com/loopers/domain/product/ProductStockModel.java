package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 상품 재고 JPA 엔티티.
 * <p>
 * 상품별 총 재고({@code onHand})와 예약 재고({@code reserved})를 관리한다.
 * 가용 재고는 {@code onHand - reserved}로 계산되며,
 * 실제 동시성 제어는 infrastructure 계층의 CAS(Compare-And-Set) UPDATE로 수행된다.
 * </p>
 *
 * @see ProductStockRepository#reserveStock(String, int)
 * @see ProductStockRepository#releaseStock(String, int)
 */
@Entity
@Table(name = "product_stocks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductStockModel {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "on_hand", nullable = false)
    private int onHand;

    @Column(nullable = false)
    private int reserved;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private ProductStockModel(Long productId, int onHand, int reserved) {
        if (onHand < 0) {
            throw new CoreException(ErrorType.INVALID_STOCK_UPDATE, "총 재고는 음수일 수 없습니다.");
        }
        this.productId = productId;
        this.onHand = onHand;
        this.reserved = reserved;
    }

    /**
     * 재고 엔티티를 생성한다. reserved=0으로 초기화.
     * <p>정적 팩토리 메서드 패턴을 사용하여 생성자를 대신한다.</p>
     *
     * @param productId 상품 ID (Product FK이자 PK)
     * @param onHand    총 재고 수량 (음수 불가)
     * @return 생성된 ProductStockModel 인스턴스
     * @throws CoreException onHand < 0인 경우 (INVALID_STOCK_UPDATE)
     */
    public static ProductStockModel create(Long productId, int onHand) {
        return new ProductStockModel(productId, onHand, 0);
    }

    /**
     * reserved를 직접 지정하여 재고 엔티티를 생성한다. 테스트용.
     * <p>정적 팩토리 메서드 패턴을 사용하여 생성자를 대신한다.</p>
     *
     * @param productId 상품 ID
     * @param onHand    총 재고 수량
     * @param reserved  예약 재고 수량
     * @return 생성된 ProductStockModel 인스턴스
     */
    public static ProductStockModel createWithReserved(Long productId, int onHand, int reserved) {
        return new ProductStockModel(productId, onHand, reserved);
    }

    /**
     * 가용 재고를 계산한다 (onHand - reserved).
     *
     * @return 주문 가능한 가용 재고 수량
     */
    public int getAvailableQty() {
        return onHand - reserved;
    }

    /**
     * 요청 수량만큼 예약(hold)이 가능한지 사전 검증한다.
     * <p>
     * 실제 동시성 보호는 Infrastructure의 CAS(Compare-And-Set) UPDATE에서 수행된다.
     * {@code UPDATE product_stocks SET reserved = reserved + :qty WHERE product_id = :productId AND (on_hand - reserved) >= :qty}
     * </p>
     *
     * @param qty 예약 요청 수량
     * @return 가용 재고가 충분하면 true
     */
    public boolean canHold(int qty) {
        return getAvailableQty() >= qty;
    }

    /**
     * 요청 수량이 가용 재고를 초과하면 예외를 던진다.
     *
     * @param qty 요청 수량
     * @throws CoreException 가용 재고보다 요청 수량이 많을 때 (CART_STOCK_EXCEEDED)
     */
    public void validateCanHold(int qty) {
        if (!canHold(qty)) {
            throw new CoreException(ErrorType.CART_STOCK_EXCEEDED);
        }
    }

    /**
     * 관리자가 총 재고(onHand)를 수정한다. 예약 재고보다 작게 설정하면 예외 발생.
     *
     * @param newOnHand 새 총 재고 수량
     * @throws CoreException newOnHand < reserved인 경우 (INVALID_STOCK_UPDATE)
     */
    public void updateOnHand(int newOnHand) {
        if (newOnHand < this.reserved) {
            throw new CoreException(ErrorType.INVALID_STOCK_UPDATE,
                    "총 재고는 예약 재고(" + this.reserved + ")보다 작을 수 없습니다.");
        }
        this.onHand = newOnHand;
    }

    @PrePersist
    private void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    private void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
