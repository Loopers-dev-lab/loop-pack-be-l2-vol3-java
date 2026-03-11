package com.loopers.application.admin;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 인증된 관리자의 식별 정보. 컨트롤러에서 "현재 관리자"를 식별하는 용도로 사용한다.
 */
@Getter
@AllArgsConstructor
public class AdminPrincipal {

    private final String adminId;
    private final String name;
    private final String department;

    public static AdminPrincipal of(String adminId, String name, String department) {
        return new AdminPrincipal(adminId, name, department);
    }
}
