package com.loopers.domain.model.order;

public enum PaymentMethod {

    CARD("카드"),
    BANK_TRANSFER("계좌이체");

    private final String description;

    PaymentMethod(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
