package com.loopers.domain.model.order;

public enum OrderStatus {

    PAYMENT_COMPLETED("결제완료"),
    PREPARING("상품준비중"),
    SHIPPING("배송중"),
    DELIVERED("배송완료"),
    CANCELLED("주문취소");

    private final String description;

    OrderStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public boolean isCancellable() {
        return this == PAYMENT_COMPLETED || this == PREPARING;
    }

    public boolean isAddressChangeable() {
        return this == PAYMENT_COMPLETED || this == PREPARING;
    }
}
