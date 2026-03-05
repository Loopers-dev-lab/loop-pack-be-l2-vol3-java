package com.loopers.interfaces.api.coupon;

import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.UserCoupon;
import com.loopers.infrastructure.coupon.UserCouponJpaRepository;
import com.loopers.infrastructure.coupon.CouponTemplateJpaRepository;
import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.user.UserV1Dto;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CouponV1ApiE2ETest {

    private static final String VALID_LOGIN_ID = "couponuser1";
    private static final String VALID_PASSWORD = "coupon@1234";
    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";

    private static final String SIGNUP_ENDPOINT = "/api/v1/users/signup";
    private static final Function<Long, String> ENDPOINT_ISSUE_COUPON = id -> "/api/v1/coupons/" + id + "/issue";
    private static final String ENDPOINT_MY_COUPONS = "/api/v1/coupons/my";

    private static final String COUPON_NAME = "신규가입 10% 할인";
    private static final Long NOT_EXISTED_COUPON_ID = 999L;
    private static final LocalDateTime FUTURE_EXPIRED_AT = LocalDateTime.now().plusDays(30);

    private final TestRestTemplate testRestTemplate;
    private final DatabaseCleanUp databaseCleanUp;
    private final CouponTemplateJpaRepository couponTemplateJpaRepository;
    private final UserCouponJpaRepository userCouponJpaRepository;
    private final UserJpaRepository userJpaRepository;

    @Autowired
    public CouponV1ApiE2ETest(
            TestRestTemplate testRestTemplate,
            DatabaseCleanUp databaseCleanUp,
            CouponTemplateJpaRepository couponTemplateJpaRepository,
            UserCouponJpaRepository userCouponJpaRepository,
            UserJpaRepository userJpaRepository
    ) {
        this.testRestTemplate = testRestTemplate;
        this.databaseCleanUp = databaseCleanUp;
        this.couponTemplateJpaRepository = couponTemplateJpaRepository;
        this.userCouponJpaRepository = userCouponJpaRepository;
        this.userJpaRepository = userJpaRepository;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    Long signUpAndGetUserId(String loginId, String password, String name) {
        UserV1Dto.SignupRequest signupRequest = new UserV1Dto.SignupRequest(
                loginId, password, name, "1990-01-01", loginId + "@test.com"
        );
        testRestTemplate.exchange(
                SIGNUP_ENDPOINT, HttpMethod.POST,
                new HttpEntity<>(signupRequest),
                new ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>>() {}
        );
        return userJpaRepository.findByLoginId(loginId).orElseThrow().getId();
    }

    HttpHeaders createUserHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_LOGIN_ID, VALID_LOGIN_ID);
        headers.set(HEADER_LOGIN_PW, VALID_PASSWORD);
        return headers;
    }

    CouponTemplate createSavedTemplate() {
        return couponTemplateJpaRepository.save(
                new CouponTemplate(COUPON_NAME, CouponType.RATE, 10, null, FUTURE_EXPIRED_AT)
        );
    }

    @DisplayName("POST /api/v1/coupons/{couponId}/issue")
    @Nested
    class IssueCoupon {

        @DisplayName("인증된 회원이 쿠폰을 발급 요청하면, 200 OK와 발급 쿠폰 정보를 반환한다.")
        @Test
        void returnsUserCouponResponse_whenIssuedSuccessfully() {
            // arrange
            signUpAndGetUserId(VALID_LOGIN_ID, VALID_PASSWORD, "쿠폰유저");
            CouponTemplate template = createSavedTemplate();

            // act
            ParameterizedTypeReference<ApiResponse<CouponV1Dto.UserCouponResponse>> responseType =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<CouponV1Dto.UserCouponResponse>> response = testRestTemplate.exchange(
                    ENDPOINT_ISSUE_COUPON.apply(template.getId()),
                    HttpMethod.POST,
                    new HttpEntity<>(createUserHeaders()),
                    responseType
            );

            // assert
            assertAll(
                    () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                    () -> assertThat(response.getBody().data().userCouponId()).isPositive(),
                    () -> assertThat(response.getBody().data().couponTemplateId()).isEqualTo(template.getId())
            );
        }

        @DisplayName("인증 헤더 없이 쿠폰 발급을 요청하면, 401 UNAUTHORIZED를 반환한다.")
        @Test
        void returnsUnauthorized_whenAuthHeaderMissing() {
            // arrange
            CouponTemplate template = createSavedTemplate();

            // act
            ParameterizedTypeReference<ApiResponse<CouponV1Dto.UserCouponResponse>> responseType =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<CouponV1Dto.UserCouponResponse>> response = testRestTemplate.exchange(
                    ENDPOINT_ISSUE_COUPON.apply(template.getId()),
                    HttpMethod.POST,
                    HttpEntity.EMPTY,
                    responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(ErrorType.UNAUTHORIZED.getStatus());
        }

        @DisplayName("존재하지 않는 쿠폰 템플릿에 발급 요청하면, 404 NOT_FOUND를 반환한다.")
        @Test
        void returnsNotFound_whenTemplateDoesNotExist() {
            // arrange
            signUpAndGetUserId(VALID_LOGIN_ID, VALID_PASSWORD, "쿠폰유저");

            // act
            ParameterizedTypeReference<ApiResponse<CouponV1Dto.UserCouponResponse>> responseType =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<CouponV1Dto.UserCouponResponse>> response = testRestTemplate.exchange(
                    ENDPOINT_ISSUE_COUPON.apply(NOT_EXISTED_COUPON_ID),
                    HttpMethod.POST,
                    new HttpEntity<>(createUserHeaders()),
                    responseType
            );

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(ErrorType.NOT_FOUND.getStatus()),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL)
            );
        }

        @DisplayName("이미 발급받은 쿠폰에 재발급을 요청하면, 409 CONFLICT를 반환한다. (BR-C03)")
        @Test
        void returnsConflict_whenAlreadyIssued() {
            // arrange
            Long userId = signUpAndGetUserId(VALID_LOGIN_ID, VALID_PASSWORD, "쿠폰유저");
            CouponTemplate template = createSavedTemplate();
            userCouponJpaRepository.save(new UserCoupon(template.getId(), userId, template.getExpiredAt()));

            // act
            ParameterizedTypeReference<ApiResponse<CouponV1Dto.UserCouponResponse>> responseType =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<CouponV1Dto.UserCouponResponse>> response = testRestTemplate.exchange(
                    ENDPOINT_ISSUE_COUPON.apply(template.getId()),
                    HttpMethod.POST,
                    new HttpEntity<>(createUserHeaders()),
                    responseType
            );

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(ErrorType.CONFLICT.getStatus()),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL)
            );
        }

    }

    @DisplayName("GET /api/v1/coupons/my")
    @Nested
    class GetMyCoupons {

        @DisplayName("인증된 회원이 내 쿠폰 목록을 조회하면, 200 OK와 쿠폰 목록을 반환한다.")
        @Test
        void returnsMyCouponList_whenUserHasCoupons() {
            // arrange
            Long userId = signUpAndGetUserId(VALID_LOGIN_ID, VALID_PASSWORD, "쿠폰유저");
            CouponTemplate template = createSavedTemplate();
            userCouponJpaRepository.save(new UserCoupon(template.getId(), userId, template.getExpiredAt()));

            // act
            ParameterizedTypeReference<ApiResponse<CouponV1Dto.MyCouponListResponse>> responseType =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<CouponV1Dto.MyCouponListResponse>> response = testRestTemplate.exchange(
                    ENDPOINT_MY_COUPONS,
                    HttpMethod.GET,
                    new HttpEntity<>(createUserHeaders()),
                    responseType
            );

            // assert
            assertAll(
                    () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                    () -> assertThat(response.getBody().data().coupons()).hasSize(1)
            );
        }

        @DisplayName("인증 헤더 없이 조회하면, 401 UNAUTHORIZED를 반환한다.")
        @Test
        void returnsUnauthorized_whenAuthHeaderMissing() {
            // act
            ParameterizedTypeReference<ApiResponse<CouponV1Dto.MyCouponListResponse>> responseType =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<CouponV1Dto.MyCouponListResponse>> response = testRestTemplate.exchange(
                    ENDPOINT_MY_COUPONS,
                    HttpMethod.GET,
                    HttpEntity.EMPTY,
                    responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(ErrorType.UNAUTHORIZED.getStatus());
        }
    }
}
