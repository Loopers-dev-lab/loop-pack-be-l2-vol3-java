package com.loopers.interfaces.api;

import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.user.User;
import com.loopers.domain.user.UserFixture;
import com.loopers.infrastructure.coupon.CouponJpaRepository;
import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.interfaces.api.coupon.CouponV1Dto;
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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CouponV1ApiE2ETest {

    private static final String ENDPOINT = "/api/v1/coupons";
    private static final String MY_COUPONS_ENDPOINT = "/api/v1/users/me/coupons";
    private static final String RAW_PASSWORD = "TestPass1!";

    private final TestRestTemplate testRestTemplate;
    private final UserJpaRepository userJpaRepository;
    private final CouponJpaRepository couponJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;
    private final BCryptPasswordEncoder bCryptPasswordEncoder = new BCryptPasswordEncoder();

    @Autowired
    public CouponV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        UserJpaRepository userJpaRepository,
        CouponJpaRepository couponJpaRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.userJpaRepository = userJpaRepository;
        this.couponJpaRepository = couponJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    private User savedUser;
    private Coupon savedCoupon;

    @BeforeEach
    void setUp() {
        String encodedPassword = bCryptPasswordEncoder.encode(RAW_PASSWORD);
        savedUser = userJpaRepository.save(
            UserFixture.builder()
                       .loginId("couponTestUser")
                       .password(encodedPassword)
                       .build()
        );

        savedCoupon = couponJpaRepository.save(
            Coupon.create("신규 회원 쿠폰", Coupon.DiscountType.FIXED, 1000L, 0L, LocalDateTime.now().plusDays(30))
        );
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders userHeaders(User user, String rawPassword) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", user.getLoginId());
        headers.set("X-Loopers-LoginPw", rawPassword);
        return headers;
    }

    @DisplayName("쿠폰 발급 시")
    @Nested
    class IssueCoupon {

        @DisplayName("유효한 쿠폰이면, 201 Created와 발급된 쿠폰 정보를 반환한다.")
        @Test
        void returnsCreated_whenValidRequest() {
            // arrange
            HttpEntity<Void> entity = new HttpEntity<>(userHeaders(savedUser, RAW_PASSWORD));

            // act
            ResponseEntity<ApiResponse<CouponV1Dto.IssuedCouponResponse>> response =
                testRestTemplate.exchange(
                    ENDPOINT + "/" + savedCoupon.getId() + "/issue",
                    HttpMethod.POST,
                    entity,
                    new ParameterizedTypeReference<>() {}
                );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                () -> assertThat(response.getBody().data().couponId()).isEqualTo(savedCoupon.getId()),
                () -> assertThat(response.getBody().data().status()).isEqualTo("AVAILABLE")
            );
        }

        @DisplayName("존재하지 않는 쿠폰이면, 404 Not Found를 반환한다.")
        @Test
        void returnsNotFound_whenCouponNotExists() {
            // arrange
            HttpEntity<Void> entity = new HttpEntity<>(userHeaders(savedUser, RAW_PASSWORD));

            // act
            ResponseEntity<ApiResponse<Void>> response =
                testRestTemplate.exchange(
                    ENDPOINT + "/99999/issue",
                    HttpMethod.POST,
                    entity,
                    new ParameterizedTypeReference<>() {}
                );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("이미 발급된 쿠폰이면, 409 Conflict를 반환한다.")
        @Test
        void returnsConflict_whenAlreadyIssued() {
            // arrange
            HttpEntity<Void> entity = new HttpEntity<>(userHeaders(savedUser, RAW_PASSWORD));
            testRestTemplate.exchange(
                ENDPOINT + "/" + savedCoupon.getId() + "/issue",
                HttpMethod.POST, entity, new ParameterizedTypeReference<>() {}
            );

            // act (두 번째 발급 시도)
            ResponseEntity<ApiResponse<Void>> response =
                testRestTemplate.exchange(
                    ENDPOINT + "/" + savedCoupon.getId() + "/issue",
                    HttpMethod.POST,
                    entity,
                    new ParameterizedTypeReference<>() {}
                );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }
    }

    @DisplayName("내 쿠폰 목록 조회 시")
    @Nested
    class GetMyCoupons {

        @DisplayName("발급된 쿠폰이 있으면, 200 OK와 쿠폰 목록을 반환한다.")
        @Test
        void returnsOk_withIssuedCouponList() {
            // arrange
            HttpEntity<Void> issueEntity = new HttpEntity<>(userHeaders(savedUser, RAW_PASSWORD));
            testRestTemplate.exchange(
                ENDPOINT + "/" + savedCoupon.getId() + "/issue",
                HttpMethod.POST, issueEntity, new ParameterizedTypeReference<>() {}
            );

            HttpEntity<Void> getEntity = new HttpEntity<>(userHeaders(savedUser, RAW_PASSWORD));

            // act
            ResponseEntity<ApiResponse<List<CouponV1Dto.IssuedCouponResponse>>> response =
                testRestTemplate.exchange(MY_COUPONS_ENDPOINT, HttpMethod.GET, getEntity, new ParameterizedTypeReference<>() {});

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data()).hasSize(1),
                () -> assertThat(response.getBody().data().get(0).couponId()).isEqualTo(savedCoupon.getId())
            );
        }

        @DisplayName("발급된 쿠폰이 없으면, 200 OK와 빈 목록을 반환한다.")
        @Test
        void returnsEmptyList_whenNoIssuedCoupons() {
            // arrange
            HttpEntity<Void> entity = new HttpEntity<>(userHeaders(savedUser, RAW_PASSWORD));

            // act
            ResponseEntity<ApiResponse<List<CouponV1Dto.IssuedCouponResponse>>> response =
                testRestTemplate.exchange(MY_COUPONS_ENDPOINT, HttpMethod.GET, entity, new ParameterizedTypeReference<>() {});

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data()).isEmpty()
            );
        }
    }
}
