package com.loopers.interfaces.api;

/**
 * 유저 정보가 필요한 요청에서 사용하는 헤더 이름.
 * <ul>
 *   <li>X-Loopers-LoginId : 로그인 ID</li>
 *   <li>X-Loopers-LoginPw : 비밀번호</li>
 *   <li>X-Admin-Id : 관리자 ID (LDAP)</li>
 *   <li>X-Admin-Token : 관리자 인증 토큰 (LDAP)</li>
 * </ul>
 */
public final class LoopersHeaders {

    public static final String X_LOOPERS_LOGIN_ID = "X-Loopers-LoginId";
    public static final String X_LOOPERS_LOGIN_PW = "X-Loopers-LoginPw";

    public static final String X_ADMIN_ID = "X-Admin-Id";
    public static final String X_ADMIN_TOKEN = "X-Admin-Token";

    private LoopersHeaders() {
    }
}
