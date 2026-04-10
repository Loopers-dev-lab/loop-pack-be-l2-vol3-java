package com.loopers.interfaces.api.product.dto;

import com.loopers.support.util.RequestUtil;
import jakarta.servlet.http.HttpServletRequest;

public record FindProductApiReqDto(String loginId, String password, Long productId, String clientIp) {

    public static FindProductApiReqDto of(String loginId, String password, Long productId, HttpServletRequest request) {
        return new FindProductApiReqDto(loginId, password, productId, RequestUtil.getClientIp(request));
    }
}
