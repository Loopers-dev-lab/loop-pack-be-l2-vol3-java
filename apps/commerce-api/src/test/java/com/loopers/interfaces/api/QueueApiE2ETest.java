package com.loopers.interfaces.api;

import com.loopers.application.queue.TokenService;
import com.loopers.domain.member.Member;
import com.loopers.domain.member.MemberRepository;
import com.loopers.domain.member.PasswordEncoder;
import com.loopers.domain.member.vo.BirthDate;
import com.loopers.domain.member.vo.Email;
import com.loopers.domain.member.vo.MemberId;
import com.loopers.domain.member.vo.Name;
import com.loopers.domain.member.vo.Password;
import com.loopers.interfaces.api.queue.QueueDto;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "queue.scheduler.enabled=false")
class QueueApiE2ETest {

    private static final String QUEUE_ENDPOINT = "/api/v1/queue";

    @Autowired private TestRestTemplate testRestTemplate;
    @Autowired private MemberRepository memberRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private DatabaseCleanUp databaseCleanUp;
    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private TokenService tokenService;

    private Member testMember;
    private static final String TEST_PASSWORD = "Password1!";

    @BeforeEach
    void setUp() {
        testMember = createTestMember("queueuser", TEST_PASSWORD);
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisTemplate.delete("order:waiting-queue");
        redisTemplate.delete("entry-token:" + testMember.getId());
    }

    @DisplayName("POST /api/v1/queue/enter")
    @Nested
    class Enter {

        @DisplayName("대기열에 진입하면 순번을 반환한다.")
        @Test
        void enter_success() {
            HttpEntity<Void> request = new HttpEntity<>(createAuthHeaders("queueuser", TEST_PASSWORD));

            ResponseEntity<ApiResponse<QueueDto.EnterResponse>> response = testRestTemplate.exchange(
                    QUEUE_ENDPOINT + "/enter",
                    HttpMethod.POST,
                    request,
                    new ParameterizedTypeReference<>() {}
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                    () -> assertThat(response.getBody().data().position()).isEqualTo(1L)
            );
        }

        @DisplayName("인증 없이 진입하면 401을 반환한다.")
        @Test
        void enter_unauthorized() {
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    QUEUE_ENDPOINT + "/enter",
                    HttpMethod.POST,
                    HttpEntity.EMPTY,
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @DisplayName("GET /api/v1/queue/position")
    @Nested
    class Position {

        @DisplayName("대기 중인 유저의 순번과 예상 대기시간을 반환한다.")
        @Test
        void position_success() {
            HttpHeaders headers = createAuthHeaders("queueuser", TEST_PASSWORD);
            testRestTemplate.exchange(QUEUE_ENDPOINT + "/enter", HttpMethod.POST, new HttpEntity<>(headers), new ParameterizedTypeReference<>() {});

            ResponseEntity<ApiResponse<QueueDto.PositionResponse>> response = testRestTemplate.exchange(
                    QUEUE_ENDPOINT + "/position",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {}
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().position()).isEqualTo(1L),
                    () -> assertThat(response.getBody().data().estimatedWaitSeconds()).isGreaterThanOrEqualTo(0L)
            );
        }

        @DisplayName("대기열에 없고 토큰도 없는 유저가 조회하면 tokenIssued=false를 반환한다.")
        @Test
        void position_not_found() {
            HttpEntity<Void> request = new HttpEntity<>(createAuthHeaders("queueuser", TEST_PASSWORD));

            ResponseEntity<ApiResponse<QueueDto.PositionResponse>> response = testRestTemplate.exchange(
                    QUEUE_ENDPOINT + "/position",
                    HttpMethod.GET,
                    request,
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().tokenIssued()).isFalse();
        }
    }

    private Member createTestMember(String memberId, String rawPassword) {
        String encodedPassword = passwordEncoder.encode(rawPassword);
        return memberRepository.save(Member.create(
                new MemberId(memberId),
                Password.ofEncoded(encodedPassword),
                new Name("테스트"),
                new Email(memberId + "@test.com"),
                new BirthDate("1997-01-01")
        ));
    }

    private HttpHeaders createAuthHeaders(String memberId, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", memberId);
        headers.set("X-Loopers-LoginPw", password);
        return headers;
    }
}
