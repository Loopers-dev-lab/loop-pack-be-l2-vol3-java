package com.loopers.interfaces.api;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public class UserApiE2ETest {

    @Test
    @DisplayName("회원가입 API 호출 테스트")
    void userSignupApiTest() {
        TestRestTemplate rest = new TestRestTemplate();

        ResponseEntity<String> res =
            rest.postForEntity("http://localhost:8080/users",null, String.class);

        Assertions.assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
