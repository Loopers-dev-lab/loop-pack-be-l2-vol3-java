package com.loopers.domain.order;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "orders")
public class Order extends BaseEntity {

    private static final String STATUS_ORDERED = "ORDERED";

    private Long memberId;
    private String status;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderLineEntity> orderLines = new ArrayList<>();

    protected Order() {}

    private Order(Long memberId, List<OrderLine> orderLines) {
        validateMemberId(memberId);
        validateOrderLines(orderLines);
        this.memberId = memberId;
        this.status = STATUS_ORDERED;
        for (OrderLine line : orderLines) {
            this.orderLines.add(new OrderLineEntity(this, line.productId(), line.quantity(), line.unitPrice()));
        }
    }

    public static Order create(Long memberId, List<OrderLine> orderLines) {
        return new Order(memberId, orderLines);
    }

    private void validateMemberId(Long memberId) {
        if (memberId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "회원 ID는 필수입니다.");
        }
    }

    private void validateOrderLines(List<OrderLine> orderLines) {
        if (orderLines == null || orderLines.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목이 비어있을 수 없습니다.");
        }
    }

    public Long getMemberId() {
        return memberId;
    }

    public String getStatus() {
        return status;
    }

    public List<OrderLineEntity> getOrderLines() {
        return Collections.unmodifiableList(orderLines);
    }

    public long getTotalAmount() {
        return orderLines.stream()
            .mapToLong(OrderLineEntity::getTotalPrice)
            .sum();
    }

    @jakarta.persistence.Entity
    @jakarta.persistence.Table(name = "order_line")
    public static class OrderLineEntity {
        @jakarta.persistence.Id
        @jakarta.persistence.GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
        private Long id;

        @jakarta.persistence.ManyToOne(fetch = jakarta.persistence.FetchType.LAZY)
        @jakarta.persistence.JoinColumn(name = "order_id", nullable = false)
        private Order order;

        @jakarta.persistence.Column(name = "product_id", nullable = false)
        private Long productId;

        @jakarta.persistence.Column(nullable = false)
        private int quantity;

        @jakarta.persistence.Column(name = "unit_price", nullable = false)
        private long unitPrice;

        protected OrderLineEntity() {}

        OrderLineEntity(Order order, Long productId, int quantity, long unitPrice) {
            this.order = order;
            this.productId = productId;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
        }

        public long getTotalPrice() {
            return (long) quantity * unitPrice;
        }

        public Long getProductId() {
            return productId;
        }

        public int getQuantity() {
            return quantity;
        }

        public long getUnitPrice() {
            return unitPrice;
        }
    }
}
