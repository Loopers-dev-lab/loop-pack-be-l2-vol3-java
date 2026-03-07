package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponService;
import com.loopers.domain.coupon.CouponRepository;
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
class CouponAdminApiE2ETest {

    private static final String ENDPOINT = "/api-admin/v1/coupons";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private E2ETestFixture fixture;

    @Autowired
    private CouponService couponService;

    @Autowired
    private CouponRepository couponRepository;

    @BeforeEach
    void setUp() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    class 쿠폰_등록 {

        @Test
        void 유효한_정보로_등록하면_200_응답과_쿠폰_정보를_반환한다() {
            CouponRequest.Register request = new CouponRequest.Register(
                    "1000원 할인", "FIXED", 1000,
                    BigDecimal.valueOf(10000), 100, LocalDateTime.now().plusDays(7)
            );

            ResponseEntity<ApiResponse<CouponAdminV1Dto.CouponResponse>> response = postRegister(request);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().id()).isNotNull(),
                    () -> assertThat(response.getBody().data().name()).isEqualTo("1000원 할인"),
                    () -> assertThat(response.getBody().data().type()).isEqualTo("FIXED"),
                    () -> assertThat(response.getBody().data().value()).isEqualTo(1000),
                    () -> assertThat(response.getBody().data().minOrderAmount()).isEqualByComparingTo(BigDecimal.valueOf(10000)),
                    () -> assertThat(response.getBody().data().maxIssueCount()).isEqualTo(100),
                    () -> assertThat(response.getBody().data().issuedCount()).isEqualTo(0),
                    () -> assertThat(response.getBody().data().expiredAt()).isNotNull(),
                    () -> assertThat(response.getBody().data().createdAt()).isNotNull(),
                    () -> assertThat(response.getBody().data().updatedAt()).isNotNull()
            );
        }

        @Test
        void 정률_타입으로_등록하면_200_응답() {
            CouponRequest.Register request = new CouponRequest.Register(
                    "10% 할인", "RATE", 10,
                    null, 50, LocalDateTime.now().plusDays(7)
            );

            ResponseEntity<ApiResponse<CouponAdminV1Dto.CouponResponse>> response = postRegister(request);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().type()).isEqualTo("RATE"),
                    () -> assertThat(response.getBody().data().minOrderAmount()).isNull()
            );
        }

        @Test
        void 정률_타입_할인값이_100을_초과하면_400_응답() {
            CouponRequest.Register request = new CouponRequest.Register(
                    "101% 할인", "RATE", 101,
                    null, 100, LocalDateTime.now().plusDays(7)
            );

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT, HttpMethod.POST,
                    new HttpEntity<>(request, fixture.adminHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                    () -> assertThat(response.getBody().meta().message()).contains("유효하지 않은 할인값입니다")
            );
        }

        @Test
        void 만료일이_현재보다_과거이면_400_응답() {
            CouponRequest.Register request = new CouponRequest.Register(
                    "쿠폰", "FIXED", 1000,
                    null, 100, LocalDateTime.now().minusDays(1)
            );

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT, HttpMethod.POST,
                    new HttpEntity<>(request, fixture.adminHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                    () -> assertThat(response.getBody().meta().message()).contains("만료일은 현재 이후여야 합니다")
            );
        }

        @Test
        void 쿠폰명이_빈값이면_400_응답() {
            CouponRequest.Register request = new CouponRequest.Register(
                    "", "FIXED", 1000,
                    null, 100, LocalDateTime.now().plusDays(7)
            );

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT, HttpMethod.POST,
                    new HttpEntity<>(request, fixture.adminHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        void 인증헤더가_누락되면_401_응답() {
            CouponRequest.Register request = new CouponRequest.Register(
                    "쿠폰", "FIXED", 1000,
                    null, 100, LocalDateTime.now().plusDays(7)
            );

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT, HttpMethod.POST,
                    new HttpEntity<>(request, new HttpHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED),
                    () -> assertThat(response.getBody().meta().message()).contains("인증 헤더가 필요합니다")
            );
        }

        @Test
        void 인증에_실패하면_401_응답() {
            CouponRequest.Register request = new CouponRequest.Register(
                    "쿠폰", "FIXED", 1000,
                    null, 100, LocalDateTime.now().plusDays(7)
            );

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-Ldap", "wrong-ldap");

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT, HttpMethod.POST,
                    new HttpEntity<>(request, headers),
                    new ParameterizedTypeReference<>() {}
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED),
                    () -> assertThat(response.getBody().meta().message()).contains("인증에 실패했습니다")
            );
        }
    }

    @Nested
    class 쿠폰_삭제 {

        @Test
        void 활성_쿠폰을_삭제하면_200_응답() {
            Long couponId = fixture.registerCoupon(
                    "1000원 할인", "FIXED", 1000,
                    BigDecimal.valueOf(10000), 100, LocalDateTime.now().plusDays(7)
            );

            ResponseEntity<ApiResponse<Void>> response = deleteRequest(couponId);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void 미존재_쿠폰이면_404_응답() {
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT + "/999", HttpMethod.DELETE,
                    new HttpEntity<>(fixture.adminHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                    () -> assertThat(response.getBody().meta().message()).contains("존재하지 않는 쿠폰입니다")
            );
        }

        @Test
        void 삭제된_쿠폰을_다시_삭제해도_200_응답() {
            Long couponId = fixture.registerCoupon(
                    "1000원 할인", "FIXED", 1000,
                    BigDecimal.valueOf(10000), 100, LocalDateTime.now().plusDays(7)
            );
            fixture.deleteCoupon(couponId);

            ResponseEntity<ApiResponse<Void>> response = deleteRequest(couponId);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void 인증헤더가_누락되면_401_응답() {
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT + "/1", HttpMethod.DELETE,
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
            headers.set("X-Loopers-Ldap", "wrong-ldap");

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT + "/1", HttpMethod.DELETE,
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
    class 쿠폰_수정 {

        @Test
        void 유효한_정보로_수정하면_200_응답과_수정된_쿠폰_정보를_반환한다() {
            Long couponId = fixture.registerCoupon(
                    "1000원 할인", "FIXED", 1000,
                    BigDecimal.valueOf(10000), 100, LocalDateTime.now().plusDays(7)
            );
            CouponRequest.UpdateInfo request = new CouponRequest.UpdateInfo(
                    null, "2000원 할인", 2000, BigDecimal.valueOf(20000), 200, null
            );

            ResponseEntity<ApiResponse<CouponAdminV1Dto.CouponResponse>> response = patchUpdateInfo(couponId, request);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().id()).isEqualTo(couponId),
                    () -> assertThat(response.getBody().data().name()).isEqualTo("2000원 할인"),
                    () -> assertThat(response.getBody().data().type()).isEqualTo("FIXED"),
                    () -> assertThat(response.getBody().data().value()).isEqualTo(2000),
                    () -> assertThat(response.getBody().data().minOrderAmount()).isEqualByComparingTo(BigDecimal.valueOf(20000)),
                    () -> assertThat(response.getBody().data().maxIssueCount()).isEqualTo(200),
                    () -> assertThat(response.getBody().data().issuedCount()).isEqualTo(0),
                    () -> assertThat(response.getBody().data().expiredAt()).isNotNull(),
                    () -> assertThat(response.getBody().data().createdAt()).isNotNull(),
                    () -> assertThat(response.getBody().data().updatedAt()).isNotNull()
            );
        }

        @Test
        void 이름만_수정하면_나머지_필드는_유지된다() {
            Long couponId = fixture.registerCoupon(
                    "1000원 할인", "FIXED", 1000,
                    BigDecimal.valueOf(10000), 100, LocalDateTime.now().plusDays(7)
            );
            CouponRequest.UpdateInfo request = new CouponRequest.UpdateInfo(
                    null, "수정된 이름", null, null, null, null
            );

            ResponseEntity<ApiResponse<CouponAdminV1Dto.CouponResponse>> response = patchUpdateInfo(couponId, request);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().name()).isEqualTo("수정된 이름"),
                    () -> assertThat(response.getBody().data().value()).isEqualTo(1000),
                    () -> assertThat(response.getBody().data().minOrderAmount()).isEqualByComparingTo(BigDecimal.valueOf(10000)),
                    () -> assertThat(response.getBody().data().maxIssueCount()).isEqualTo(100)
            );
        }

        @Test
        void type_필드를_전달하면_400_응답() {
            Long couponId = fixture.registerCoupon(
                    "1000원 할인", "FIXED", 1000,
                    null, 100, LocalDateTime.now().plusDays(7)
            );
            CouponRequest.UpdateInfo request = new CouponRequest.UpdateInfo(
                    "RATE", null, null, null, null, null
            );

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT + "/" + couponId, HttpMethod.PATCH,
                    new HttpEntity<>(request, fixture.adminHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                    () -> assertThat(response.getBody().meta().message()).contains("쿠폰 유형은 변경할 수 없습니다")
            );
        }

        @Test
        void 정률_타입_할인값이_100을_초과하면_400_응답() {
            Long couponId = fixture.registerCoupon(
                    "10% 할인", "RATE", 10,
                    null, 100, LocalDateTime.now().plusDays(7)
            );
            CouponRequest.UpdateInfo request = new CouponRequest.UpdateInfo(
                    null, null, 101, null, null, null
            );

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT + "/" + couponId, HttpMethod.PATCH,
                    new HttpEntity<>(request, fixture.adminHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                    () -> assertThat(response.getBody().meta().message()).contains("유효하지 않은 할인값입니다")
            );
        }

        @Test
        void 최대_발급_수량을_현재_발급_수량보다_작게_설정하면_400_응답() {
            Long couponId = fixture.registerCoupon(
                    "쿠폰", "FIXED", 1000,
                    null, 100, LocalDateTime.now().plusDays(7)
            );
            couponService.issue(couponId);
            couponService.issue(couponId);
            CouponRequest.UpdateInfo request = new CouponRequest.UpdateInfo(
                    null, null, null, null, 1, null
            );

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT + "/" + couponId, HttpMethod.PATCH,
                    new HttpEntity<>(request, fixture.adminHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                    () -> assertThat(response.getBody().meta().message()).contains("현재 발급 수량보다 작게 설정할 수 없습니다")
            );
        }

        @Test
        void 만료일이_현재보다_과거이면_400_응답() {
            Long couponId = fixture.registerCoupon(
                    "쿠폰", "FIXED", 1000,
                    null, 100, LocalDateTime.now().plusDays(7)
            );
            CouponRequest.UpdateInfo request = new CouponRequest.UpdateInfo(
                    null, null, null, null, null, LocalDateTime.now().minusDays(1)
            );

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT + "/" + couponId, HttpMethod.PATCH,
                    new HttpEntity<>(request, fixture.adminHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        void 미존재_쿠폰을_수정하면_404_응답() {
            CouponRequest.UpdateInfo request = new CouponRequest.UpdateInfo(
                    null, "수정", null, null, null, null
            );

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT + "/999", HttpMethod.PATCH,
                    new HttpEntity<>(request, fixture.adminHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                    () -> assertThat(response.getBody().meta().message()).contains("존재하지 않는 쿠폰입니다")
            );
        }

        @Test
        void 필드_규칙_위반시_400_응답() {
            Long couponId = fixture.registerCoupon(
                    "쿠폰", "FIXED", 1000,
                    null, 100, LocalDateTime.now().plusDays(7)
            );
            CouponRequest.UpdateInfo request = new CouponRequest.UpdateInfo(
                    null, null, -1, null, null, null
            );

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT + "/" + couponId, HttpMethod.PATCH,
                    new HttpEntity<>(request, fixture.adminHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        void 인증헤더가_누락되면_401_응답() {
            CouponRequest.UpdateInfo request = new CouponRequest.UpdateInfo(
                    null, "수정", null, null, null, null
            );

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT + "/1", HttpMethod.PATCH,
                    new HttpEntity<>(request, new HttpHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED),
                    () -> assertThat(response.getBody().meta().message()).contains("인증 헤더가 필요합니다")
            );
        }

        @Test
        void 인증에_실패하면_401_응답() {
            CouponRequest.UpdateInfo request = new CouponRequest.UpdateInfo(
                    null, "수정", null, null, null, null
            );
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-Ldap", "wrong-ldap");

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT + "/1", HttpMethod.PATCH,
                    new HttpEntity<>(request, headers),
                    new ParameterizedTypeReference<>() {}
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED),
                    () -> assertThat(response.getBody().meta().message()).contains("인증에 실패했습니다")
            );
        }
    }

    @Nested
    class 쿠폰_목록_조회 {

        @Test
        void 활성_쿠폰을_최신_등록순으로_페이징_조회하면_200_응답() {
            fixture.registerCoupon("쿠폰A", "FIXED", 1000, null, 100, LocalDateTime.now().plusDays(7));
            fixture.registerCoupon("쿠폰B", "RATE", 10, null, 50, LocalDateTime.now().plusDays(7));

            ResponseEntity<ApiResponse<PageResponse<CouponAdminV1Dto.CouponResponse>>> response = getList(0, 20);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().content()).hasSize(2),
                    () -> assertThat(response.getBody().data().content().get(0).name()).isEqualTo("쿠폰B"),
                    () -> assertThat(response.getBody().data().content().get(1).name()).isEqualTo("쿠폰A"),
                    () -> assertThat(response.getBody().data().totalElements()).isEqualTo(2)
            );
        }

        @Test
        void 삭제된_쿠폰은_목록에서_제외된다() {
            fixture.registerCoupon("쿠폰A", "FIXED", 1000, null, 100, LocalDateTime.now().plusDays(7));
            Long deletedId = fixture.registerCoupon("쿠폰B", "FIXED", 2000, null, 100, LocalDateTime.now().plusDays(7));
            fixture.deleteCoupon(deletedId);

            ResponseEntity<ApiResponse<PageResponse<CouponAdminV1Dto.CouponResponse>>> response = getList(0, 20);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().content()).hasSize(1),
                    () -> assertThat(response.getBody().data().content().get(0).name()).isEqualTo("쿠폰A")
            );
        }

        @Test
        void 결과가_없으면_빈_목록을_반환한다() {
            ResponseEntity<ApiResponse<PageResponse<CouponAdminV1Dto.CouponResponse>>> response = getList(0, 20);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().content()).isEmpty(),
                    () -> assertThat(response.getBody().data().totalElements()).isEqualTo(0)
            );
        }

        @Test
        void 요청_필드_규칙_위반시_400_응답() {
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT + "?page=-1", HttpMethod.GET,
                    new HttpEntity<>(fixture.adminHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        void 인증헤더가_누락되면_401_응답() {
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT, HttpMethod.GET,
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
            headers.set("X-Loopers-Ldap", "wrong-ldap");

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT, HttpMethod.GET,
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
    class 쿠폰_상세_조회 {

        @Test
        void 활성_쿠폰을_조회하면_200_응답과_상세_정보를_반환한다() {
            Long couponId = fixture.registerCoupon(
                    "1000원 할인", "FIXED", 1000,
                    BigDecimal.valueOf(10000), 100, LocalDateTime.now().plusDays(7)
            );

            ResponseEntity<ApiResponse<CouponAdminV1Dto.CouponResponse>> response = getDetail(couponId);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().id()).isEqualTo(couponId),
                    () -> assertThat(response.getBody().data().name()).isEqualTo("1000원 할인"),
                    () -> assertThat(response.getBody().data().type()).isEqualTo("FIXED"),
                    () -> assertThat(response.getBody().data().value()).isEqualTo(1000),
                    () -> assertThat(response.getBody().data().minOrderAmount()).isEqualByComparingTo(BigDecimal.valueOf(10000)),
                    () -> assertThat(response.getBody().data().maxIssueCount()).isEqualTo(100),
                    () -> assertThat(response.getBody().data().issuedCount()).isEqualTo(0),
                    () -> assertThat(response.getBody().data().expiredAt()).isNotNull(),
                    () -> assertThat(response.getBody().data().createdAt()).isNotNull(),
                    () -> assertThat(response.getBody().data().updatedAt()).isNotNull()
            );
        }

        @Test
        void 미존재_쿠폰이면_404_응답() {
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT + "/999", HttpMethod.GET,
                    new HttpEntity<>(fixture.adminHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                    () -> assertThat(response.getBody().meta().message()).contains("존재하지 않는 쿠폰입니다")
            );
        }

        @Test
        void 삭제된_쿠폰이면_404_응답() {
            Long couponId = fixture.registerCoupon(
                    "1000원 할인", "FIXED", 1000,
                    null, 100, LocalDateTime.now().plusDays(7)
            );
            fixture.deleteCoupon(couponId);

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT + "/" + couponId, HttpMethod.GET,
                    new HttpEntity<>(fixture.adminHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        void 인증헤더가_누락되면_401_응답() {
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT + "/1", HttpMethod.GET,
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
            headers.set("X-Loopers-Ldap", "wrong-ldap");

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT + "/1", HttpMethod.GET,
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
    class 쿠폰_발급_내역_조회 {

        private static final String LOGIN_ID = "testuser";
        private static final String LOGIN_PW = "Test1234!";

        @Test
        void 발급_내역을_최신순으로_페이징_조회하면_200_응답() {
            Long couponId = fixture.registerCoupon(
                    "1000원 할인", "FIXED", 1000,
                    BigDecimal.valueOf(10000), 100, LocalDateTime.now().plusDays(7)
            );
            fixture.signUp(LOGIN_ID, LOGIN_PW, "홍길동", "test@example.com");
            fixture.signUp("testuser2", LOGIN_PW, "김철수", "test2@example.com");
            fixture.issueCoupon(couponId, LOGIN_ID, LOGIN_PW);
            fixture.issueCoupon(couponId, "testuser2", LOGIN_PW);

            ResponseEntity<ApiResponse<PageResponse<CouponAdminV1Dto.IssuedCouponResponse>>> response =
                    getIssues(couponId, 0, 20);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().content()).hasSize(2),
                    () -> assertThat(response.getBody().data().content().get(0).loginId()).isEqualTo("testuser2"),
                    () -> assertThat(response.getBody().data().content().get(1).loginId()).isEqualTo(LOGIN_ID),
                    () -> assertThat(response.getBody().data().totalElements()).isEqualTo(2)
            );
        }

        @Test
        void 상태_AVAILABLE_USED_EXPIRED를_반환한다() {
            Long couponId = fixture.registerCoupon(
                    "1000원 할인", "FIXED", 1000,
                    null, 100, LocalDateTime.now().plusDays(7)
            );
            fixture.signUp(LOGIN_ID, LOGIN_PW, "홍길동", "test@example.com");
            fixture.issueCoupon(couponId, LOGIN_ID, LOGIN_PW);

            ResponseEntity<ApiResponse<PageResponse<CouponAdminV1Dto.IssuedCouponResponse>>> response =
                    getIssues(couponId, 0, 20);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().content().get(0).status()).isEqualTo("AVAILABLE")
            );
        }

        @Test
        void 빈_목록이면_빈_배열을_반환한다() {
            Long couponId = fixture.registerCoupon(
                    "1000원 할인", "FIXED", 1000,
                    null, 100, LocalDateTime.now().plusDays(7)
            );

            ResponseEntity<ApiResponse<PageResponse<CouponAdminV1Dto.IssuedCouponResponse>>> response =
                    getIssues(couponId, 0, 20);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().content()).isEmpty(),
                    () -> assertThat(response.getBody().data().totalElements()).isEqualTo(0)
            );
        }

        @Test
        void 미존재_쿠폰이면_404_응답() {
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT + "/999/issues?page=0&size=20", HttpMethod.GET,
                    new HttpEntity<>(fixture.adminHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                    () -> assertThat(response.getBody().meta().message()).contains("존재하지 않는 쿠폰입니다")
            );
        }

        @Test
        void 요청_필드_규칙_위반시_400_응답() {
            Long couponId = fixture.registerCoupon(
                    "1000원 할인", "FIXED", 1000,
                    null, 100, LocalDateTime.now().plusDays(7)
            );

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT + "/" + couponId + "/issues?page=-1", HttpMethod.GET,
                    new HttpEntity<>(fixture.adminHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        void 인증헤더가_누락되면_401_응답() {
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT + "/1/issues?page=0&size=20", HttpMethod.GET,
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
            headers.set("X-Loopers-Ldap", "wrong-ldap");

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT + "/1/issues?page=0&size=20", HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {}
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED),
                    () -> assertThat(response.getBody().meta().message()).contains("인증에 실패했습니다")
            );
        }
    }

    private ResponseEntity<ApiResponse<PageResponse<CouponAdminV1Dto.IssuedCouponResponse>>> getIssues(
            Long couponId, int page, int size) {
        return testRestTemplate.exchange(
                ENDPOINT + "/" + couponId + "/issues?page=" + page + "&size=" + size, HttpMethod.GET,
                new HttpEntity<>(fixture.adminHeaders()),
                new ParameterizedTypeReference<>() {}
        );
    }

    private ResponseEntity<ApiResponse<PageResponse<CouponAdminV1Dto.CouponResponse>>> getList(
            int page, int size) {
        return testRestTemplate.exchange(
                ENDPOINT + "?page=" + page + "&size=" + size, HttpMethod.GET,
                new HttpEntity<>(fixture.adminHeaders()),
                new ParameterizedTypeReference<>() {}
        );
    }

    private ResponseEntity<ApiResponse<CouponAdminV1Dto.CouponResponse>> getDetail(Long couponId) {
        return testRestTemplate.exchange(
                ENDPOINT + "/" + couponId, HttpMethod.GET,
                new HttpEntity<>(fixture.adminHeaders()),
                new ParameterizedTypeReference<>() {}
        );
    }

    private ResponseEntity<ApiResponse<Void>> deleteRequest(Long couponId) {
        return testRestTemplate.exchange(
                ENDPOINT + "/" + couponId, HttpMethod.DELETE,
                new HttpEntity<>(fixture.adminHeaders()),
                new ParameterizedTypeReference<>() {}
        );
    }

    private ResponseEntity<ApiResponse<CouponAdminV1Dto.CouponResponse>> postRegister(
            CouponRequest.Register request) {
        return testRestTemplate.exchange(
                ENDPOINT, HttpMethod.POST,
                new HttpEntity<>(request, fixture.adminHeaders()),
                new ParameterizedTypeReference<>() {}
        );
    }

    private ResponseEntity<ApiResponse<CouponAdminV1Dto.CouponResponse>> patchUpdateInfo(
            Long couponId, CouponRequest.UpdateInfo request) {
        return testRestTemplate.exchange(
                ENDPOINT + "/" + couponId, HttpMethod.PATCH,
                new HttpEntity<>(request, fixture.adminHeaders()),
                new ParameterizedTypeReference<>() {}
        );
    }
}
