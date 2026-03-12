package com.loopers.domain.coupon;

import com.loopers.support.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CouponErrorCode implements ErrorCode {
    NOT_FOUND(HttpStatus.NOT_FOUND, "COUPON_001", "쿠폰을 찾을 수 없습니다."),
    ALREADY_DELETED(HttpStatus.BAD_REQUEST, "COUPON_002", "삭제된 쿠폰입니다."),
    EXPIRED(HttpStatus.BAD_REQUEST, "COUPON_003", "만료된 쿠폰입니다."),
    QUANTITY_EXHAUSTED(HttpStatus.BAD_REQUEST, "COUPON_004", "쿠폰 수량이 소진되었습니다."),
    ALREADY_ISSUED(HttpStatus.CONFLICT, "COUPON_005", "이미 발급받은 쿠폰입니다."),
    NOT_OWNED(HttpStatus.FORBIDDEN, "COUPON_006", "본인 소유의 쿠폰이 아닙니다."),
    ALREADY_USED(HttpStatus.BAD_REQUEST, "COUPON_007", "이미 사용된 쿠폰입니다."),
    MIN_ORDER_AMOUNT_NOT_MET(HttpStatus.BAD_REQUEST, "COUPON_008", "최소 주문 금액을 충족하지 않습니다."),
    NOT_RESTORABLE(HttpStatus.BAD_REQUEST, "COUPON_009", "복원할 수 없는 쿠폰입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
