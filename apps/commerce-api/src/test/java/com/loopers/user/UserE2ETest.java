package com.loopers.user;

import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.user.dto.CreateUserRequest;
import com.loopers.user.dto.CreateUserResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(MySqlTestContainersConfig.class)
@Transactional
public class UserE2ETest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void 회원가입_API_요청시_사용자가_생성되고_201_Created_반환() {
        // given
        CreateUserRequest request = new CreateUserRequest(
                "testuser",
                "Password1!",
                "홍길동",
                "1990-01-01",
                "test@example.com"
        );

        //실제 HTTP 요청
        ResponseEntity<CreateUserResponse> response = restTemplate.postForEntity(
                "/api/v1/users",
                request,
                CreateUserResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }
}
