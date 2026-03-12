package com.loopers.interfaces.api.coupon;

import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.UserCoupon;
import com.loopers.infrastructure.coupon.UserCouponJpaRepository;
import com.loopers.infrastructure.coupon.CouponTemplateJpaRepository;
import com.loopers.interfaces.api.ApiResponse;
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
import org.springframework.data.domain.PageRequest;
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
class CouponAdminV1ApiE2ETest {

    private static final String HEADER_ADMIN_LDAP = "X-Loopers-Ldap";
    private static final String ADMIN_LDAP_VALUE = "loopers.admin";

    private static final String ENDPOINT_TEMPLATES = "/api-admin/v1/coupons";
    private static final Function<Long, String> ENDPOINT_TEMPLATE = id -> "/api-admin/v1/coupons/" + id;
    private static final Function<Long, String> ENDPOINT_TEMPLATE_ISSUES = id -> "/api-admin/v1/coupons/" + id + "/issues";

    private static final String COUPON_NAME = "신규가입 10% 할인";
    private static final String UPDATED_COUPON_NAME = "기존고객 5% 할인";
    private static final Long NOT_EXISTED_COUPON_ID = 999L;
    private static final Long USER_ID = 1L;
    private static final LocalDateTime FUTURE_EXPIRED_AT = LocalDateTime.now().plusDays(30);

    private final TestRestTemplate testRestTemplate;
    private final DatabaseCleanUp databaseCleanUp;
    private final CouponTemplateJpaRepository couponTemplateJpaRepository;
    private final UserCouponJpaRepository userCouponJpaRepository;

