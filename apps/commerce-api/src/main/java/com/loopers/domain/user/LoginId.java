package com.loopers.domain.user;

import jakarta.persistence.Embeddable;

@Embeddable
public class LoginId {
    private String value    ;

    //  기본 생성자 (protected)
    protected LoginId() {}

    // 매개변수 생성자 (단순 할당만 - 껍데기)
    public LoginId(String value) {
        this.value = value;
    }

    // getter
    public String getValue() {
        return value;
    }

}
