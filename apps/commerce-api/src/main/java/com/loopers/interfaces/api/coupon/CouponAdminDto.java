package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponDetailInfo;
import com.loopers.application.coupon.CouponListInfo;

import java.time.ZonedDateTime;

public class CouponAdminDto {

    public static record RegisterRequest(String name, String type, int value, Integer totalQuantity, ZonedDateTime expiredAt) {
    }

    public static record RegisterResponse(Long couponId) {
    }

    public static record UpdateRequest(String name, int value, ZonedDateTime expiredAt) {
    }

    public static record ListResponse(Long id, String name, String type) {

        public static ListResponse from(CouponListInfo info) {
            return new ListResponse(info.id(), info.name(), info.type());
        }
    }

    public static record DetailResponse(Long id, String name, String type, int value, ZonedDateTime expiredAt) {

        public static DetailResponse from(CouponDetailInfo info) {
            return new DetailResponse(info.id(), info.name(), info.type(), info.value(), info.expiredAt());
        }
    }
}