    @Autowired
    public CouponAdminV1ApiE2ETest(
            TestRestTemplate testRestTemplate,
            DatabaseCleanUp databaseCleanUp,
            CouponTemplateJpaRepository couponTemplateJpaRepository,
            UserCouponJpaRepository userCouponJpaRepository
    ) {
        this.testRestTemplate = testRestTemplate;
        this.databaseCleanUp = databaseCleanUp;
        this.couponTemplateJpaRepository = couponTemplateJpaRepository;
        this.userCouponJpaRepository = userCouponJpaRepository;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    HttpHeaders createAdminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_ADMIN_LDAP, ADMIN_LDAP_VALUE);
        return headers;
    }

    CouponTemplate createSavedTemplate() {
        return couponTemplateJpaRepository.save(
                new CouponTemplate(COUPON_NAME, CouponType.RATE, 10, null, FUTURE_EXPIRED_AT)
        );
    }

    @DisplayName("GET /api-admin/v1/coupons")
    @Nested
    class GetTemplates {

        @DisplayName("관리자가 쿠폰 템플릿 목록을 조회하면, 200 OK와 목록을 반환한다.")
        @Test
        void returnsTemplateList_whenAdminRequests() {
            // arrange
            createSavedTemplate();
            createSavedTemplate();

            // act
            ParameterizedTypeReference<ApiResponse<CouponAdminV1Dto.CouponTemplateListResponse>> responseType =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<CouponAdminV1Dto.CouponTemplateListResponse>> response = testRestTemplate.exchange(
                    ENDPOINT_TEMPLATES + "?page=0&size=20",
                    HttpMethod.GET,
                    new HttpEntity<>(createAdminHeaders()),
                    responseType
            );

            // assert
            assertAll(
                    () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                    () -> assertThat(response.getBody().data().templates()).hasSize(2)
            );
        }

        @DisplayName("관리자 인증 없이 조회하면, 401 UNAUTHORIZED를 반환한다.")
        @Test
        void returnsUnauthorized_whenAdminHeaderMissing() {
            // act
            ParameterizedTypeReference<ApiResponse<CouponAdminV1Dto.CouponTemplateListResponse>> responseType =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<CouponAdminV1Dto.CouponTemplateListResponse>> response = testRestTemplate.exchange(
                    ENDPOINT_TEMPLATES + "?page=0&size=20",
                    HttpMethod.GET,
                    HttpEntity.EMPTY,
                    responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(ErrorType.UNAUTHORIZED.getStatus());
        }
    }

    @DisplayName("GET /api-admin/v1/coupons/{couponId}")
    @Nested
    class GetTemplate {

        @DisplayName("관리자가 존재하는 쿠폰 템플릿을 상세 조회하면, 200 OK와 상세 정보를 반환한다.")
        @Test
        void returnsTemplateDetail_whenAdminRequests() {
            // arrange
            CouponTemplate template = createSavedTemplate();

            // act
            ParameterizedTypeReference<ApiResponse<CouponAdminV1Dto.CouponTemplateResponse>> responseType =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<CouponAdminV1Dto.CouponTemplateResponse>> response = testRestTemplate.exchange(
                    ENDPOINT_TEMPLATE.apply(template.getId()),
                    HttpMethod.GET,
                    new HttpEntity<>(createAdminHeaders()),
                    responseType
            );

            // assert
            assertAll(
                    () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                    () -> assertThat(response.getBody().data().id()).isEqualTo(template.getId()),
                    () -> assertThat(response.getBody().data().name()).isEqualTo(COUPON_NAME)
            );
        }

        @DisplayName("존재하지 않는 쿠폰 템플릿을 조회하면, 404 NOT_FOUND를 반환한다.")
        @Test
        void returnsNotFound_whenTemplateDoesNotExist() {
            // act
            ParameterizedTypeReference<ApiResponse<CouponAdminV1Dto.CouponTemplateResponse>> responseType =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<CouponAdminV1Dto.CouponTemplateResponse>> response = testRestTemplate.exchange(
                    ENDPOINT_TEMPLATE.apply(NOT_EXISTED_COUPON_ID),
                    HttpMethod.GET,
                    new HttpEntity<>(createAdminHeaders()),
                    responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(ErrorType.NOT_FOUND.getStatus());
        }
    }

    @DisplayName("POST /api-admin/v1/coupons")
    @Nested
    class RegisterTemplate {

        @DisplayName("관리자가 쿠폰 템플릿을 등록하면, 200 OK와 등록된 쿠폰 정보를 반환한다.")
        @Test
        void returnsRegisteredTemplate_whenAdminRegisters() {
            // arrange
            CouponAdminV1Dto.CouponTemplateRegisterRequest request = new CouponAdminV1Dto.CouponTemplateRegisterRequest(
                    COUPON_NAME, CouponType.RATE, 10, 5000, FUTURE_EXPIRED_AT
            );

            // act
            ParameterizedTypeReference<ApiResponse<CouponAdminV1Dto.CouponTemplateResponse>> responseType =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<CouponAdminV1Dto.CouponTemplateResponse>> response = testRestTemplate.exchange(
                    ENDPOINT_TEMPLATES,
                    HttpMethod.POST,
                    new HttpEntity<>(request, createAdminHeaders()),
                    responseType
            );

            // assert
            assertAll(
                    () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                    () -> assertThat(response.getBody().data().id()).isPositive(),
                    () -> assertThat(response.getBody().data().name()).isEqualTo(COUPON_NAME)
            );
        }

        @DisplayName("관리자 인증 없이 등록하면, 401 UNAUTHORIZED를 반환한다.")
        @Test
        void returnsUnauthorized_whenAdminHeaderMissing() {
            // arrange
            CouponAdminV1Dto.CouponTemplateRegisterRequest request = new CouponAdminV1Dto.CouponTemplateRegisterRequest(
                    COUPON_NAME, CouponType.RATE, 10, null, FUTURE_EXPIRED_AT
            );

            // act
            ParameterizedTypeReference<ApiResponse<CouponAdminV1Dto.CouponTemplateResponse>> responseType =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<CouponAdminV1Dto.CouponTemplateResponse>> response = testRestTemplate.exchange(
                    ENDPOINT_TEMPLATES,
                    HttpMethod.POST,
                    new HttpEntity<>(request),
                    responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(ErrorType.UNAUTHORIZED.getStatus());
        }
    }

    @DisplayName("PUT /api-admin/v1/coupons/{couponId}")
    @Nested
    class UpdateTemplate {

        @DisplayName("관리자가 쿠폰 템플릿을 수정하면, 200 OK와 수정된 쿠폰 정보를 반환한다.")
        @Test
        void returnsUpdatedTemplate_whenAdminUpdates() {
            // arrange
            CouponTemplate template = createSavedTemplate();
            CouponAdminV1Dto.CouponTemplateUpdateRequest request = new CouponAdminV1Dto.CouponTemplateUpdateRequest(
                    UPDATED_COUPON_NAME, CouponType.FIXED, 3000, 10000, FUTURE_EXPIRED_AT
            );

            // act
            ParameterizedTypeReference<ApiResponse<CouponAdminV1Dto.CouponTemplateResponse>> responseType =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<CouponAdminV1Dto.CouponTemplateResponse>> response = testRestTemplate.exchange(
                    ENDPOINT_TEMPLATE.apply(template.getId()),
                    HttpMethod.PUT,
                    new HttpEntity<>(request, createAdminHeaders()),
                    responseType
            );

            // assert
            assertAll(
                    () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                    () -> assertThat(response.getBody().data().name()).isEqualTo(UPDATED_COUPON_NAME),
                    () -> assertThat(response.getBody().data().type()).isEqualTo(CouponType.FIXED),
                    () -> assertThat(response.getBody().data().value()).isEqualTo(3000)
            );
        }

        @DisplayName("존재하지 않는 쿠폰 템플릿을 수정하면, 404 NOT_FOUND를 반환한다.")
        @Test
        void returnsNotFound_whenTemplateDoesNotExist() {
            // arrange
            CouponAdminV1Dto.CouponTemplateUpdateRequest request = new CouponAdminV1Dto.CouponTemplateUpdateRequest(
                    UPDATED_COUPON_NAME, CouponType.FIXED, 3000, null, FUTURE_EXPIRED_AT
            );

            // act
            ParameterizedTypeReference<ApiResponse<CouponAdminV1Dto.CouponTemplateResponse>> responseType =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<CouponAdminV1Dto.CouponTemplateResponse>> response = testRestTemplate.exchange(
                    ENDPOINT_TEMPLATE.apply(NOT_EXISTED_COUPON_ID),
                    HttpMethod.PUT,
                    new HttpEntity<>(request, createAdminHeaders()),
                    responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(ErrorType.NOT_FOUND.getStatus());
        }
    }

    @DisplayName("DELETE /api-admin/v1/coupons/{couponId}")
    @Nested
    class DeleteTemplate {

        @DisplayName("관리자가 쿠폰 템플릿을 삭제하면, 200 OK를 반환하고 템플릿과 발급 쿠폰이 soft delete 된다. (BR-C05)")
        @Test
        void softDeletesTemplateAndIssues_whenAdminDeletes() {
            // arrange
            CouponTemplate template = createSavedTemplate();
            userCouponJpaRepository.save(new UserCoupon(template.getId(), USER_ID, template.getExpiredAt()));

            // act
            ParameterizedTypeReference<ApiResponse<Void>> responseType =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                    ENDPOINT_TEMPLATE.apply(template.getId()),
                    HttpMethod.DELETE,
                    new HttpEntity<>(createAdminHeaders()),
                    responseType
            );

            // assert
            CouponTemplate deletedTemplate = couponTemplateJpaRepository.findById(template.getId()).orElseThrow();
            long activeIssueCount = userCouponJpaRepository
                    .findAllByCouponTemplateIdAndDeletedAtIsNull(template.getId(), PageRequest.of(0, 1))
                    .getTotalElements();
            assertAll(
                    () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                    () -> assertThat(deletedTemplate.getDeletedAt()).isNotNull(),
                    () -> assertThat(activeIssueCount).isZero()
            );
        }

        @DisplayName("존재하지 않는 쿠폰 템플릿을 삭제하면, 404 NOT_FOUND를 반환한다.")
        @Test
        void returnsNotFound_whenTemplateDoesNotExist() {
            // act
            ParameterizedTypeReference<ApiResponse<Void>> responseType =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                    ENDPOINT_TEMPLATE.apply(NOT_EXISTED_COUPON_ID),
                    HttpMethod.DELETE,
                    new HttpEntity<>(createAdminHeaders()),
                    responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(ErrorType.NOT_FOUND.getStatus());
        }
    }

    @DisplayName("GET /api-admin/v1/coupons/{couponId}/issues")
    @Nested
    class GetIssuesByTemplate {

        @DisplayName("관리자가 쿠폰 템플릿의 발급 내역을 조회하면, 200 OK와 발급 내역 목록을 반환한다.")
        @Test
        void returnsIssueList_whenAdminRequests() {
            // arrange
            CouponTemplate template = createSavedTemplate();
            userCouponJpaRepository.save(new UserCoupon(template.getId(), USER_ID, template.getExpiredAt()));
            userCouponJpaRepository.save(new UserCoupon(template.getId(), USER_ID + 1, template.getExpiredAt()));

            // act
            ParameterizedTypeReference<ApiResponse<CouponAdminV1Dto.UserCouponListResponse>> responseType =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<CouponAdminV1Dto.UserCouponListResponse>> response = testRestTemplate.exchange(
                    ENDPOINT_TEMPLATE_ISSUES.apply(template.getId()) + "?page=0&size=20",
                    HttpMethod.GET,
                    new HttpEntity<>(createAdminHeaders()),
                    responseType
            );

            // assert
            assertAll(
                    () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                    () -> assertThat(response.getBody().data().userCoupons()).hasSize(2)
            );
        }

        @DisplayName("존재하지 않는 쿠폰 템플릿의 발급 내역을 조회하면, 404 NOT_FOUND를 반환한다.")
        @Test
        void returnsNotFound_whenTemplateDoesNotExist() {
            // act
            ParameterizedTypeReference<ApiResponse<CouponAdminV1Dto.UserCouponListResponse>> responseType =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<CouponAdminV1Dto.UserCouponListResponse>> response = testRestTemplate.exchange(
                    ENDPOINT_TEMPLATE_ISSUES.apply(NOT_EXISTED_COUPON_ID) + "?page=0&size=20",
                    HttpMethod.GET,
                    new HttpEntity<>(createAdminHeaders()),
                    responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(ErrorType.NOT_FOUND.getStatus());
        }
    }
}
