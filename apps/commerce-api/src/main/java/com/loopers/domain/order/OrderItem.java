package com.loopers.domain.order;

import com.loopers.domain.common.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "order_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "option_id", nullable = false)
    private Long optionId;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "option_name", nullable = false)
    private String optionName;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "price", nullable = false))
    private Money price;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    private OrderItem(Long optionId, String productName, String optionName, Money price, int quantity) {
        validate(optionId, productName, optionName, price, quantity);
        this.optionId = optionId;
        this.productName = productName;
        this.optionName = optionName;
        this.price = price;
        this.quantity = quantity;
    }

    public static OrderItem of(Long optionId, String productName, String optionName, Money price, int quantity) {
        return new OrderItem(optionId, productName, optionName, price, quantity);
    }

    void setOrder(Order order) {
        this.order = order;
    }

    public Money getTotalPrice() {
        return price.multiply(quantity);
    }

    private void validate(Long optionId, String productName, String optionName, Money price, int quantity) {
        if (optionId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "옵션 ID는 필수입니다.");
        }
        if (productName == null || productName.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품명은 필수입니다.");
        }
        if (optionName == null || optionName.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "옵션명은 필수입니다.");
        }
        if (price == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "가격은 필수입니다.");
        }
        if (quantity <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "수량은 1 이상이어야 합니다.");
        }
    }
}
