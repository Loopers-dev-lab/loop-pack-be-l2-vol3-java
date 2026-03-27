package com.loopers.application.payment;

import com.loopers.domain.payment.CardType;

public record PaymentCommand(
    Long orderId,
    CardType cardType,
    String cardNo
) {
    /**
     * 카드번호 마스킹: "1234-5678-9814-1451" → "1234-****-****-1451"
     */
    public String maskedCardNo() {
        if (cardNo == null || cardNo.length() < 4) return cardNo;
        String digitsOnly = cardNo.replaceAll("-", "");
        if (digitsOnly.length() < 8) return cardNo;

        String first4 = digitsOnly.substring(0, 4);
        String last4 = digitsOnly.substring(digitsOnly.length() - 4);
        return first4 + "-****-****-" + last4;
    }
}
