package com.loopers.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.error.ErrorType;

/**
 * E2E 테스트용 정적 유틸리티 메서드를 제공한다.
 *
 * <p>인증 헤더 생성 및 에러 응답 검증 등 E2E 테스트에서 반복되는 작업을 캡슐화한다.
 */
public class E2ETestHelper {

    /**
     * 일반 사용자 인증 헤더를 생성한다.
     *
     * @param loginId 로그인 ID
     * @param loginPw 로그인 비밀번호
     * @return 사용자 인증 정보가 포함된 HTTP 헤더
     */
    public static HttpHeaders userAuthHeaders(String loginId, String loginPw) {
        var headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", loginId);
        headers.set("X-Loopers-LoginPw", loginPw);
        return headers;
    }

    /**
     * 어드민 인증 헤더를 생성한다.
     *
     * @return 어드민 LDAP 인증 정보가 포함된 HTTP 헤더
     */
    public static HttpHeaders adminAuthHeaders() {
        var headers = new HttpHeaders();
        headers.set("X-Loopers-Ldap", "loopers.admin");
        return headers;
    }

    /**
     * 에러 응답의 상태 코드와 에러 코드를 검증한다.
     *
     * @param response  검증할 API 응답
     * @param status    기대하는 HTTP 상태 코드
     * @param errorType 기대하는 에러 타입
     * @param <T>       응답 본문의 타입
     */
    public static <T> void assertErrorResponse(
            ResponseEntity<ApiResponse<T>> response,
            HttpStatus status,
            ErrorType errorType
    ) {
        assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(status),
                () -> assertThat(response.getBody()).isNotNull(),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(errorType.getCode())
        );
    }
}