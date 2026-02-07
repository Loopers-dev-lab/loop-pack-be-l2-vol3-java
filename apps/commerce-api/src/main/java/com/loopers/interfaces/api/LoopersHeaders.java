package com.loopers.interfaces.api;

/**
 * 유저 정보가 필요한 요청에서 사용하는 헤더 이름.
 * <ul>
 *   <li>X-Loopers-LoginId : 로그인 ID</li>
 *   <li>X-Loopers-LoginPw : 비밀번호</li>
 * </ul>
 */
public final class LoopersHeaders {

    public static final String X_LOOPERS_LOGIN_ID = "X-Loopers-LoginId";
    public static final String X_LOOPERS_LOGIN_PW = "X-Loopers-LoginPw";

    private LoopersHeaders() {
    }
}
