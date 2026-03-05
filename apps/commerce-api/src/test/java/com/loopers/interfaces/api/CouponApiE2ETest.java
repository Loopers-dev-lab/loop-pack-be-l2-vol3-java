package com.loopers.interfaces.api;

import com.loopers.domain.common.Money;
import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponRepository;
import com.loopers.domain.coupon.DiscountType;
import com.loopers.domain.coupon.IssuedCouponStatus;
import com.loopers.domain.member.Member;
import com.loopers.domain.member.MemberRepository;
import com.loopers.domain.member.PasswordEncoder;
import com.loopers.domain.member.vo.BirthDate;
import com.loopers.domain.member.vo.Email;
import com.loopers.domain.member.vo.MemberId;
import com.loopers.domain.member.vo.Name;
import com.loopers.domain.member.vo.Password;
import com.loopers.interfaces.api.coupon.CouponDto;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CouponApiE2ETest {

    private static final String TEST_PASSWORD = "Password1!";

    @Autowired
    private TestRestTemplate testRestTemplate;
    @Autowired
    private CouponRepository couponRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private Coupon testCoupon;
    private Member testMember;

    @BeforeEach
    void setUp() {
        testCoupon = couponRepository.save(Coupon.create(
                "1000원 할인", DiscountType.FIXED, Money.of(1000L), Money.of(5000L), null, 100,
                ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(30)
        ));
        testMember = createTestMember("testuser", TEST_PASSWORD);
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    @DisplayName("POST /api/v1/coupons/{couponId}/issue")
    class IssueCouponTest {

        @Test
        @DisplayName("쿠폰을 발급할 수 있다")
        void issue_success() {
            HttpEntity<Void> httpEntity = createHttpEntity(null, "testuser", TEST_PASSWORD);

            ResponseEntity<ApiResponse<CouponDto.IssuedCouponResponse>> response = testRestTemplate.exchange(
                    "/api/v1/coupons/" + testCoupon.getId() + "/issue",
                    HttpMethod.POST, httpEntity, new ParameterizedTypeReference<>() {}
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().couponId()).isEqualTo(testCoupon.getId()),
                    () -> assertThat(response.getBody().data().status()).isEqualTo(IssuedCouponStatus.AVAILABLE)
            );
        }
    }

    @Nested
    @DisplayName("GET /api/v1/users/me/coupons")
    class GetMyIssuedCouponsTest {

        @Test
        @DisplayName("내 쿠폰 목록을 조회할 수 있다")
        void getMyCoupons_success() {
            HttpEntity<Void> issueEntity = createHttpEntity(null, "testuser", TEST_PASSWORD);
            testRestTemplate.exchange(
                    "/api/v1/coupons/" + testCoupon.getId() + "/issue",
                    HttpMethod.POST, issueEntity, new ParameterizedTypeReference<ApiResponse<Object>>() {}
            );

            HttpEntity<Void> httpEntity = createHttpEntity(null, "testuser", TEST_PASSWORD);
            ResponseEntity<ApiResponse<CouponDto.IssuedCouponListResponse>> response = testRestTemplate.exchange(
                    "/api/v1/users/me/coupons", HttpMethod.GET, httpEntity, new ParameterizedTypeReference<>() {}
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().coupons()).hasSize(1),
                    () -> assertThat(response.getBody().data().coupons().get(0).couponName()).isEqualTo("1000원 할인")
            );
        }
    }

    private Member createTestMember(String memberId, String rawPassword) {
        String encodedPassword = passwordEncoder.encode(rawPassword);
        Member member = Member.create(
                new MemberId(memberId),
                Password.ofEncoded(encodedPassword),
                new Name("테스트"),
                new Email(memberId + "@test.com"),
                new BirthDate("1997-01-01")
        );
        return memberRepository.save(member);
    }

    private <T> HttpEntity<T> createHttpEntity(T body, String memberId, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Loopers-LoginId", memberId);
        headers.set("X-Loopers-LoginPw", password);
        return new HttpEntity<>(body, headers);
    }
}
