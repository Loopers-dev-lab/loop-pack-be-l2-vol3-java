package com.loopers.interfaces.api.coupon;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import com.loopers.support.E2ETestFixture;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(E2ETestFixture.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CouponApiE2ETest {

    private static final String ENDPOINT = "/api/v1/coupons";
    private static final String LOGIN_ID = "testuser";
    private static final String LOGIN_PW = "Test1234!";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private E2ETestFixture fixture;

    @BeforeEach
    void setUp() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    class 쿠폰_발급 {

        private Long couponId;

        @BeforeEach
        void setUp() {
            fixture.signUp(LOGIN_ID, LOGIN_PW, "홍길동", "test@example.com");
            couponId = fixture.registerCoupon(
                    "1000원 할인", "FIXED", 1000,
                    BigDecimal.valueOf(10000), 100, LocalDateTime.now().plusDays(7)
            );
        }

        @Test
        void 유효한_쿠폰에_발급_요청하면_200_응답과_발급된_쿠폰_정보를_반환한다() {
            ResponseEntity<ApiResponse<CouponV1Dto.IssuedCouponResponse>> response = postIssue(couponId);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().id()).isNotNull(),
                    () -> assertThat(response.getBody().data().couponId()).isEqualTo(couponId),
                    () -> assertThat(response.getBody().data().couponName()).isEqualTo("1000원 할인"),
                    () -> assertThat(response.getBody().data().type()).isEqualTo("FIXED"),
                    () -> assertThat(response.getBody().data().value()).isEqualTo(1000),
                    () -> assertThat(response.getBody().data().status()).isEqualTo("AVAILABLE"),
                    () -> assertThat(response.getBody().data().expiredAt()).isNotNull(),
                    () -> assertThat(response.getBody().data().createdAt()).isNotNull()
            );
        }

        @Test
        void 미존재_쿠폰이면_404_응답() {
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT + "/999/issue", HttpMethod.POST,
                    new HttpEntity<>(fixture.userHeaders(LOGIN_ID, LOGIN_PW)),
                    new ParameterizedTypeReference<>() {}
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                    () -> assertThat(response.getBody().meta().message()).contains("존재하지 않는 쿠폰입니다")
            );
        }

        @Test
        void 이미_발급받은_쿠폰이면_409_응답() {
            fixture.issueCoupon(couponId, LOGIN_ID, LOGIN_PW);

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT + "/" + couponId + "/issue", HttpMethod.POST,
                    new HttpEntity<>(fixture.userHeaders(LOGIN_ID, LOGIN_PW)),
                    new ParameterizedTypeReference<>() {}
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT),
                    () -> assertThat(response.getBody().meta().message()).contains("이미 발급받은 쿠폰입니다")
            );
        }

        @Test
        void 인증_헤더가_누락되면_401_응답() {
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT + "/" + couponId + "/issue", HttpMethod.POST,
                    new HttpEntity<>(new HttpHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED),
                    () -> assertThat(response.getBody().meta().message()).contains("인증 헤더가 필요합니다")
            );
        }

        @Test
        void 인증에_실패하면_401_응답() {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", LOGIN_ID);
            headers.set("X-Loopers-LoginPw", "wrong-password");

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT + "/" + couponId + "/issue", HttpMethod.POST,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {}
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED),
                    () -> assertThat(response.getBody().meta().message()).contains("인증에 실패했습니다")
            );
        }
    }

    @Nested
    class 내_쿠폰_목록_조회 {

        private static final String MY_COUPONS_ENDPOINT = "/api/v1/users/me/coupons";

        @BeforeEach
        void setUp() {
            fixture.signUp(LOGIN_ID, LOGIN_PW, "홍길동", "test@example.com");
        }

        @Test
        void 보유_쿠폰을_최신순으로_페이징_조회하면_200_응답() {
            Long couponId1 = fixture.registerCoupon(
                    "1000원 할인", "FIXED", 1000,
                    BigDecimal.valueOf(10000), 100, LocalDateTime.now().plusDays(7)
            );
            Long couponId2 = fixture.registerCoupon(
                    "10% 할인", "RATE", 10,
                    null, 100, LocalDateTime.now().plusDays(7)
            );
            fixture.issueCoupon(couponId1, LOGIN_ID, LOGIN_PW);
            fixture.issueCoupon(couponId2, LOGIN_ID, LOGIN_PW);

            ResponseEntity<ApiResponse<PageResponse<CouponV1Dto.MyCouponResponse>>> response =
                    getMyCoupons(0, 20);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().content()).hasSize(2),
                    () -> assertThat(response.getBody().data().content().get(0).couponName()).isEqualTo("10% 할인"),
                    () -> assertThat(response.getBody().data().content().get(1).couponName()).isEqualTo("1000원 할인"),
                    () -> assertThat(response.getBody().data().totalElements()).isEqualTo(2)
            );
        }

        @Test
        void 상태_AVAILABLE_USED_EXPIRED를_반환한다() {
            Long couponId = fixture.registerCoupon(
                    "1000원 할인", "FIXED", 1000,
                    null, 100, LocalDateTime.now().plusDays(7)
            );
            fixture.issueCoupon(couponId, LOGIN_ID, LOGIN_PW);

            ResponseEntity<ApiResponse<PageResponse<CouponV1Dto.MyCouponResponse>>> response =
                    getMyCoupons(0, 20);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().content().get(0).status()).isEqualTo("AVAILABLE")
            );
        }

        @Test
        void 삭제된_발급_쿠폰은_제외된다() {
            Long couponId1 = fixture.registerCoupon(
                    "1000원 할인", "FIXED", 1000,
                    null, 100, LocalDateTime.now().plusDays(7)
            );
            Long couponId2 = fixture.registerCoupon(
                    "2000원 할인", "FIXED", 2000,
                    null, 100, LocalDateTime.now().plusDays(7)
            );
            fixture.issueCoupon(couponId1, LOGIN_ID, LOGIN_PW);
            fixture.issueCoupon(couponId2, LOGIN_ID, LOGIN_PW);
            fixture.deleteCoupon(couponId2);

            ResponseEntity<ApiResponse<PageResponse<CouponV1Dto.MyCouponResponse>>> response =
                    getMyCoupons(0, 20);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().content()).hasSize(1),
                    () -> assertThat(response.getBody().data().content().get(0).couponName()).isEqualTo("1000원 할인")
            );
        }

        @Test
        void 빈_목록이면_빈_배열을_반환한다() {
            ResponseEntity<ApiResponse<PageResponse<CouponV1Dto.MyCouponResponse>>> response =
                    getMyCoupons(0, 20);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().content()).isEmpty(),
                    () -> assertThat(response.getBody().data().totalElements()).isEqualTo(0)
            );
        }

        @Test
        void 요청_필드_규칙_위반시_400_응답() {
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    MY_COUPONS_ENDPOINT + "?page=-1", HttpMethod.GET,
                    new HttpEntity<>(fixture.userHeaders(LOGIN_ID, LOGIN_PW)),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        void 인증_헤더가_누락되면_401_응답() {
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    MY_COUPONS_ENDPOINT, HttpMethod.GET,
                    new HttpEntity<>(new HttpHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED),
                    () -> assertThat(response.getBody().meta().message()).contains("인증 헤더가 필요합니다")
            );
        }

        @Test
        void 인증에_실패하면_401_응답() {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", LOGIN_ID);
            headers.set("X-Loopers-LoginPw", "wrong-password");

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    MY_COUPONS_ENDPOINT, HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {}
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED),
                    () -> assertThat(response.getBody().meta().message()).contains("인증에 실패했습니다")
            );
        }

        private ResponseEntity<ApiResponse<PageResponse<CouponV1Dto.MyCouponResponse>>> getMyCoupons(
                int page, int size) {
            return testRestTemplate.exchange(
                    MY_COUPONS_ENDPOINT + "?page=" + page + "&size=" + size, HttpMethod.GET,
                    new HttpEntity<>(fixture.userHeaders(LOGIN_ID, LOGIN_PW)),
                    new ParameterizedTypeReference<>() {}
            );
        }
    }

    private ResponseEntity<ApiResponse<CouponV1Dto.IssuedCouponResponse>> postIssue(Long couponId) {
        return testRestTemplate.exchange(
                ENDPOINT + "/" + couponId + "/issue", HttpMethod.POST,
                new HttpEntity<>(fixture.userHeaders(LOGIN_ID, LOGIN_PW)),
                new ParameterizedTypeReference<>() {}
        );
    }
}
