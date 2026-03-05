package com.loopers.interfaces.api;

import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.infrastructure.coupon.CouponJpaRepository;
import com.loopers.infrastructure.coupon.IssuedCouponJpaRepository;
import com.loopers.interfaces.api.coupon.AdminCouponV1Dto;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminCouponV1ApiE2ETest {

    private static final String ENDPOINT = "/api-admin/v1/coupons";

    private final TestRestTemplate testRestTemplate;
    private final CouponJpaRepository couponJpaRepository;
    private final IssuedCouponJpaRepository issuedCouponJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public AdminCouponV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        CouponJpaRepository couponJpaRepository,
        IssuedCouponJpaRepository issuedCouponJpaRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.couponJpaRepository = couponJpaRepository;
        this.issuedCouponJpaRepository = issuedCouponJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-Ldap", "admin");
        return headers;
    }

    @DisplayName("쿠폰 생성 시")
    @Nested
    class CreateCoupon {

        @DisplayName("유효한 요청이면, 201 Created와 쿠폰 정보를 반환한다.")
        @Test
        void returnsCreated_whenValidRequest() {
            // arrange
            AdminCouponV1Dto.CreateRequest request = new AdminCouponV1Dto.CreateRequest(
                "신규 회원 쿠폰",
                "FIXED",
                1000L,
                0L,
                LocalDateTime.now().plusDays(30));
            HttpEntity<AdminCouponV1Dto.CreateRequest> entity = new HttpEntity<>(request, adminHeaders());

            // act
            ResponseEntity<ApiResponse<AdminCouponV1Dto.CouponResponse>> response =
                testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                () -> assertThat(response.getBody().data().id()).isNotNull(),
                () -> assertThat(response.getBody().data().name()).isEqualTo("신규 회원 쿠폰"),
                () -> assertThat(response.getBody().data().discountType()).isEqualTo("FIXED"),
                () -> assertThat(response.getBody().data().discountValue()).isEqualTo(1000L)
            );
        }

        @DisplayName("name이 비어있으면, 400 Bad Request를 반환한다.")
        @Test
        void returnsBadRequest_whenNameIsBlank() {
            // arrange
            AdminCouponV1Dto.CreateRequest request = new AdminCouponV1Dto.CreateRequest(
                "",
                "FIXED",
                1000L,
                0L,
                LocalDateTime.now().plusDays(30)
            );
            HttpEntity<AdminCouponV1Dto.CreateRequest> entity = new HttpEntity<>(request, adminHeaders());

            // act
            ResponseEntity<ApiResponse<Void>> response =
                testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("discountValue가 0 이하이면, 400 Bad Request를 반환한다.")
        @Test
        void returnsBadRequest_whenDiscountValueIsZero() {
            // arrange
            AdminCouponV1Dto.CreateRequest request = new AdminCouponV1Dto.CreateRequest(
                "쿠폰",
                "FIXED",
                0L,
                0L,
                LocalDateTime.now().plusDays(30)
            );
            HttpEntity<AdminCouponV1Dto.CreateRequest> entity = new HttpEntity<>(request, adminHeaders());

            // act
            ResponseEntity<ApiResponse<Void>> response =
                testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("쿠폰 목록 조회 시")
    @Nested
    class GetCoupons {

        @DisplayName("쿠폰이 존재하면, 200 OK와 페이징된 쿠폰 목록을 반환한다.")
        @Test
        void returnsOk_withPagedCouponList() {
            // arrange
            couponJpaRepository.save(Coupon.create("쿠폰1", Coupon.DiscountType.FIXED, 1000L, 0L, LocalDateTime.now().plusDays(30)));
            couponJpaRepository.save(Coupon.create("쿠폰2", Coupon.DiscountType.FIXED, 1000L, 0L, LocalDateTime.now().plusDays(30)));
            couponJpaRepository.save(Coupon.create("쿠폰3", Coupon.DiscountType.FIXED, 1000L, 0L, LocalDateTime.now().plusDays(30)));
            HttpEntity<Void> entity = new HttpEntity<>(adminHeaders());

            // act
            ResponseEntity<ApiResponse<PageResponse<AdminCouponV1Dto.CouponResponse>>> response =
                testRestTemplate.exchange(
                    ENDPOINT + "?page=0&size=2",
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<>() {}
                );

            // assert
            PageResponse<AdminCouponV1Dto.CouponResponse> page = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(page.content()).hasSize(2),
                () -> assertThat(page.page()).isEqualTo(0),
                () -> assertThat(page.size()).isEqualTo(2),
                () -> assertThat(page.totalElements()).isEqualTo(3L),
                () -> assertThat(page.totalPages()).isEqualTo(2)
            );
        }
    }

    @DisplayName("쿠폰 상세 조회 시")
    @Nested
    class GetCoupon {

        @DisplayName("존재하는 쿠폰이면, 200 OK와 쿠폰 정보를 반환한다.")
        @Test
        void returnsOk_whenCouponExists() {
            // arrange
            Coupon saved = couponJpaRepository.save(
                Coupon.create("신규 회원 쿠폰", Coupon.DiscountType.FIXED, 1000L, 0L, LocalDateTime.now().plusDays(30))
            );
            HttpEntity<Void> entity = new HttpEntity<>(adminHeaders());

            // act
            ResponseEntity<ApiResponse<AdminCouponV1Dto.CouponResponse>> response =
                testRestTemplate.exchange(
                    ENDPOINT + "/" + saved.getId(),
                    HttpMethod.GET, entity, new ParameterizedTypeReference<>() {}
                );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().id()).isEqualTo(saved.getId()),
                () -> assertThat(response.getBody().data().name()).isEqualTo("신규 회원 쿠폰")
            );
        }

        @DisplayName("존재하지 않는 쿠폰이면, 404 Not Found를 반환한다.")
        @Test
        void returnsNotFound_whenCouponNotExists() {
            // arrange
            HttpEntity<Void> entity = new HttpEntity<>(adminHeaders());

            // act
            ResponseEntity<ApiResponse<Void>> response =
                testRestTemplate.exchange(
                    ENDPOINT + "/99999",
                    HttpMethod.GET, entity, new ParameterizedTypeReference<>() {}
                );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("쿠폰 수정 시")
    @Nested
    class UpdateCoupon {

        @DisplayName("존재하는 쿠폰이면, 200 OK와 수정된 쿠폰 정보를 반환한다.")
        @Test
        void returnsOk_whenCouponExists() {
            // arrange
            Coupon saved = couponJpaRepository.save(
                Coupon.create("기존 쿠폰", Coupon.DiscountType.FIXED, 1000L, 0L, LocalDateTime.now().plusDays(30))
            );
            AdminCouponV1Dto.UpdateRequest request = new AdminCouponV1Dto.UpdateRequest(
                "수정된 쿠폰", 2000L, 5000L, LocalDateTime.now().plusDays(60)
            );
            HttpEntity<AdminCouponV1Dto.UpdateRequest> entity = new HttpEntity<>(request, adminHeaders());

            // act
            ResponseEntity<ApiResponse<AdminCouponV1Dto.CouponResponse>> response =
                testRestTemplate.exchange(
                    ENDPOINT + "/" + saved.getId(),
                    HttpMethod.PUT, entity, new ParameterizedTypeReference<>() {}
                );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().name()).isEqualTo("수정된 쿠폰"),
                () -> assertThat(response.getBody().data().discountValue()).isEqualTo(2000L)
            );
        }

        @DisplayName("존재하지 않는 쿠폰이면, 404 Not Found를 반환한다.")
        @Test
        void returnsNotFound_whenCouponNotExists() {
            // arrange
            AdminCouponV1Dto.UpdateRequest request = new AdminCouponV1Dto.UpdateRequest(
                "수정된 쿠폰", 2000L, 5000L, LocalDateTime.now().plusDays(60)
            );
            HttpEntity<AdminCouponV1Dto.UpdateRequest> entity = new HttpEntity<>(request, adminHeaders());

            // act
            ResponseEntity<ApiResponse<Void>> response =
                testRestTemplate.exchange(
                    ENDPOINT + "/99999",
                    HttpMethod.PUT, entity, new ParameterizedTypeReference<>() {}
                );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("쿠폰 삭제 시")
    @Nested
    class DeleteCoupon {

        @DisplayName("존재하는 쿠폰이면, 204 No Content를 반환한다.")
        @Test
        void returnsNoContent_whenCouponExists() {
            // arrange
            Coupon saved = couponJpaRepository.save(
                Coupon.create("삭제할 쿠폰", Coupon.DiscountType.FIXED, 1000L, 0L, LocalDateTime.now().plusDays(30))
            );
            HttpEntity<Void> entity = new HttpEntity<>(adminHeaders());

            // act
            ResponseEntity<Void> response =
                testRestTemplate.exchange(
                    ENDPOINT + "/" + saved.getId(),
                    HttpMethod.DELETE, entity, new ParameterizedTypeReference<>() {}
                );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        }

        @DisplayName("존재하지 않는 쿠폰이면, 404 Not Found를 반환한다.")
        @Test
        void returnsNotFound_whenCouponNotExists() {
            // arrange
            HttpEntity<Void> entity = new HttpEntity<>(adminHeaders());

            // act
            ResponseEntity<ApiResponse<Void>> response =
                testRestTemplate.exchange(
                    ENDPOINT + "/99999",
                    HttpMethod.DELETE, entity, new ParameterizedTypeReference<>() {}
                );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("쿠폰 발급 내역 조회 시")
    @Nested
    class GetCouponIssues {

        @DisplayName("발급 내역이 있으면, 200 OK와 페이징된 발급 목록을 반환한다.")
        @Test
        void returnsOk_withPagedIssueList() {
            // arrange
            Coupon coupon = couponJpaRepository.save(
                Coupon.create("신규 회원 쿠폰", Coupon.DiscountType.FIXED, 1000L, 0L, LocalDateTime.now().plusDays(30))
            );
            issuedCouponJpaRepository.save(IssuedCoupon.create(1L, coupon.getId(), LocalDateTime.now().plusDays(30)));
            issuedCouponJpaRepository.save(IssuedCoupon.create(2L, coupon.getId(), LocalDateTime.now().plusDays(30)));
            issuedCouponJpaRepository.save(IssuedCoupon.create(3L, coupon.getId(), LocalDateTime.now().plusDays(30)));
            HttpEntity<Void> entity = new HttpEntity<>(adminHeaders());

            // act
            ResponseEntity<ApiResponse<PageResponse<AdminCouponV1Dto.IssuedCouponResponse>>> response =
                testRestTemplate.exchange(
                    ENDPOINT + "/" + coupon.getId() + "/issues?page=0&size=2",
                    HttpMethod.GET, entity, new ParameterizedTypeReference<>() {}
                );

            // assert
            PageResponse<AdminCouponV1Dto.IssuedCouponResponse> page = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(page.content()).hasSize(2),
                () -> assertThat(page.page()).isEqualTo(0),
                () -> assertThat(page.size()).isEqualTo(2),
                () -> assertThat(page.totalElements()).isEqualTo(3L),
                () -> assertThat(page.totalPages()).isEqualTo(2)
            );
        }
    }
}
