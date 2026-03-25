package com.loopers.application.payment;

/**
 * 결제 요청 커맨드.
 * Controller에서 요청 DTO를 변환하여 Facade에 전달.
 *
 * @param orderId 결제할 주문 ID
 * @param cardType 카드 타입 (예: "VISA", "MASTER")
 * @param cardNo 카드 번호
 */
public record PaymentCommand(
        Long orderId,
        String cardType,
        String cardNo
) {}
