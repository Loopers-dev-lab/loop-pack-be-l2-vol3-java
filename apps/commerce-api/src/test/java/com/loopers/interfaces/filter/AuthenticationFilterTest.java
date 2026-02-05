package com.loopers.interfaces.filter;

import com.loopers.domain.member.Member;
import com.loopers.domain.member.MemberRepository;
import com.loopers.domain.member.PasswordEncoder;
import com.loopers.domain.member.vo.BirthDate;
import com.loopers.domain.member.vo.Email;
import com.loopers.domain.member.vo.MemberId;
import com.loopers.domain.member.vo.Name;
import com.loopers.domain.member.vo.Password;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthenticationFilterTest {

    private static final String ME_ENDPOINT = "/api/v1/members/me";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private Member testMember;

    @BeforeEach
    void setUp() {
        String encodedPassword = passwordEncoder.encode("Password1!");
        testMember = new Member(
                new MemberId("testuser"),
                Password.ofEncoded(encodedPassword),
                new Name("홍길동"),
                new Email("test@test.com"),
                new BirthDate("1997-01-01")
        );
        memberRepository.save(testMember);
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("인증 필터 테스트")
    @Nested
    class AuthenticationTest {

        @DisplayName("인증 헤더 없이 보호된 API에 접근하면 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        void access_protected_api_without_auth_header_returns_unauthorized() {
            // act
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ME_ENDPOINT,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @DisplayName("잘못된 비밀번호로 접근하면 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        void access_protected_api_with_wrong_password_returns_unauthorized() {
            // arrange
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", "testuser");
            headers.set("X-Loopers-LoginPw", "WrongPassword1!");
            HttpEntity<Void> httpEntity = new HttpEntity<>(headers);

            // act
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ME_ENDPOINT,
                    HttpMethod.GET,
                    httpEntity,
                    new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @DisplayName("존재하지 않는 사용자로 접근하면 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        void access_protected_api_with_non_existent_user_returns_unauthorized() {
            // arrange
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", "nonexistent");
            headers.set("X-Loopers-LoginPw", "Password1!");
            HttpEntity<Void> httpEntity = new HttpEntity<>(headers);

            // act
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ME_ENDPOINT,
                    HttpMethod.GET,
                    httpEntity,
                    new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @DisplayName("유효한 인증 정보로 보호된 API에 접근하면 성공한다.")
        @Test
        void access_protected_api_with_valid_auth_returns_success() {
            // arrange
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", "testuser");
            headers.set("X-Loopers-LoginPw", "Password1!");
            HttpEntity<Void> httpEntity = new HttpEntity<>(headers);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                    ME_ENDPOINT,
                    HttpMethod.GET,
                    httpEntity,
                    new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }
}
