package com.loopers.interfaces.api.coupon;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.member.MemberDto;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ImportTestcontainers(MySqlTestContainersConfig.class)
@ActiveProfiles("test")
@DisplayName("Coupon API E2E 테스트")
class CouponApiE2ETest {

    private static final String TEST_LOGIN_ID = "coupone2euser";
    private static final String TEST_PASSWORD = "Test1234!@";
    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @BeforeEach
    void setUp() {
        MemberDto.RegisterRequest registerRequest = new MemberDto.RegisterRequest(
                TEST_LOGIN_ID,
                TEST_PASSWORD,
                "쿠폰이",
                "19900101",
                "coupon.e2e@test.com",
                "010-9999-8888"
        );

        testRestTemplate.exchange(
                "/api/v1/members",
                HttpMethod.POST,
                new HttpEntity<>(registerRequest),
                new ParameterizedTypeReference<ApiResponse<Void>>() {}
        );
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_LOGIN_ID, TEST_LOGIN_ID);
        headers.set(HEADER_LOGIN_PW, TEST_PASSWORD);
        return headers;
    }

    @Nested
    @DisplayName("쿠폰 발급/조회")
    class CouponIssueAndList {
        @Test
        @DisplayName("존재하지 않는 쿠폰 발급 요청은 404")
        void issueInvalidCouponId() {
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                    "/api/v1/coupons/00000000-0000-0000-0000-000000000999/issue",
                    HttpMethod.POST,
                    new HttpEntity<>(authHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("내 쿠폰 목록 조회는 200")
        void listMyCoupons() {
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                    "/api/v1/users/me/coupons",
                    HttpMethod.GET,
                    new HttpEntity<>(authHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }
}
